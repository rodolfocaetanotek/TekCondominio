package br.com.tekcondo.documento.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.documento.entity.Documento;
import br.com.tekcondo.documento.repository.DocumentoRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.armazenamento.ArmazenamentoArquivos;
import br.com.tekcondo.comum.config.TekCondoProperties;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositório de documentos e anexos. Valida extensão E conteúdo real (assinatura do arquivo), limita tamanho
 * e só entrega arquivos por endpoint autenticado — nunca por URL pública.
 */
@Service
public class DocumentoService {

    /** Extensões aceitas → tipo MIME servido. */
    private static final Map<String, String> PERMITIDOS = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv", "text/csv", "txt", "text/plain");

    /** Anexos vinculados a registros de outros módulos e a permissão exigida (consulta, edição). */
    private static final Map<String, Permissao[]> PERMISSOES_POR_ENTIDADE = Map.of(
            "FinancialEntry", new Permissao[]{Permissao.FINANCE_VIEW, Permissao.FINANCE_EDIT},
            "Occurrence", new Permissao[]{Permissao.OCCURRENCE_VIEW, Permissao.OCCURRENCE_EDIT},
            "Contract", new Permissao[]{Permissao.SUPPLIER_VIEW, Permissao.SUPPLIER_EDIT},
            "Assembly", new Permissao[]{Permissao.ASSEMBLY_VIEW, Permissao.ASSEMBLY_EDIT});

    public record EnvioDocumento(Documento.Categoria categoria, String titulo, String descricao, Documento.Visibilidade visibilidade,
                         String tipoEntidade, UUID idEntidade, UUID idUnidade, String nomeOriginal, byte[] conteudo) {
    }

    public record DocumentoResponse(UUID id, Documento.Categoria categoria, String titulo, String descricao, String nomeArquivo,
                                   String tipoConteudo, long tamanhoBytes, Documento.Visibilidade visibilidade, String tipoEntidade,
                                   UUID idEntidade, UUID idUnidade, boolean arquivado, Instant criadoEm, String nomeCriadoPor) {

        public static DocumentoResponse de(Documento d) {
            return new DocumentoResponse(d.getId(), d.getCategoria(), d.getTitulo(), d.getDescricao(), d.getNomeArquivo(),
                    d.getTipoConteudo(), d.getTamanhoBytes(), d.getVisibilidade(), d.getTipoEntidade(), d.getIdEntidade(),
                    d.getIdUnidade(), d.getArquivadoEm() != null, d.getCriadoEm(), d.getNomeCriadoPor());
        }
    }

    public record ConteudoArquivo(String nomeArquivo, String tipoConteudo, byte[] conteudo) {
    }

    private final DocumentoRepository repository;
    private final ArmazenamentoArquivos armazenamento;
    private final AcessoCondominio acesso;
    private final AcessoService acessoService;
    private final AuditoriaService auditoria;
    private final TekCondoProperties props;
    private final Clock clock;

    public DocumentoService(DocumentoRepository repository, ArmazenamentoArquivos armazenamento, AcessoCondominio acesso,
                           AcessoService acessoService, AuditoriaService auditoria, TekCondoProperties props, Clock clock) {
        this.repository = repository;
        this.armazenamento = armazenamento;
        this.acesso = acesso;
        this.acessoService = acessoService;
        this.auditoria = auditoria;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<DocumentoResponse> listar(UUID idCondominio, Documento.Categoria categoria, String tipoEntidade, UUID idEntidade,
                                               String busca, boolean incluirArquivados, Pageable paginacao) {
        exigirConsulta(idCondominio, tipoEntidade);
        Specification<Documento> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idCondominio"), idCondominio));
            if (categoria != null) p.add(cb.equal(root.get("categoria"), categoria));
            if (tipoEntidade != null) p.add(cb.equal(root.get("tipoEntidade"), tipoEntidade));
            if (idEntidade != null) p.add(cb.equal(root.get("idEntidade"), idEntidade));
            if (!incluirArquivados) p.add(cb.isNull(root.get("arquivadoEm")));
            if (busca != null && !busca.isBlank()) {
                String like = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titulo")), like), cb.like(cb.lower(root.get("nomeArquivo")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(repository.findAll(spec, paginacao), DocumentoResponse::de);
    }

    @Transactional
    public DocumentoResponse enviar(UUID idCondominio, EnvioDocumento u) {
        Condominio condominio = acesso.exigir(idCondominio, permissaoEdicao(u.tipoEntidade()));
        validar(u);
        String extensao = extensao(u.nomeOriginal());
        String chave = armazenamento.gravar(u.conteudo());
        var usuario = acesso.usuario();
        Documento documento = repository.save(new Documento(condominio.getIdAdministradora(), idCondominio, u.categoria(),
                u.titulo().trim(), aparadoOuNulo(u.descricao()), nomeSeguro(u.nomeOriginal()), PERMITIDOS.get(extensao),
                u.conteudo().length, sha256(u.conteudo()), chave,
                u.visibilidade() == null ? Documento.Visibilidade.INTERNAL : u.visibilidade(), u.tipoEntidade(), u.idEntidade(),
                u.idUnidade(), usuario.idUsuario(), usuario.nome()));
        auditoria.registrar(AcaoAuditoria.DOCUMENT_UPLOADED, "Document", documento.getId(), Map.of("title", documento.getTitulo(),
                "file", documento.getNomeArquivo(), "visibility", documento.getVisibilidade().name()));
        return DocumentoResponse.de(documento);
    }

    @Transactional
    public ConteudoArquivo baixar(UUID idCondominio, UUID id) {
        Documento d = repository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        exigirConsulta(idCondominio, d.getTipoEntidade());
        auditoria.registrar(AcaoAuditoria.DOCUMENT_DOWNLOADED, "Document", id, Map.of("file", d.getNomeArquivo()));
        return new ConteudoArquivo(d.getNomeArquivo(), d.getTipoConteudo(), armazenamento.ler(d.getChaveArmazenamento()));
    }

    /** Entrega para o portal do morador (permissão verificada pelo serviço do portal). */
    public ConteudoArquivo ler(Documento d) {
        return new ConteudoArquivo(d.getNomeArquivo(), d.getTipoConteudo(), armazenamento.ler(d.getChaveArmazenamento()));
    }

    @Transactional
    public DocumentoResponse arquivar(UUID idCondominio, UUID id) {
        Documento d = repository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        acesso.exigir(idCondominio, permissaoEdicao(d.getTipoEntidade()));
        d.arquivar(acesso.usuario().idUsuario(), Instant.now(clock));
        auditoria.registrar(AcaoAuditoria.DOCUMENT_ARCHIVED, "Document", id, Map.of("title", d.getTitulo()));
        return DocumentoResponse.de(d);
    }

    // ------------------------------------------------------------------

    private void exigirConsulta(UUID idCondominio, String tipoEntidade) {
        if (tipoEntidade == null) {
            acesso.exigir(idCondominio, Permissao.DOCUMENT_VIEW);
            return;
        }
        Permissao[] permissoes = PERMISSOES_POR_ENTIDADE.get(tipoEntidade);
        if (permissoes == null) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY_TYPE", "Tipo de vínculo inválido.");
        }
        acesso.exigir(idCondominio, acessoService.concessoesAtuais().tem(Permissao.DOCUMENT_VIEW, idCondominio)
                ? Permissao.DOCUMENT_VIEW : permissoes[0]);
    }

    private Permissao permissaoEdicao(String tipoEntidade) {
        if (tipoEntidade == null) {
            return Permissao.DOCUMENT_EDIT;
        }
        Permissao[] permissoes = PERMISSOES_POR_ENTIDADE.get(tipoEntidade);
        if (permissoes == null) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY_TYPE", "Tipo de vínculo inválido.");
        }
        return permissoes[1];
    }

    private void validar(EnvioDocumento u) {
        if (u.conteudo() == null || u.conteudo().length == 0) {
            throw ApiException.requisicaoInvalida("EMPTY_FILE", "Arquivo vazio.");
        }
        if (u.conteudo().length > props.storage().maxFileBytes()) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Arquivo maior que " + (props.storage().maxFileBytes() / (1024 * 1024)) + " MB.");
        }
        if (u.titulo() == null || u.titulo().isBlank() || u.titulo().length() > 200) {
            throw ApiException.requisicaoInvalida("TITLE_REQUIRED", "Informe o título (até 200 caracteres).");
        }
        String ext = extensao(u.nomeOriginal());
        if (!PERMITIDOS.containsKey(ext)) {
            throw ApiException.requisicaoInvalida("FILE_TYPE_NOT_ALLOWED", "Tipo de arquivo não permitido. Use PDF, imagem (PNG/JPG), DOCX, XLSX, CSV ou TXT.");
        }
        if (!assinaturaConfere(ext, u.conteudo())) {
            throw ApiException.requisicaoInvalida("FILE_CONTENT_MISMATCH", "O conteúdo do arquivo não corresponde à extensão informada.");
        }
        if ((u.tipoEntidade() == null) != (u.idEntidade() == null)) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY", "Informe tipo e identificador do vínculo, ou nenhum dos dois.");
        }
    }

    static boolean assinaturaConfere(String ext, byte[] c) {
        return switch (ext) {
            case "pdf" -> comecaCom(c, 0x25, 0x50, 0x44, 0x46);
            case "png" -> comecaCom(c, 0x89, 0x50, 0x4E, 0x47);
            case "jpg", "jpeg" -> comecaCom(c, 0xFF, 0xD8, 0xFF);
            case "docx", "xlsx" -> comecaCom(c, 0x50, 0x4B, 0x03, 0x04);
            case "csv", "txt" -> {
                for (int i = 0; i < Math.min(c.length, 4096); i++) {
                    if (c[i] == 0) yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    private static boolean comecaCom(byte[] c, int... assinatura) {
        if (c.length < assinatura.length) return false;
        for (int i = 0; i < assinatura.length; i++) {
            if ((c[i] & 0xFF) != assinatura[i]) return false;
        }
        return true;
    }

    private static String extensao(String nome) {
        if (nome == null || !nome.contains(".")) return "";
        return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** Remove caminhos e caracteres de controle do nome original. */
    private static String nomeSeguro(String nomeOriginal) {
        String nome = nomeOriginal.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_");
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

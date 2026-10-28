package br.com.tekcondo.estrutura.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.repository.PessoaRepository;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.io.NumerosBrasileiros;
import br.com.tekcondo.comum.io.CsvReader;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.BlocoRequest;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.BlocoResponse;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.ErroImportacao;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.ResultadoImportacao;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.ResumoFracoes;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.UnidadeRequest;
import br.com.tekcondo.estrutura.dto.EstruturaDtos.UnidadeResponse;
import br.com.tekcondo.estrutura.entity.Bloco;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.BlocoRepository;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blocos, unidades, frações ideais e importação de planilha. */
@Service
public class EstruturaService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int MAX_LINHAS_IMPORTACAO = 5000;
    /** Valores aceitos na coluna "tipo" da planilha (formato de arquivo documentado; não alterar). */
    private static final Map<String, Unidade.Tipo> APELIDOS_TIPO = Map.ofEntries(
            Map.entry("apartamento", Unidade.Tipo.APARTMENT), Map.entry("apto", Unidade.Tipo.APARTMENT),
            Map.entry("ap", Unidade.Tipo.APARTMENT), Map.entry("casa", Unidade.Tipo.HOUSE),
            Map.entry("sala", Unidade.Tipo.ROOM), Map.entry("loja", Unidade.Tipo.STORE),
            Map.entry("vaga", Unidade.Tipo.PARKING), Map.entry("garagem", Unidade.Tipo.PARKING),
            Map.entry("box", Unidade.Tipo.PARKING), Map.entry("outro", Unidade.Tipo.OTHER), Map.entry("outros", Unidade.Tipo.OTHER));

    private final BlocoRepository blocoRepository;
    private final UnidadeRepository unidadeRepository;
    private final PessoaRepository pessoaRepository;
    private final VinculoRepository vinculoRepository;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public EstruturaService(BlocoRepository blocoRepository, UnidadeRepository unidadeRepository,
                            PessoaRepository pessoaRepository, VinculoRepository vinculoRepository,
                            AcessoCondominio acesso, AuditoriaService auditoriaService, Clock clock) {
        this.blocoRepository = blocoRepository;
        this.unidadeRepository = unidadeRepository;
        this.pessoaRepository = pessoaRepository;
        this.vinculoRepository = vinculoRepository;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- blocos

    @Transactional(readOnly = true)
    public List<BlocoResponse> blocos(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.STRUCTURE_VIEW);
        return blocoRepository.findByIdCondominioOrderByNomeAsc(idCondominio).stream()
                .map(b -> new BlocoResponse(b.getId(), b.getNome(), b.getDescricao(), b.isAtivo(),
                        blocoRepository.contarUnidadesAtivas(b.getId())))
                .toList();
    }

    @Transactional
    public BlocoResponse cadastrarBloco(UUID idCondominio, BlocoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        garantirNomeBlocoLivre(idCondominio, request.nome(), null);
        Bloco bloco = blocoRepository.save(new Bloco(condominio.getIdAdministradora(), idCondominio,
                request.nome(), request.descricao()));
        auditoriaService.registrar(AcaoAuditoria.BUILDING_CREATED, "Building", bloco.getId(), Map.of("name", bloco.getNome()));
        return new BlocoResponse(bloco.getId(), bloco.getNome(), bloco.getDescricao(), true, 0);
    }

    @Transactional
    public BlocoResponse atualizarBloco(UUID idCondominio, UUID id, BlocoRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        Bloco bloco = carregarBloco(idCondominio, id);
        garantirNomeBlocoLivre(idCondominio, request.nome(), id);
        String antes = bloco.getNome();
        bloco.atualizar(request.nome(), request.descricao());
        auditoriaService.registrar(AcaoAuditoria.BUILDING_UPDATED, "Building", id, Map.of("name", Map.of("from", antes, "to", bloco.getNome())));
        return new BlocoResponse(id, bloco.getNome(), bloco.getDescricao(), bloco.isAtivo(),
                blocoRepository.contarUnidadesAtivas(id));
    }

    @Transactional
    public BlocoResponse setBlocoAtivo(UUID idCondominio, UUID id, boolean ativo) {
        acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        Bloco bloco = carregarBloco(idCondominio, id);
        long unidades = blocoRepository.contarUnidadesAtivas(id);
        if (!ativo && unidades > 0) {
            throw ApiException.regraNegocio("BUILDING_HAS_UNITS",
                    "O bloco possui " + unidades + " unidade(s) ativa(s). Inative ou mova as unidades antes.");
        }
        bloco.setAtivo(ativo);
        auditoriaService.registrar(ativo ? AcaoAuditoria.BUILDING_ACTIVATED : AcaoAuditoria.BUILDING_DEACTIVATED, "Building", id, null);
        return new BlocoResponse(id, bloco.getNome(), bloco.getDescricao(), ativo, unidades);
    }

    // ---------------------------------------------------------------- unidades

    @Transactional(readOnly = true)
    public PaginaResponse<UnidadeResponse> unidades(UUID idCondominio, String busca, UUID idBloco, Boolean ativo,
                                                    Pageable pageable) {
        acesso.exigir(idCondominio, Permissao.STRUCTURE_VIEW);
        Specification<Unidade> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idCondominio"), idCondominio));
            if (busca != null && !busca.isBlank()) {
                p.add(cb.like(cb.lower(root.get("identificacao")), "%" + busca.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (idBloco != null) {
                p.add(cb.equal(root.get("bloco").get("id"), idBloco));
            }
            if (ativo != null) {
                p.add(cb.equal(root.get("ativo"), ativo));
            }
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("bloco", JoinType.LEFT);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Unidade> pagina = unidadeRepository.findAll(spec, pageable);
        Map<UUID, List<Vinculo>> vinculos = vinculosVigentes(pagina.getContent());
        return PaginaResponse.of(pagina, u -> toResponse(u, vinculos.getOrDefault(u.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public UnidadeResponse unidade(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.STRUCTURE_VIEW);
        Unidade unidade = carregarUnidade(idCondominio, id);
        return toResponse(unidade, vinculosVigentes(List.of(unidade)).getOrDefault(id, List.of()));
    }

    @Transactional
    public UnidadeResponse cadastrarUnidade(UUID idCondominio, UnidadeRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        Unidade unidade = new Unidade(condominio.getIdAdministradora(), idCondominio);
        aplicar(unidade, idCondominio, request);
        unidadeRepository.save(unidade);
        auditoriaService.registrar(AcaoAuditoria.UNIT_CREATED, "Unit", unidade.getId(), Map.of("unit", unidade.rotulo(),
                "idealFraction", unidade.getFracaoIdeal().toPlainString()));
        return toResponse(unidade, List.of());
    }

    @Transactional
    public UnidadeResponse atualizarUnidade(UUID idCondominio, UUID id, UnidadeRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        Unidade unidade = carregarUnidade(idCondominio, id);
        if (request.versao() == null || !request.versao().equals(unidade.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String antes = unidade.rotulo() + " / fração " + unidade.getFracaoIdeal().stripTrailingZeros().toPlainString();
        aplicar(unidade, idCondominio, request);
        unidadeRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.UNIT_UPDATED, "Unit", id, Map.of("from", antes,
                "to", unidade.rotulo() + " / fração " + unidade.getFracaoIdeal().stripTrailingZeros().toPlainString()));
        return toResponse(unidade, vinculosVigentes(List.of(unidade)).getOrDefault(id, List.of()));
    }

    @Transactional
    public UnidadeResponse setUnidadeAtiva(UUID idCondominio, UUID id, boolean ativa) {
        acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        Unidade unidade = carregarUnidade(idCondominio, id);
        if (unidade.isAtivo() == ativa) {
            throw ApiException.regraNegocio("UNIT_STATUS_UNCHANGED", ativa ? "A unidade já está ativa." : "A unidade já está inativa.");
        }
        unidade.setAtivo(ativa);
        unidadeRepository.flush();
        auditoriaService.registrar(ativa ? AcaoAuditoria.UNIT_ACTIVATED : AcaoAuditoria.UNIT_DEACTIVATED, "Unit", id, Map.of("unit", unidade.rotulo()));
        return toResponse(unidade, vinculosVigentes(List.of(unidade)).getOrDefault(id, List.of()));
    }

    @Transactional(readOnly = true)
    public ResumoFracoes resumoFracoes(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.STRUCTURE_VIEW);
        return resumirFracoes(idCondominio);
    }

    public ResumoFracoes resumirFracoes(UUID idCondominio) {
        BigDecimal total = unidadeRepository.somarFracoesAtivas(idCondominio);
        long quantidade = unidadeRepository.countByIdCondominioAndAtivoTrue(idCondominio);
        BigDecimal diferenca = CEM.subtract(total);
        return new ResumoFracoes(total, quantidade, diferenca.signum() == 0, diferenca);
    }

    // ---------------------------------------------------------------- importação

    /**
     * Importa unidades (e proprietários) de CSV. Colunas: bloco; unidade*; tipo; fracao; area; proprietario;
     * documento; email; telefone. Tudo ou nada: com qualquer erro, nada é gravado e o relatório lista as linhas.
     */
    @Transactional
    public ResultadoImportacao importarUnidades(UUID idCondominio, byte[] conteudo, boolean simulacao) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.STRUCTURE_EDIT);
        List<CsvReader.Linha> linhas = CsvReader.ler(conteudo);
        List<ErroImportacao> erros = new ArrayList<>();
        if (!CsvReader.cabecalho(conteudo).contains("unidade")) {
            erros.add(new ErroImportacao(1, "Cabeçalho inválido: a coluna \"unidade\" é obrigatória."));
            return new ResultadoImportacao(simulacao, linhas.size(), 0, 0, 0, 0, erros);
        }
        if (linhas.size() > MAX_LINHAS_IMPORTACAO) {
            erros.add(new ErroImportacao(1, "Arquivo com mais de " + MAX_LINHAS_IMPORTACAO + " linhas."));
            return new ResultadoImportacao(simulacao, linhas.size(), 0, 0, 0, 0, erros);
        }

        record LinhaValida(int linha, String bloco, String identificacao, Unidade.Tipo tipo, BigDecimal fracao,
                           BigDecimal area, String nomeProprietario, String documento, String email, String telefone) {
        }
        List<LinhaValida> validas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        Map<String, Bloco> blocosPorNome = blocoRepository.findByIdCondominioOrderByNomeAsc(idCondominio)
                .stream().collect(Collectors.toMap(b -> b.getNome().toLowerCase(Locale.ROOT), Function.identity()));

        for (CsvReader.Linha linha : linhas) {
            int errosAntes = erros.size();
            String identificacao = linha.get("unidade");
            String nomeBloco = linha.get("bloco");
            if (identificacao == null) {
                erros.add(new ErroImportacao(linha.numero(), "Unidade não informada."));
                continue;
            }
            if (identificacao.length() > 30 || (nomeBloco != null && nomeBloco.length() > 80)) {
                erros.add(new ErroImportacao(linha.numero(), "Bloco (até 80) ou unidade (até 30 caracteres) muito longos."));
            }
            Unidade.Tipo tipo = Unidade.Tipo.APARTMENT;
            if (linha.get("tipo") != null) {
                tipo = APELIDOS_TIPO.get(CsvReader.normalizarCabecalho(linha.get("tipo")));
                if (tipo == null) {
                    erros.add(new ErroImportacao(linha.numero(), "Tipo inválido: \"" + linha.get("tipo")
                            + "\" (use apartamento, casa, sala, loja, vaga ou outro)."));
                }
            }
            BigDecimal fracao = converterNumero(linha.get("fracao"), linha.numero(), "Fração ideal", erros);
            if (fracao != null && (fracao.signum() < 0 || fracao.compareTo(CEM) > 0 || fracao.scale() > 6)) {
                erros.add(new ErroImportacao(linha.numero(), "Fração ideal deve estar entre 0 e 100, com até 6 casas decimais."));
            }
            BigDecimal area = converterNumero(linha.get("area"), linha.numero(), "Área", erros);
            if (area != null && area.signum() <= 0) {
                erros.add(new ErroImportacao(linha.numero(), "Área deve ser positiva."));
            }
            String chave = (nomeBloco == null ? "" : nomeBloco.toLowerCase(Locale.ROOT)) + "|" + identificacao.toLowerCase(Locale.ROOT);
            if (!vistas.add(chave)) {
                erros.add(new ErroImportacao(linha.numero(), "Unidade repetida no arquivo: " + identificacao));
            } else if (unidadeExiste(idCondominio, nomeBloco == null ? null : blocosPorNome.get(nomeBloco.toLowerCase(Locale.ROOT)), identificacao)) {
                erros.add(new ErroImportacao(linha.numero(), "Unidade já cadastrada: " + identificacao));
            }
            String proprietario = linha.get("proprietario");
            String documento = linha.get("documento");
            String email = linha.get("email");
            String telefone = linha.get("telefone");
            if (proprietario == null && (documento != null || email != null || telefone != null)) {
                erros.add(new ErroImportacao(linha.numero(), "Informe o nome do proprietário."));
            }
            if (documento != null && !DocumentosBrasileiros.isCpfOuCnpjValido(documento)) {
                erros.add(new ErroImportacao(linha.numero(), "CPF/CNPJ inválido: " + documento));
            }
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                erros.add(new ErroImportacao(linha.numero(), "E-mail inválido: " + email));
            }
            if (telefone != null && !DocumentosBrasileiros.isTelefoneValido(telefone)) {
                erros.add(new ErroImportacao(linha.numero(), "Telefone inválido: " + telefone));
            }
            if (erros.size() == errosAntes) {
                validas.add(new LinhaValida(linha.numero(), nomeBloco, identificacao, tipo, fracao, area, proprietario,
                        documento == null ? null : DocumentosBrasileiros.normalizarDocumento(documento), email,
                        DocumentosBrasileiros.somenteDigitos(telefone)));
            }
        }
        if (!erros.isEmpty() || simulacao) {
            long novosBlocos = validas.stream().map(LinhaValida::bloco).filter(b -> b != null)
                    .map(b -> b.toLowerCase(Locale.ROOT)).distinct().filter(b -> !blocosPorNome.containsKey(b)).count();
            return new ResultadoImportacao(simulacao || !erros.isEmpty(), linhas.size(), erros.isEmpty() ? (int) novosBlocos : 0,
                    erros.isEmpty() ? validas.size() : 0, 0, 0, erros);
        }

        int blocosCriados = 0;
        int pessoasCriadas = 0;
        int pessoasReaproveitadas = 0;
        Map<String, Pessoa> pessoasPorDocumento = new HashMap<>();
        LocalDate hoje = LocalDate.now(clock);
        UUID autor = acesso.usuario().idUsuario();
        for (LinhaValida v : validas) {
            Bloco bloco = null;
            if (v.bloco() != null) {
                bloco = blocosPorNome.get(v.bloco().toLowerCase(Locale.ROOT));
                if (bloco == null) {
                    bloco = blocoRepository.save(new Bloco(condominio.getIdAdministradora(), idCondominio, v.bloco(), null));
                    blocosPorNome.put(v.bloco().toLowerCase(Locale.ROOT), bloco);
                    blocosCriados++;
                }
            }
            Unidade unidade = new Unidade(condominio.getIdAdministradora(), idCondominio);
            unidade.atualizar(bloco, v.identificacao(), v.tipo(), null, v.area(), v.fracao(), null);
            unidadeRepository.save(unidade);
            if (v.nomeProprietario() != null) {
                Pessoa pessoa = v.documento() == null ? null : pessoasPorDocumento.computeIfAbsent(v.documento(),
                        d -> pessoaRepository.findByIdAdministradoraAndDocumento(condominio.getIdAdministradora(), d).orElse(null));
                if (pessoa == null) {
                    pessoa = new Pessoa(condominio.getIdAdministradora());
                    pessoa.atualizar(v.nomeProprietario(), v.documento() != null && v.documento().length() == 14
                            ? Pessoa.Tipo.COMPANY : Pessoa.Tipo.INDIVIDUAL, v.documento(), v.email(), v.telefone(), null, null);
                    pessoaRepository.save(pessoa);
                    if (v.documento() != null) {
                        pessoasPorDocumento.put(v.documento(), pessoa);
                    }
                    pessoasCriadas++;
                } else {
                    pessoasReaproveitadas++;
                }
                vinculoRepository.save(new Vinculo(unidade, pessoa, Vinculo.Tipo.OWNER, true, hoje, autor));
            }
        }
        auditoriaService.registrar(AcaoAuditoria.UNITS_IMPORTED, "Condominium", idCondominio, Map.of("units", validas.size(),
                "buildingsCreated", blocosCriados, "peopleCreated", pessoasCriadas));
        return new ResultadoImportacao(false, linhas.size(), blocosCriados, validas.size(), pessoasCriadas, pessoasReaproveitadas, List.of());
    }

    // ---------------------------------------------------------------- apoio

    public Unidade carregarUnidade(UUID idCondominio, UUID id) {
        return unidadeRepository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
    }

    private Bloco carregarBloco(UUID idCondominio, UUID id) {
        return blocoRepository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Bloco"));
    }

    private void garantirNomeBlocoLivre(UUID idCondominio, String nome, UUID excetoId) {
        blocoRepository.buscarPorNome(idCondominio, nome.trim())
                .filter(b -> !b.getId().equals(excetoId))
                .ifPresent(b -> {
                    throw ApiException.conflito("BUILDING_NAME_TAKEN", "Já existe um bloco com este nome.");
                });
    }

    private void aplicar(Unidade unidade, UUID idCondominio, UnidadeRequest request) {
        Bloco bloco = request.idBloco() == null ? null : carregarBloco(idCondominio, request.idBloco());
        if (bloco != null && !bloco.isAtivo()) {
            throw ApiException.regraNegocio("BUILDING_INACTIVE", "O bloco selecionado está inativo.");
        }
        UUID propria = unidade.getVersao() == null ? null : unidade.getId();
        boolean duplicada = unidadeRepository.buscarPorIdentificacao(idCondominio, request.identificacao().trim()).stream()
                .anyMatch(u -> !u.getId().equals(propria) && mesmoBloco(u.getBloco(), bloco));
        if (duplicada) {
            throw ApiException.conflito("UNIT_IDENTIFIER_TAKEN", "Já existe uma unidade com esta identificação neste bloco.");
        }
        unidade.atualizar(bloco, request.identificacao(), request.tipo(), request.andar(), request.area(),
                request.fracaoIdeal(), request.observacoes());
    }

    private boolean unidadeExiste(UUID idCondominio, Bloco bloco, String identificacao) {
        return unidadeRepository.buscarPorIdentificacao(idCondominio, identificacao).stream()
                .anyMatch(u -> mesmoBloco(u.getBloco(), bloco));
    }

    private static boolean mesmoBloco(Bloco a, Bloco b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private static BigDecimal converterNumero(String bruto, int linha, String rotulo, List<ErroImportacao> erros) {
        if (bruto == null) {
            return null;
        }
        try {
            return NumerosBrasileiros.converter(bruto);
        } catch (NumberFormatException e) {
            erros.add(new ErroImportacao(linha, rotulo + " inválida: " + bruto));
            return null;
        }
    }

    private Map<UUID, List<Vinculo>> vinculosVigentes(List<Unidade> unidades) {
        if (unidades.isEmpty()) {
            return Map.of();
        }
        return vinculoRepository.buscarVigentesPorUnidades(unidades.stream().map(Unidade::getId).toList()).stream()
                .collect(Collectors.groupingBy(v -> v.getUnidade().getId()));
    }

    private static UnidadeResponse toResponse(Unidade u, List<Vinculo> vinculos) {
        String proprietario = vinculos.stream().filter(v -> v.getTipo() == Vinculo.Tipo.OWNER)
                .map(v -> v.getPessoa().getNome()).sorted().collect(Collectors.joining(", "));
        String responsavel = vinculos.stream().filter(Vinculo::isResponsavelFinanceiro)
                .map(v -> v.getPessoa().getNome()).findFirst().orElse(null);
        return new UnidadeResponse(u.getId(), u.getBloco() == null ? null : u.getBloco().getId(),
                u.getBloco() == null ? null : u.getBloco().getNome(), u.getIdentificacao(), u.rotulo(), u.getTipo(),
                u.getAndar(), u.getArea(), u.getFracaoIdeal(), u.getObservacoes(), u.isAtivo(), u.getVersao(),
                proprietario.isEmpty() ? null : proprietario, responsavel);
    }
}

package br.com.tekcondo.pessoa.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.EscopoAcesso;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.acesso.entity.Papel;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import br.com.tekcondo.acesso.repository.PapelRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.acesso.service.SenhaService;
import br.com.tekcondo.pessoa.dto.PessoaDtos.EncerramentoVinculoRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaDetalhe;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.PessoaResumo;
import br.com.tekcondo.pessoa.dto.PessoaDtos.VinculoRequest;
import br.com.tekcondo.pessoa.dto.PessoaDtos.VinculoResponse;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.pessoa.repository.PessoaRepository;
import br.com.tekcondo.pessoa.repository.VinculoRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pessoas (cadastro único por CPF/CNPJ na administradora) e vínculos com as unidades.
 * Quem não tem acesso à administradora inteira só enxerga pessoas vinculadas aos seus condomínios.
 */
@Service
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final VinculoRepository vinculoRepository;
    private final UnidadeRepository unidadeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final PapelUsuarioRepository papelUsuarioRepository;
    private final SenhaService senhaService;
    private final AcessoService acessoService;
    private final AcessoCondominio acessoCondominio;
    private final AuditoriaService auditoriaService;

    public PessoaService(PessoaRepository pessoaRepository, VinculoRepository vinculoRepository,
                         UnidadeRepository unidadeRepository, UsuarioRepository usuarioRepository,
                         PapelRepository papelRepository, PapelUsuarioRepository papelUsuarioRepository,
                         SenhaService senhaService, AcessoService acessoService, AcessoCondominio acessoCondominio,
                         AuditoriaService auditoriaService) {
        this.pessoaRepository = pessoaRepository;
        this.vinculoRepository = vinculoRepository;
        this.unidadeRepository = unidadeRepository;
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.senhaService = senhaService;
        this.acessoService = acessoService;
        this.acessoCondominio = acessoCondominio;
        this.auditoriaService = auditoriaService;
    }

    // ---------------------------------------------------------------- pessoas

    @Transactional(readOnly = true)
    public PaginaResponse<PessoaResumo> listar(String busca, Boolean ativo, UUID idCondominio, Pageable pageable) {
        UUID idAdministradora = acessoCondominio.idAdministradora();
        EscopoAcesso escopo = acessoService.exigirEscopo(Permissao.PEOPLE_VIEW);
        if (idCondominio != null) {
            acessoCondominio.exigir(idCondominio, Permissao.PEOPLE_VIEW);
        }
        Specification<Pessoa> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idAdministradora"), idAdministradora));
            if (busca != null && !busca.isBlank()) {
                String termo = busca.trim().toLowerCase(Locale.ROOT);
                String digitos = termo.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
                List<Predicate> ou = new ArrayList<>();
                ou.add(cb.like(cb.lower(root.get("nome")), "%" + termo + "%"));
                ou.add(cb.like(cb.lower(root.get("email")), "%" + termo + "%"));
                if (!digitos.isEmpty()) {
                    ou.add(cb.like(root.get("documento"), "%" + digitos + "%"));
                }
                p.add(cb.or(ou.toArray(Predicate[]::new)));
            }
            if (ativo != null) {
                p.add(cb.equal(root.get("ativo"), ativo));
            }
            Set<UUID> condominios = idCondominio != null ? Set.of(idCondominio)
                    : escopo.administradoraInteira() ? null : escopo.idsCondominio();
            if (condominios != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<Vinculo> v = sub.from(Vinculo.class);
                sub.select(v.get("pessoa").get("id"))
                        .where(v.get("unidade").get("idCondominio").in(condominios));
                p.add(root.get("id").in(sub));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Pessoa> pagina = pessoaRepository.findAll(spec, pageable);
        Map<UUID, List<String>> unidades = new HashMap<>();
        for (Pessoa pessoa : pagina.getContent()) {
            unidades.put(pessoa.getId(), vinculoRepository.buscarPorPessoa(pessoa.getId()).stream()
                    .filter(Vinculo::isVigente).map(v -> v.getUnidade().rotulo() + " (" + rotuloTipo(v.getTipo()) + ")")
                    .distinct().toList());
        }
        return PaginaResponse.of(pagina, x -> new PessoaResumo(x.getId(), x.getNome(), x.getTipo(), x.getDocumento(),
                x.getEmail(), x.getTelefone(), x.isAtivo(), unidades.getOrDefault(x.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PessoaDetalhe obter(UUID id) {
        return toDetalhe(carregarVisivel(id, Permissao.PEOPLE_VIEW));
    }

    @Transactional
    public PessoaDetalhe cadastrar(PessoaRequest request) {
        acessoService.exigirEscopo(Permissao.PEOPLE_EDIT);
        Pessoa pessoa = new Pessoa(acessoCondominio.idAdministradora());
        aplicar(pessoa, request);
        pessoaRepository.save(pessoa);
        auditoriaService.registrar(AcaoAuditoria.PERSON_CREATED, "Person", pessoa.getId(), Map.of("name", pessoa.getNome()));
        return toDetalhe(pessoa);
    }

    @Transactional
    public PessoaDetalhe atualizar(UUID id, PessoaRequest request) {
        Pessoa pessoa = carregarVisivel(id, Permissao.PEOPLE_EDIT);
        if (request.versao() == null || !request.versao().equals(pessoa.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        String antes = pessoa.getNome() + " / " + pessoa.getEmail() + " / " + pessoa.getTelefone();
        aplicar(pessoa, request);
        pessoaRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.PERSON_UPDATED, "Person", id,
                Map.of("from", antes, "to", pessoa.getNome() + " / " + pessoa.getEmail() + " / " + pessoa.getTelefone()));
        return toDetalhe(pessoa);
    }

    @Transactional
    public PessoaDetalhe setAtivo(UUID id, boolean ativo) {
        Pessoa pessoa = carregarVisivel(id, Permissao.PEOPLE_EDIT);
        if (!ativo && vinculoRepository.buscarPorPessoa(id).stream().anyMatch(Vinculo::isVigente)) {
            throw ApiException.regraNegocio("PERSON_HAS_OCCUPANCY",
                    "A pessoa possui vínculos vigentes com unidades. Encerre os vínculos antes de inativá-la.");
        }
        pessoa.setAtivo(ativo);
        pessoaRepository.flush();
        auditoriaService.registrar(ativo ? AcaoAuditoria.PERSON_ACTIVATED : AcaoAuditoria.PERSON_DEACTIVATED, "Person", id, null);
        return toDetalhe(pessoa);
    }

    /**
     * Concede acesso ao portal do morador: cria (ou vincula) o usuário com o e-mail da pessoa e atribui, em cada
     * condomínio onde ela tem vínculo vigente, o papel de Proprietário ou Morador. Envia convite para definir a senha.
     */
    @Transactional
    public PessoaDetalhe concederAcessoPortal(UUID id) {
        UsuarioAtual autor = acessoCondominio.usuario();
        acessoService.exigirNaAdministradora(Permissao.USER_MANAGE);
        Pessoa pessoa = carregarVisivel(id, Permissao.PEOPLE_VIEW);
        if (pessoa.getEmail() == null) {
            throw ApiException.regraNegocio("PERSON_WITHOUT_EMAIL", "Cadastre o e-mail da pessoa para conceder acesso ao portal.");
        }
        List<Vinculo> vigentes = vinculoRepository.buscarPorPessoa(id).stream().filter(Vinculo::isVigente).toList();
        if (vigentes.isEmpty()) {
            throw ApiException.regraNegocio("PERSON_WITHOUT_OCCUPANCY", "A pessoa não possui vínculo vigente com nenhuma unidade.");
        }
        Usuario usuario = usuarioRepository.findByIdPessoa(id).orElse(null);
        boolean criado = false;
        if (usuario == null) {
            Usuario porEmail = usuarioRepository.buscarPorEmail(pessoa.getEmail()).orElse(null);
            if (porEmail != null) {
                if (!autor.idAdministradora().equals(porEmail.getIdAdministradora()) || porEmail.getIdPessoa() != null) {
                    throw ApiException.conflito("USER_EMAIL_TAKEN", "Já existe um usuário com este e-mail.");
                }
                usuario = porEmail;
            } else {
                usuario = usuarioRepository.save(Usuario.membroAdministradora(autor.idAdministradora(), pessoa.getNome(),
                        pessoa.getEmail(), senhaService.hashSenhaInutilizavel()));
                criado = true;
            }
            usuario.vincularPessoa(id);
        }
        Papel proprietario = papelRepository.buscarPapelSistema("OWNER").orElseThrow();
        Papel morador = papelRepository.buscarPapelSistema("RESIDENT").orElseThrow();
        Map<UUID, Boolean> proprietarioNoCondominio = vigentes.stream().collect(Collectors.toMap(v -> v.getUnidade().getIdCondominio(),
                v -> v.getTipo() == Vinculo.Tipo.OWNER, Boolean::logicalOr));
        for (Map.Entry<UUID, Boolean> e : proprietarioNoCondominio.entrySet()) {
            Papel papel = e.getValue() ? proprietario : morador;
            if (!papelUsuarioRepository.existeNoCondominio(usuario.getId(), papel.getId(), e.getKey())) {
                papelUsuarioRepository.save(new PapelUsuario(usuario.getId(), papel, autor.idAdministradora(), e.getKey(), autor.idUsuario()));
            }
        }
        auditoriaService.registrar(AcaoAuditoria.PORTAL_ACCESS_GRANTED, "Person", id,
                Map.of("email", pessoa.getEmail(), "condominiums", proprietarioNoCondominio.size()));
        if (criado || usuario.nuncaAcessou()) {
            senhaService.enviarConvite(usuario);
        }
        return toDetalhe(pessoa);
    }

    // ---------------------------------------------------------------- vínculos

    @Transactional(readOnly = true)
    public List<VinculoResponse> vinculosDaUnidade(UUID idCondominio, UUID idUnidade) {
        acessoCondominio.exigir(idCondominio, Permissao.PEOPLE_VIEW);
        unidadeRepository.findByIdAndIdCondominio(idUnidade, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        return vinculoRepository.buscarPorUnidade(idUnidade).stream().map(VinculoResponse::de).toList();
    }

    @Transactional
    public VinculoResponse adicionarVinculo(UUID idCondominio, VinculoRequest request) {
        acessoCondominio.exigirAtivo(idCondominio, Permissao.PEOPLE_EDIT);
        Unidade unidade = unidadeRepository.findByIdAndIdCondominio(request.idUnidade(), idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        Pessoa pessoa = pessoaRepository.findByIdAndIdAdministradora(request.idPessoa(), acessoCondominio.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        if (!pessoa.isAtivo() || !unidade.isAtivo()) {
            throw ApiException.regraNegocio("INACTIVE_RECORD", "Pessoa ou unidade inativa.");
        }
        if (vinculoRepository.existeVigente(unidade.getId(), pessoa.getId(), request.tipo())) {
            throw ApiException.conflito("OCCUPANCY_EXISTS", "Esta pessoa já possui este vínculo vigente com a unidade.");
        }
        if (request.responsavelFinanceiro() && vinculoRepository.buscarResponsavelFinanceiroVigente(unidade.getId()).isPresent()) {
            throw ApiException.conflito("FINANCIAL_RESPONSIBLE_EXISTS",
                    "A unidade já possui responsável financeiro. Use \"Definir como responsável financeiro\" para transferir.");
        }
        Vinculo vinculo = vinculoRepository.save(new Vinculo(unidade, pessoa, request.tipo(),
                request.responsavelFinanceiro(), request.dataInicio(), acessoCondominio.usuario().idUsuario()));
        auditoriaService.registrar(AcaoAuditoria.OCCUPANCY_STARTED, "Unit", unidade.getId(), Map.of("person", pessoa.getNome(),
                "role", request.tipo().name(), "financialResponsible", request.responsavelFinanceiro()));
        return VinculoResponse.de(vinculo);
    }

    @Transactional
    public VinculoResponse encerrarVinculo(UUID idCondominio, UUID idVinculo, EncerramentoVinculoRequest request) {
        acessoCondominio.exigirAtivo(idCondominio, Permissao.PEOPLE_EDIT);
        Vinculo vinculo = carregarVinculo(idCondominio, idVinculo);
        vinculo.encerrar(request.dataFim(), request.motivo() == null || request.motivo().isBlank() ? null : request.motivo().trim());
        vinculoRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.OCCUPANCY_ENDED, "Unit", vinculo.getUnidade().getId(), Map.of(
                "person", vinculo.getPessoa().getNome(), "role", vinculo.getTipo().name(),
                "endDate", request.dataFim().toString()));
        return VinculoResponse.de(vinculo);
    }

    /** Transfere a responsabilidade financeira da unidade para este vínculo vigente. */
    @Transactional
    public VinculoResponse definirResponsavelFinanceiro(UUID idCondominio, UUID idVinculo) {
        acessoCondominio.exigirAtivo(idCondominio, Permissao.PEOPLE_EDIT);
        Vinculo vinculo = carregarVinculo(idCondominio, idVinculo);
        if (!vinculo.isVigente()) {
            throw ApiException.regraNegocio("OCCUPANCY_ENDED", "O vínculo está encerrado.");
        }
        vinculoRepository.buscarResponsavelFinanceiroVigente(vinculo.getUnidade().getId())
                .filter(v -> !v.getId().equals(idVinculo))
                .ifPresent(anterior -> {
                    anterior.setResponsavelFinanceiro(false);
                    vinculoRepository.flush();
                });
        vinculo.setResponsavelFinanceiro(true);
        vinculoRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.FINANCIAL_RESPONSIBLE_CHANGED, "Unit", vinculo.getUnidade().getId(),
                Map.of("person", vinculo.getPessoa().getNome()));
        return VinculoResponse.de(vinculo);
    }

    // ---------------------------------------------------------------- apoio

    private Vinculo carregarVinculo(UUID idCondominio, UUID idVinculo) {
        return vinculoRepository.findById(idVinculo)
                .filter(v -> v.getIdAdministradora().equals(acessoCondominio.idAdministradora())
                        && v.getUnidade().getIdCondominio().equals(idCondominio))
                .orElseThrow(() -> ApiException.naoEncontrado("Vínculo"));
    }

    private Pessoa carregarVisivel(UUID id, Permissao permissao) {
        Pessoa pessoa = pessoaRepository.findByIdAndIdAdministradora(id, acessoCondominio.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        EscopoAcesso escopo = acessoService.exigirEscopo(permissao);
        if (!escopo.administradoraInteira()) {
            boolean visivel = vinculoRepository.buscarPorPessoa(id).stream()
                    .anyMatch(v -> escopo.idsCondominio().contains(v.getUnidade().getIdCondominio()));
            if (!visivel) {
                throw ApiException.naoEncontrado("Pessoa");
            }
        }
        return pessoa;
    }

    private void aplicar(Pessoa pessoa, PessoaRequest request) {
        String documento = DocumentosBrasileiros.normalizarDocumento(request.documento());
        if (documento != null) {
            boolean juridica = request.tipo() == Pessoa.Tipo.COMPANY;
            if (juridica != (documento.length() == 14)) {
                throw ApiException.regraNegocio("DOCUMENT_KIND_MISMATCH",
                        juridica ? "Pessoa jurídica deve ter CNPJ." : "Pessoa física deve ter CPF.");
            }
            pessoaRepository.findByIdAdministradoraAndDocumento(pessoa.getIdAdministradora(), documento)
                    .filter(outra -> !outra.getId().equals(pessoa.getId()))
                    .ifPresent(outra -> {
                        throw ApiException.conflito("PERSON_DOCUMENT_TAKEN",
                                "Já existe uma pessoa com este CPF/CNPJ: " + outra.getNome() + ".");
                    });
        }
        pessoa.atualizar(request.nome(), request.tipo(), documento, emBranco(request.email()),
                DocumentosBrasileiros.somenteDigitos(request.telefone()), DocumentosBrasileiros.somenteDigitos(request.telefoneSecundario()),
                emBranco(request.observacoes()));
    }

    private PessoaDetalhe toDetalhe(Pessoa p) {
        List<VinculoResponse> vinculos = vinculoRepository.buscarPorPessoa(p.getId()).stream()
                .map(VinculoResponse::de).toList();
        return new PessoaDetalhe(p.getId(), p.getNome(), p.getTipo(), p.getDocumento(), p.getEmail(), p.getTelefone(),
                p.getTelefoneSecundario(), p.getObservacoes(), p.isAtivo(), p.getVersao(),
                usuarioRepository.findByIdPessoa(p.getId()).isPresent(), vinculos);
    }

    private static String rotuloTipo(Vinculo.Tipo tipo) {
        return switch (tipo) {
            case OWNER -> "proprietário";
            case TENANT -> "inquilino";
            case RESIDENT -> "morador";
        };
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package br.com.tekcondo.ocorrencia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ocorrências, reclamações e solicitações de manutenção. */
@Service
public class OcorrenciaService {

    public record CadastroRequest(@NotBlank(message = "Informe o título.") @Size(max = 150) String titulo,
                                @NotBlank(message = "Descreva a ocorrência.") @Size(max = 4000) String descricao,
                                @NotNull(message = "Informe a categoria.") Ocorrencia.Categoria categoria,
                                Ocorrencia.Prioridade prioridade, UUID idUnidade) {
    }

    public record TriagemRequest(@NotNull Ocorrencia.Categoria categoria, @NotNull Ocorrencia.Prioridade prioridade,
                                @Size(max = 150) String nomeResponsavel, LocalDate prazo) {
    }

    public record SituacaoRequest(@NotNull(message = "Informe a nova situação.") Ocorrencia.Situacao situacao,
                                @Size(max = 2000) String mensagem) {
    }

    public record ComentarioRequest(@NotBlank(message = "Escreva a mensagem.") @Size(max = 2000) String mensagem, boolean interna) {
    }

    public record Resumo(UUID id, int numero, String titulo, Ocorrencia.Categoria categoria, Ocorrencia.Prioridade prioridade,
                          Ocorrencia.Situacao situacao, UUID idUnidade, String rotuloUnidade, String nomeSolicitante, String nomeResponsavel,
                          LocalDate prazo, boolean atrasada, Instant criadoEm, Instant resolvidaEm) {
    }

    public record Interacao(UUID id, String nomeAutor, String mensagem, Ocorrencia.Situacao situacaoAlterada, boolean interna,
                              Instant criadoEm) {
    }

    public record Detalhe(Resumo resumo, String descricao, String solucao, Long versao, List<Interacao> interacoes) {
    }

    public record Estatisticas(Map<Ocorrencia.Situacao, Long> porSituacao, long abertas, long atrasadas, Double mediaHorasSolucao) {
    }

    private final OcorrenciaRepository repository;
    private final InteracaoOcorrenciaRepository interacoes;
    private final UnidadeRepository unidades;
    private final AcessoCondominio acesso;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public OcorrenciaService(OcorrenciaRepository repository, InteracaoOcorrenciaRepository interacoes, UnidadeRepository unidades,
                             AcessoCondominio acesso, NotificacaoService notificacoes, AuditoriaService auditoria, Clock clock) {
        this.repository = repository;
        this.interacoes = interacoes;
        this.unidades = unidades;
        this.acesso = acesso;
        this.notificacoes = notificacoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<Resumo> listar(UUID idCondominio, List<Ocorrencia.Situacao> situacoes, Ocorrencia.Categoria categoria,
                                      Ocorrencia.Prioridade prioridade, String busca, Pageable paginacao) {
        acesso.exigir(idCondominio, Permissao.OCCURRENCE_VIEW);
        return PaginaResponse.of(repository.findAll(especificacao(idCondominio, situacoes, categoria, prioridade, busca, null), paginacao),
                this::resumo);
    }

    public Specification<Ocorrencia> especificacao(UUID idCondominio, List<Ocorrencia.Situacao> situacoes, Ocorrencia.Categoria categoria,
                                          Ocorrencia.Prioridade prioridade, String busca, java.util.Collection<UUID> idsUnidades) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idCondominio"), idCondominio));
            if (situacoes != null && !situacoes.isEmpty()) p.add(root.get("situacao").in(situacoes));
            if (categoria != null) p.add(cb.equal(root.get("categoria"), categoria));
            if (prioridade != null) p.add(cb.equal(root.get("prioridade"), prioridade));
            if (idsUnidades != null) p.add(root.get("idUnidade").in(idsUnidades));
            if (busca != null && !busca.isBlank()) {
                String like = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titulo")), like), cb.like(cb.lower(root.get("descricao")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.OCCURRENCE_VIEW);
        return detalhe(carregar(idCondominio, id), true);
    }

    @Transactional
    public Detalhe cadastrar(UUID idCondominio, CadastroRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.OCCURRENCE_EDIT);
        var usuario = acesso.usuario();
        return detalhe(cadastrarInterno(condominio, request, usuario.idUsuario(), usuario.nome()), true);
    }

    /** Usado também pelo portal do morador (permissão verificada pelo chamador). */
    public Ocorrencia cadastrarInterno(Condominio condominio, CadastroRequest request, UUID idUsuario, String nomeUsuario) {
        if (request.idUnidade() != null) {
            unidades.findByIdAndIdCondominio(request.idUnidade(), condominio.getId()).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        }
        Ocorrencia o = repository.save(new Ocorrencia(condominio.getIdAdministradora(), condominio.getId(),
                repository.proximoNumero(condominio.getId()), request.titulo().trim(), request.descricao().trim(),
                request.categoria(), request.prioridade() == null ? Ocorrencia.Prioridade.MEDIUM : request.prioridade(),
                request.idUnidade(), idUsuario, nomeUsuario));
        auditoria.registrar(AcaoAuditoria.OCCURRENCE_CREATED, "Occurrence", o.getId(), Map.of("number", o.getNumero(), "title", o.getTitulo()));
        notificacoes.notificar(condominio.getIdAdministradora(),
                notificacoes.usuariosComPermissao(condominio.getIdAdministradora(), condominio.getId(), Permissao.OCCURRENCE_EDIT)
                        .stream().filter(u -> !u.equals(idUsuario)).toList(),
                "Nova ocorrência nº " + o.getNumero(), o.getTitulo(), "/ocorrencias/" + o.getId(), null);
        return o;
    }

    @Transactional
    public Detalhe atualizar(UUID idCondominio, UUID id, TriagemRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.OCCURRENCE_EDIT);
        Ocorrencia o = carregar(idCondominio, id);
        o.triar(request.categoria(), request.prioridade(), aparadoOuNulo(request.nomeResponsavel()), request.prazo());
        repository.flush();
        auditoria.registrar(AcaoAuditoria.OCCURRENCE_UPDATED, "Occurrence", id, Map.of("priority", request.prioridade().name(),
                "assignee", String.valueOf(request.nomeResponsavel()), "dueDate", String.valueOf(request.prazo())));
        return detalhe(o, true);
    }

    @Transactional
    public Detalhe alterarSituacao(UUID idCondominio, UUID id, SituacaoRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.OCCURRENCE_EDIT);
        Ocorrencia o = carregar(idCondominio, id);
        var usuario = acesso.usuario();
        if (request.situacao() == Ocorrencia.Situacao.OPEN && o.getSituacao() != Ocorrencia.Situacao.OPEN) {
            o.reabrir();
        } else {
            o.alterarSituacao(request.situacao(), aparadoOuNulo(request.mensagem()), Instant.now(clock));
        }
        interacoes.save(new InteracaoOcorrencia(o.getId(), usuario.idUsuario(), usuario.nome(),
                request.mensagem() == null || request.mensagem().isBlank() ? "Situação alterada." : request.mensagem().trim(),
                request.situacao(), false));
        repository.flush();
        auditoria.registrar(AcaoAuditoria.OCCURRENCE_UPDATED, "Occurrence", id, Map.of("status", request.situacao().name()));
        if (o.getIdUsuarioSolicitante() != null && !o.getIdUsuarioSolicitante().equals(usuario.idUsuario())) {
            notificacoes.notificar(o.getIdAdministradora(), List.of(o.getIdUsuarioSolicitante()),
                    "Ocorrência nº " + o.getNumero() + " atualizada", rotuloSituacao(request.situacao()), "/portal/ocorrencias/" + o.getId(), null);
        }
        return detalhe(o, true);
    }

    @Transactional
    public Detalhe comentar(UUID idCondominio, UUID id, ComentarioRequest request) {
        acesso.exigir(idCondominio, Permissao.OCCURRENCE_EDIT);
        Ocorrencia o = carregar(idCondominio, id);
        var usuario = acesso.usuario();
        interacoes.save(new InteracaoOcorrencia(o.getId(), usuario.idUsuario(), usuario.nome(), request.mensagem().trim(), null,
                request.interna()));
        if (!request.interna() && o.getIdUsuarioSolicitante() != null && !o.getIdUsuarioSolicitante().equals(usuario.idUsuario())) {
            notificacoes.notificar(o.getIdAdministradora(), List.of(o.getIdUsuarioSolicitante()),
                    "Nova mensagem na ocorrência nº " + o.getNumero(), request.mensagem().trim(), "/portal/ocorrencias/" + o.getId(), null);
        }
        return detalhe(o, true);
    }

    @Transactional(readOnly = true)
    public Estatisticas estatisticas(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.OCCURRENCE_VIEW);
        LocalDate hoje = LocalDate.now(clock);
        Map<Ocorrencia.Situacao, Long> porSituacao = new EnumMap<>(Ocorrencia.Situacao.class);
        long atrasadas = 0;
        long quantidadeResolvidas = 0;
        long horasResolucao = 0;
        for (Ocorrencia o : repository.findAll(especificacao(idCondominio, null, null, null, null, null))) {
            porSituacao.merge(o.getSituacao(), 1L, Long::sum);
            if (o.isAtrasada(hoje)) atrasadas++;
            if (o.getSituacao() == Ocorrencia.Situacao.RESOLVED && o.getResolvidaEm() != null) {
                quantidadeResolvidas++;
                horasResolucao += Duration.between(o.getCriadoEm(), o.getResolvidaEm()).toHours();
            }
        }
        long abertas = porSituacao.getOrDefault(Ocorrencia.Situacao.OPEN, 0L) + porSituacao.getOrDefault(Ocorrencia.Situacao.IN_PROGRESS, 0L)
                + porSituacao.getOrDefault(Ocorrencia.Situacao.WAITING, 0L);
        return new Estatisticas(porSituacao, abertas, atrasadas, quantidadeResolvidas == 0 ? null : (double) horasResolucao / quantidadeResolvidas);
    }

    // ------------------------------------------------------------------

    public Ocorrencia carregar(UUID idCondominio, UUID id) {
        return repository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Ocorrência"));
    }

    public Resumo resumo(Ocorrencia o) {
        String rotuloUnidade = o.getIdUnidade() == null ? null : unidades.findById(o.getIdUnidade()).map(u -> u.rotulo()).orElse(null);
        return new Resumo(o.getId(), o.getNumero(), o.getTitulo(), o.getCategoria(), o.getPrioridade(), o.getSituacao(),
                o.getIdUnidade(), rotuloUnidade, o.getNomeSolicitante(), o.getNomeResponsavel(), o.getPrazo(),
                o.isAtrasada(LocalDate.now(clock)), o.getCriadoEm(), o.getResolvidaEm());
    }

    public Detalhe detalhe(Ocorrencia o, boolean incluirInternas) {
        List<Interacao> lista = interacoes.findByIdOcorrenciaOrderByCriadoEmAsc(o.getId()).stream()
                .filter(i -> incluirInternas || !i.isInterna())
                .map(i -> new Interacao(i.getId(), i.getNomeAutor(), i.getMensagem(), i.getSituacaoAlterada(), i.isInterna(),
                        i.getCriadoEm()))
                .toList();
        return new Detalhe(resumo(o), o.getDescricao(), o.getSolucao(), o.getVersao(), lista);
    }

    private static String rotuloSituacao(Ocorrencia.Situacao s) {
        return switch (s) {
            case OPEN -> "Reaberta";
            case IN_PROGRESS -> "Em atendimento";
            case WAITING -> "Aguardando";
            case RESOLVED -> "Resolvida";
            case CANCELLED -> "Cancelada";
        };
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

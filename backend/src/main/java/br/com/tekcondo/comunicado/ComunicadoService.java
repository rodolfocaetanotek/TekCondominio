package br.com.tekcondo.comunicado;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.email.EmailDispatcher;
import br.com.tekcondo.integracao.email.MensagemEmail;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.repository.BlocoRepository;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comunicados com público-alvo, notificação interna, e-mail opcional e controle de leitura. */
@Service
public class ComunicadoService {

    public record ComunicadoRequest(@NotBlank(message = "Informe o título.") @Size(max = 150) String titulo,
                          @NotBlank(message = "Escreva o comunicado.") @Size(max = 8000) String texto,
                          @NotNull(message = "Informe o público.") Comunicado.PublicoAlvo publicoAlvo,
                          UUID idBloco, UUID idUnidade, boolean fixado, boolean enviarEmail, LocalDate dataExpiracao) {
    }

    public record ComunicadoResponse(UUID id, String titulo, String texto, Comunicado.PublicoAlvo publicoAlvo, UUID idBloco,
                           String nomeBloco, UUID idUnidade, String rotuloUnidade, boolean fixado, boolean enviarEmail,
                           LocalDate dataExpiracao, Instant publicadoEm, boolean arquivado, String nomeCriadoPor,
                           Long quantidadeLeituras, Boolean lido) {
    }

    private final ComunicadoRepository repository;
    private final BlocoRepository blocos;
    private final UnidadeRepository unidades;
    private final AcessoCondominio acesso;
    private final NotificacaoService notificacoes;
    private final EmailDispatcher emailDispatcher;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ComunicadoService(ComunicadoRepository repository, BlocoRepository blocos, UnidadeRepository unidades,
                               AcessoCondominio acesso, NotificacaoService notificacoes, EmailDispatcher emailDispatcher,
                               AuditoriaService auditoria, Clock clock) {
        this.repository = repository;
        this.blocos = blocos;
        this.unidades = unidades;
        this.acesso = acesso;
        this.notificacoes = notificacoes;
        this.emailDispatcher = emailDispatcher;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ComunicadoResponse> listar(UUID idCondominio, boolean incluirArquivados) {
        acesso.exigir(idCondominio, Permissao.ANNOUNCEMENT_VIEW);
        UUID idUsuario = acesso.usuario().idUsuario();
        return repository.buscarDoCondominio(idCondominio).stream()
                .filter(a -> incluirArquivados || a.getArquivadoEm() == null)
                .map(a -> paraResponse(a, repository.contarLeituras(a.getId()), repository.foiLido(a.getId(), idUsuario)))
                .toList();
    }

    @Transactional
    public ComunicadoResponse publicar(UUID idCondominio, ComunicadoRequest r) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.ANNOUNCEMENT_EDIT);
        if (r.idBloco() != null) {
            blocos.findByIdAndIdCondominio(r.idBloco(), idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Bloco"));
        }
        if (r.idUnidade() != null) {
            unidades.findByIdAndIdCondominio(r.idUnidade(), idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        }
        var usuario = acesso.usuario();
        Comunicado a = repository.save(new Comunicado(condominio.getIdAdministradora(), idCondominio, r.titulo().trim(),
                r.texto().trim(), r.publicoAlvo(), r.idBloco(), r.idUnidade(), r.fixado(), r.enviarEmail(), r.dataExpiracao(),
                Instant.now(clock), usuario.idUsuario(), usuario.nome()));
        distribuir(condominio, a);
        auditoria.registrar(AcaoAuditoria.ANNOUNCEMENT_PUBLISHED, "Announcement", a.getId(), Map.of("title", a.getTitulo(),
                "audience", a.getPublicoAlvo().name(), "email", a.isEnviarEmail()));
        return paraResponse(a, 0L, false);
    }

    /** Usado também na convocação de assembleia. */
    public void distribuir(Condominio condominio, Comunicado a) {
        List<UUID> destinatarios = new ArrayList<>(repository.usuariosMoradores(condominio.getId(), a.getPublicoAlvo().name(),
                a.getIdBloco(), a.getIdUnidade()));
        destinatarios.addAll(notificacoes.usuariosComPermissao(condominio.getIdAdministradora(), condominio.getId(),
                Permissao.ANNOUNCEMENT_VIEW));
        notificacoes.notificar(condominio.getIdAdministradora(), destinatarios, "Comunicado: " + a.getTitulo(),
                a.getTexto().length() > 200 ? a.getTexto().substring(0, 200) + "…" : a.getTexto(), "/comunicados", null);
        if (a.isEnviarEmail()) {
            for (String email : repository.emailsMoradores(condominio.getId(), a.getPublicoAlvo().name(), a.getIdBloco(), a.getIdUnidade())) {
                emailDispatcher.enviarAposCommit(new MensagemEmail(email, "[" + condominio.getNome() + "] " + a.getTitulo(),
                        a.getTexto() + "\n\n— " + condominio.getNome() + "\nMensagem enviada pelo Tek Condomínio."));
            }
        }
    }

    @Transactional
    public ComunicadoResponse arquivar(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.ANNOUNCEMENT_EDIT);
        Comunicado a = repository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Comunicado"));
        a.arquivar(Instant.now(clock));
        auditoria.registrar(AcaoAuditoria.ANNOUNCEMENT_ARCHIVED, "Announcement", id, null);
        return paraResponse(a, repository.contarLeituras(id), null);
    }

    @Transactional
    public void marcarLido(UUID idCondominio, UUID id) {
        repository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Comunicado"));
        repository.marcarLido(id, acesso.usuario().idUsuario(), Instant.now(clock));
    }

    public ComunicadoResponse paraResponse(Comunicado a, Long quantidadeLeituras, Boolean lido) {
        String nomeBloco = a.getIdBloco() == null ? null : blocos.findById(a.getIdBloco()).map(b -> b.getNome()).orElse(null);
        String rotuloUnidade = a.getIdUnidade() == null ? null : unidades.findById(a.getIdUnidade()).map(u -> u.rotulo()).orElse(null);
        return new ComunicadoResponse(a.getId(), a.getTitulo(), a.getTexto(), a.getPublicoAlvo(), a.getIdBloco(), nomeBloco, a.getIdUnidade(),
                rotuloUnidade, a.isFixado(), a.isEnviarEmail(), a.getDataExpiracao(), a.getPublicadoEm(), a.getArquivadoEm() != null,
                a.getNomeCriadoPor(), quantidadeLeituras, lido);
    }
}

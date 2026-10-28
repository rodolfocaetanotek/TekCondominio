package br.com.tekcondo.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canal de notificação interno. Outros canais (e-mail etc.) são acionados pelos módulos via EmailDispatcher. */
@Service
public class NotificacaoService {

    public record NotificacaoResponse(UUID id, String titulo, String texto, String link, boolean lida, Instant criadoEm) {
    }

    public record CaixaEntrada(long naoLidas, List<NotificacaoResponse> itens) {
    }

    private final NotificacaoRepository repository;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, UsuarioAtualProvider usuarioAtualProvider, Clock clock) {
        this.repository = repository;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.clock = clock;
    }

    /** Envia para cada usuário; com chaveDeduplicacao, não repete a mesma notificação ao mesmo usuário. */
    @Transactional
    public int notificar(UUID idAdministradora, Collection<UUID> idsUsuario, String titulo, String texto, String link, String chaveDeduplicacao) {
        int enviadas = 0;
        for (UUID idUsuario : idsUsuario.stream().distinct().toList()) {
            if (chaveDeduplicacao != null && repository.existsByIdUsuarioAndChaveDeduplicacao(idUsuario, chaveDeduplicacao)) {
                continue;
            }
            repository.save(new Notificacao(idAdministradora, idUsuario, titulo, texto, link, chaveDeduplicacao));
            enviadas++;
        }
        return enviadas;
    }

    public List<UUID> usuariosComPermissao(UUID idAdministradora, UUID idCondominio, Permissao permissao) {
        return repository.usuariosComPermissao(idAdministradora, idCondominio, permissao.name());
    }

    @Transactional(readOnly = true)
    public CaixaEntrada caixaEntrada() {
        UUID idUsuario = usuarioAtualProvider.obter().idUsuario();
        List<NotificacaoResponse> itens = repository.findByIdUsuarioOrderByCriadoEmDesc(idUsuario, PageRequest.of(0, 30)).stream()
                .map(n -> new NotificacaoResponse(n.getId(), n.getTitulo(), n.getTexto(), n.getLink(), n.getLidaEm() != null,
                        n.getCriadoEm()))
                .toList();
        return new CaixaEntrada(repository.countByIdUsuarioAndLidaEmIsNull(idUsuario), itens);
    }

    @Transactional
    public void marcarLida(UUID id) {
        UUID idUsuario = usuarioAtualProvider.obter().idUsuario();
        Notificacao n = repository.findById(id).filter(x -> x.getIdUsuario().equals(idUsuario))
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação"));
        n.marcarLida(Instant.now(clock));
    }

    @Transactional
    public void marcarTodasLidas() {
        repository.marcarTodasLidas(usuarioAtualProvider.obter().idUsuario(), Instant.now(clock));
    }
}

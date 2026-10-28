package br.com.tekcondo.auditoria.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.entity.RegistroAuditoria;
import br.com.tekcondo.auditoria.repository.RegistroAuditoriaRepository;
import br.com.tekcondo.comum.security.IpCliente;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a trilha de auditoria na MESMA transação da operação auditada: se a operação for revertida,
 * o registro de auditoria também é (não há registro de algo que não aconteceu).
 * Nunca passe senhas, tokens ou segredos em {@code detalhes}.
 */
@Service
public class AuditoriaService {

    private final RegistroAuditoriaRepository repository;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final Clock clock;

    public AuditoriaService(RegistroAuditoriaRepository repository, UsuarioAtualProvider usuarioAtualProvider, Clock clock) {
        this.repository = repository;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.clock = clock;
    }

    /** Auditoria de ação do usuário autenticado na requisição corrente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AcaoAuditoria acao, String tipoEntidade, UUID idEntidade, Map<String, Object> detalhes) {
        UsuarioAtual usuario = usuarioAtualProvider.obter();
        salvar(usuario.idAdministradora(), usuario.idUsuario(), usuario.email(), acao, tipoEntidade, idEntidade, detalhes);
    }

    /** Auditoria com ator explícito (ex.: login, quando ainda não há usuário autenticado no contexto). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarComo(UUID idAdministradora, UUID idUsuario, String emailUsuario, AcaoAuditoria acao, String tipoEntidade,
                         UUID idEntidade, Map<String, Object> detalhes) {
        salvar(idAdministradora, idUsuario, emailUsuario, acao, tipoEntidade, idEntidade, detalhes);
    }

    private void salvar(UUID idAdministradora, UUID idUsuario, String emailUsuario, AcaoAuditoria acao, String tipoEntidade,
                      UUID idEntidade, Map<String, Object> detalhes) {
        repository.save(new RegistroAuditoria(idAdministradora, idUsuario, emailUsuario, acao.name(), tipoEntidade,
                idEntidade == null ? null : idEntidade.toString(), detalhes == null || detalhes.isEmpty() ? null : detalhes,
                IpCliente.atual(), Instant.now(clock)));
    }
}

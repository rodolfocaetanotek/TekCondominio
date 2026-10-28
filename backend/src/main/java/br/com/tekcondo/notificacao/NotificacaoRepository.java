package br.com.tekcondo.notificacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findByIdUsuarioOrderByCriadoEmDesc(UUID idUsuario, Pageable pageable);

    long countByIdUsuarioAndLidaEmIsNull(UUID idUsuario);

    boolean existsByIdUsuarioAndChaveDeduplicacao(UUID idUsuario, String chaveDeduplicacao);

    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora where n.idUsuario = :idUsuario and n.lidaEm is null")
    int marcarTodasLidas(UUID idUsuario, Instant agora);

    /** Usuários ativos com a permissão no condomínio (ou em toda a organização). */
    @Query(value = """
            select distinct ur.user_id from user_role ur
              join role_permission rp on rp.role_id = ur.role_id
              join app_user u on u.id = ur.user_id
             where u.active and ur.organization_id = :idAdministradora and rp.permission_code = :permissao
               and (ur.condominium_id is null or ur.condominium_id = :idCondominio)
            """, nativeQuery = true)
    List<UUID> usuariosComPermissao(UUID idAdministradora, UUID idCondominio, String permissao);
}

package br.com.tekcondo.acesso.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.acesso.autorizacao.ConcessaoPermissao;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PapelUsuarioRepository extends JpaRepository<PapelUsuario, UUID> {

    /**
     * Concessões efetivas de um usuário ATIVO dentro da sua organização. Usuário inativo não recebe
     * nenhuma concessão, mesmo com token ainda válido.
     */
    @Query("""
            select new br.com.tekcondo.acesso.autorizacao.ConcessaoPermissao(pu.idCondominio, p)
            from PapelUsuario pu
              join pu.papel pa
              join pa.permissoes p,
              Usuario u
            where u.id = pu.idUsuario
              and u.ativo = true
              and pu.idUsuario = :idUsuario
              and pu.idAdministradora = :idAdministradora
            """)
    List<ConcessaoPermissao> buscarConcessoes(UUID idUsuario, UUID idAdministradora);

    @Query("select pu from PapelUsuario pu join fetch pu.papel where pu.idUsuario = :idUsuario order by pu.criadoEm")
    List<PapelUsuario> buscarPorUsuarioComPapel(UUID idUsuario);

    @Query("select pu from PapelUsuario pu join fetch pu.papel where pu.idUsuario in :idsUsuario")
    List<PapelUsuario> buscarPorUsuariosComPapel(Collection<UUID> idsUsuario);

    @Query("""
            select count(pu) > 0 from PapelUsuario pu
            where pu.idUsuario = :idUsuario and pu.papel.id = :idPapel and pu.idCondominio is null
            """)
    boolean existeNaAdministradora(UUID idUsuario, UUID idPapel);

    @Query("""
            select count(pu) > 0 from PapelUsuario pu
            where pu.idUsuario = :idUsuario and pu.papel.id = :idPapel and pu.idCondominio = :idCondominio
            """)
    boolean existeNoCondominio(UUID idUsuario, UUID idPapel, UUID idCondominio);

    /** Administradores ativos (papel de administrador para toda a organização). */
    @Query("""
            select count(distinct pu.idUsuario) from PapelUsuario pu, Usuario u
            where u.id = pu.idUsuario and u.ativo = true
              and pu.idAdministradora = :idAdministradora
              and pu.idCondominio is null
              and pu.papel.codigo = :codigoPapelAdministrador
            """)
    long contarAdministradoresAtivos(UUID idAdministradora, String codigoPapelAdministrador);

    default long contarAdministradoresAtivosDaAdministradora(UUID idAdministradora) {
        return contarAdministradoresAtivos(idAdministradora, br.com.tekcondo.acesso.entity.Papel.ORG_ADMIN);
    }
}

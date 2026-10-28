package br.com.tekcondo.acesso.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.acesso.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    @Query("select u from Usuario u where lower(u.email) = lower(:email)")
    Optional<Usuario> buscarPorEmail(String email);

    @Query("select count(u) > 0 from Usuario u where lower(u.email) = lower(:email)")
    boolean existePorEmail(String email);

    Optional<Usuario> findByIdAndIdAdministradora(UUID id, UUID idAdministradora);

    Optional<Usuario> findByIdPessoa(UUID idPessoa);
}

package br.com.tekcondo.acesso.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.acesso.entity.Papel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PapelRepository extends JpaRepository<Papel, UUID> {

    @Query("select p from Papel p where p.idAdministradora is null and p.codigo = :codigo")
    Optional<Papel> buscarPapelSistema(String codigo);

    /** Papéis disponíveis para a organização: os do sistema e os próprios dela. */
    @Query("""
            select p from Papel p
            where p.idAdministradora is null or p.idAdministradora = :idAdministradora
            order by p.escopo, p.nome
            """)
    List<Papel> buscarDisponiveisPara(UUID idAdministradora);
}

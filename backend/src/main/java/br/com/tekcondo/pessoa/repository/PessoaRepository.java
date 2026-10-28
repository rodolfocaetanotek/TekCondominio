package br.com.tekcondo.pessoa.repository;

import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.pessoa.entity.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID>, JpaSpecificationExecutor<Pessoa> {

    Optional<Pessoa> findByIdAndIdAdministradora(UUID id, UUID idAdministradora);

    Optional<Pessoa> findByIdAdministradoraAndDocumento(UUID idAdministradora, String documento);
}

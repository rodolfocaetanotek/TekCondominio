package br.com.tekcondo.administradora.repository;

import java.util.UUID;

import br.com.tekcondo.administradora.entity.Administradora;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministradoraRepository extends JpaRepository<Administradora, UUID> {

    boolean existsByCnpj(String cnpj);
}

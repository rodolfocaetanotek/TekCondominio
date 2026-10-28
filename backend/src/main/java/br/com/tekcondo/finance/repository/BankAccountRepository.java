package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByCondominiumIdOrderByNameAsc(UUID condominiumId);

    Optional<BankAccount> findByIdAndCondominiumId(UUID id, UUID condominiumId);

    @Query("select b from BankAccount b where b.condominiumId = :condominiumId and lower(b.name) = lower(:name)")
    Optional<BankAccount> findByName(UUID condominiumId, String name);
}

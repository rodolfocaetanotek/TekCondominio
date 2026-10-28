package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.finance.entity.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {

    boolean existsByBankAccountIdAndFileHash(UUID bankAccountId, String fileHash);

    List<StatementImport> findTop20ByBankAccountIdOrderByCreatedAtDesc(UUID bankAccountId);
}

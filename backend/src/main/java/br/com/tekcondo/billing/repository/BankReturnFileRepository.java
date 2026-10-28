package br.com.tekcondo.billing.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.billing.entity.BankReturnFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankReturnFileRepository extends JpaRepository<BankReturnFile, UUID> {

    boolean existsByBankAccountIdAndFileHash(UUID bankAccountId, String fileHash);

    List<BankReturnFile> findTop30ByCondominiumIdOrderByCreatedAtDesc(UUID condominiumId);
}

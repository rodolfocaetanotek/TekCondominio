package br.com.tekcondo.supplier.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.supplier.entity.ContractHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractHistoryRepository extends JpaRepository<ContractHistory, UUID> {

    List<ContractHistory> findByContractIdOrderByCreatedAtDesc(UUID contractId);
}

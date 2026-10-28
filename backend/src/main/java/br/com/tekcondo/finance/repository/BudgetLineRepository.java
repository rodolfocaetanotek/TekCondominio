package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.finance.entity.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {

    List<BudgetLine> findByCondominiumIdAndYear(UUID condominiumId, int year);
}

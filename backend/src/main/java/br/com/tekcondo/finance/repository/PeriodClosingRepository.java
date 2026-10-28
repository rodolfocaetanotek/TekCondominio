package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.UUID;

import br.com.tekcondo.finance.entity.PeriodClosing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodClosingRepository extends JpaRepository<PeriodClosing, UUID> {

    List<PeriodClosing> findByCondominiumIdOrderByCreatedAtDesc(UUID condominiumId);
}

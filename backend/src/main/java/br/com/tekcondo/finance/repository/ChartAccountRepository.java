package br.com.tekcondo.finance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.finance.entity.ChartAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, UUID> {

    /** Plano padrão do sistema + contas próprias da administradora. */
    @Query("select a from ChartAccount a where a.organizationId is null or a.organizationId = :organizationId order by a.code")
    List<ChartAccount> findAvailable(UUID organizationId);

    @Query("select a from ChartAccount a where a.id = :id and (a.organizationId is null or a.organizationId = :organizationId)")
    Optional<ChartAccount> findAvailable(UUID id, UUID organizationId);

    @Query("""
            select count(a) > 0 from ChartAccount a
            where (a.organizationId is null or a.organizationId = :organizationId) and a.code = :code
            """)
    boolean existsCode(UUID organizationId, String code);

    @Query("select a from ChartAccount a where a.organizationId is null and a.code = :code")
    Optional<ChartAccount> findSystemByCode(String code);
}

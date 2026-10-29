package br.com.tekcondo.portaria;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AutorizacaoVisitaRepository extends JpaRepository<AutorizacaoVisita, UUID> {

    Optional<AutorizacaoVisita> findByIdAndIdCondominio(UUID id, UUID idCondominio);

    @Query("""
            select v from AutorizacaoVisita v where v.idCondominio = :idCondominio and v.codigoAcesso = :codigo
              and v.situacao = br.com.tekcondo.portaria.AutorizacaoVisita.Situacao.EXPECTED
            """)
    Optional<AutorizacaoVisita> buscarEsperadaPorCodigo(UUID idCondominio, String codigo);

    /** Portaria do dia: esperados válidos hoje, quem está dentro e o que entrou/saiu hoje. */
    @Query("""
            select v from AutorizacaoVisita v where v.idCondominio = :idCondominio and (
                (v.situacao = br.com.tekcondo.portaria.AutorizacaoVisita.Situacao.EXPECTED and :dia between v.validaDe and v.validaAte)
                or v.situacao = br.com.tekcondo.portaria.AutorizacaoVisita.Situacao.INSIDE
                or (v.entradaEm >= :inicioDia and v.entradaEm < :fimDia))
            order by v.situacao, v.nome
            """)
    List<AutorizacaoVisita> buscarDoDia(UUID idCondominio, LocalDate dia, Instant inicioDia, Instant fimDia);

    @Query("""
            select v from AutorizacaoVisita v where v.idCondominio = :idCondominio and v.criadoEm >= :de and v.criadoEm < :ate
            order by v.criadoEm desc
            """)
    List<AutorizacaoVisita> buscarCriadasEntre(UUID idCondominio, Instant de, Instant ate);

    @Query("select v from AutorizacaoVisita v where v.idCondominio = :idCondominio and v.idUnidade in :idsUnidades order by v.validaDe desc, v.criadoEm desc")
    List<AutorizacaoVisita> buscarDasUnidades(UUID idCondominio, Collection<UUID> idsUnidades);

    /** Registros encerrados há mais tempo que a retenção e ainda não anonimizados. */
    @Query("""
            select v from AutorizacaoVisita v where v.anonimizadoEm is null and v.validaAte < :antesDe
              and v.situacao <> br.com.tekcondo.portaria.AutorizacaoVisita.Situacao.INSIDE
            """)
    List<AutorizacaoVisita> buscarParaAnonimizar(LocalDate antesDe);
}

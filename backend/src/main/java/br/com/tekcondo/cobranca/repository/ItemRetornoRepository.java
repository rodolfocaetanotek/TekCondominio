package br.com.tekcondo.cobranca.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.cobranca.entity.ItemRetorno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemRetornoRepository extends JpaRepository<ItemRetorno, UUID> {

    List<ItemRetorno> findByIdArquivoOrderBySequenciaAsc(UUID idArquivo);

    @Query("select i from ItemRetorno i where i.idCondominio = :idCondominio and i.situacao = :situacao order by i.dataOcorrencia desc")
    List<ItemRetorno> buscarPorSituacao(UUID idCondominio, ItemRetorno.Situacao situacao);

    @Query("select i from ItemRetorno i where i.id = :id and i.idCondominio = :idCondominio")
    Optional<ItemRetorno> buscarNoCondominio(UUID id, UUID idCondominio);

    @Query("""
            select count(i) > 0 from ItemRetorno i where i.idContaBancaria = :idConta and i.situacao = 'SETTLED'
              and i.codigoMovimento = :movimento and i.dataOcorrencia = :ocorrencia
              and coalesce(i.nossoNumero, '') = coalesce(:nossoNumero, '') and coalesce(i.seuNumero, '') = coalesce(:seuNumero, '')
            """)
    boolean jaBaixado(UUID idConta, String movimento, java.time.LocalDate ocorrencia, String nossoNumero, String seuNumero);
}

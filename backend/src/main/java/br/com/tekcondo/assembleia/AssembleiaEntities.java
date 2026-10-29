package br.com.tekcondo.assembleia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Entidades do módulo de assembleias. */
public final class AssembleiaEntities {

    private AssembleiaEntities() {
    }

    @Entity
    @Table(name = "assembly")
    public static class Assembleia extends AuditableEntity {

        public enum Tipo {
            ORDINARY, EXTRAORDINARY
        }

        public enum CriterioVoto {
            UNIT, IDEAL_FRACTION
        }

        public enum Situacao {
            DRAFT, CALLED, IN_PROGRESS, CLOSED, CANCELLED
        }

        @Column(name = "organization_id", nullable = false, updatable = false)
        private UUID idAdministradora;

        @Column(name = "condominium_id", nullable = false, updatable = false)
        private UUID idCondominio;

        @Column(name = "title", nullable = false)
        private String titulo;

        @Enumerated(EnumType.STRING)
        @Column(name = "kind", nullable = false)
        private Tipo tipo;

        @Column(name = "scheduled_at", nullable = false)
        private Instant primeiraConvocacaoEm;

        @Column(name = "second_call_at")
        private Instant segundaConvocacaoEm;

        @Column(name = "location", nullable = false)
        private String local;

        @Enumerated(EnumType.STRING)
        @Column(name = "vote_weight", nullable = false)
        private CriterioVoto criterioVoto;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private Situacao situacao = Situacao.DRAFT;

        @Column(name = "minutes")
        private String ata;

        @Column(name = "called_at")
        private Instant convocadaEm;

        @Column(name = "closed_at")
        private Instant encerradaEm;

        @Column(name = "cancel_reason")
        private String motivoCancelamento;

        @Column(name = "created_by", updatable = false)
        private UUID criadoPor;

        protected Assembleia() {
        }

        public Assembleia(UUID idAdministradora, UUID idCondominio, UUID criadoPor) {
            this.idAdministradora = idAdministradora;
            this.idCondominio = idCondominio;
            this.criadoPor = criadoPor;
        }

        public void atualizar(String titulo, Tipo tipo, Instant primeiraConvocacaoEm, Instant segundaConvocacaoEm, String local, CriterioVoto criterioVoto) {
            if (situacao != Situacao.DRAFT && situacao != Situacao.CALLED) {
                throw ApiException.regraNegocio("ASSEMBLY_LOCKED", "A assembleia já foi iniciada e não pode ser alterada.");
            }
            if (segundaConvocacaoEm != null && segundaConvocacaoEm.isBefore(primeiraConvocacaoEm)) {
                throw ApiException.regraNegocio("SECOND_CALL_BEFORE_FIRST", "A segunda convocação deve ser após a primeira.");
            }
            this.titulo = titulo;
            this.tipo = tipo;
            this.primeiraConvocacaoEm = primeiraConvocacaoEm;
            this.segundaConvocacaoEm = segundaConvocacaoEm;
            this.local = local;
            this.criterioVoto = criterioVoto;
        }

        public void convocar(Instant agora) {
            exigir(Situacao.DRAFT, "Somente assembleias em rascunho podem ser convocadas.");
            situacao = Situacao.CALLED;
            convocadaEm = agora;
        }

        public void iniciar() {
            exigir(Situacao.CALLED, "A assembleia precisa estar convocada para ser iniciada.");
            situacao = Situacao.IN_PROGRESS;
        }

        public void encerrar(Instant agora) {
            exigir(Situacao.IN_PROGRESS, "Somente assembleias em andamento podem ser encerradas.");
            situacao = Situacao.CLOSED;
            encerradaEm = agora;
        }

        public void cancelar(String motivo) {
            if (situacao == Situacao.CLOSED || situacao == Situacao.CANCELLED) {
                throw ApiException.regraNegocio("ASSEMBLY_FINISHED", "A assembleia já foi encerrada ou cancelada.");
            }
            situacao = Situacao.CANCELLED;
            motivoCancelamento = motivo;
        }

        public void registrarAta(String texto) {
            if (situacao != Situacao.IN_PROGRESS && situacao != Situacao.CLOSED) {
                throw ApiException.regraNegocio("ASSEMBLY_NOT_HELD", "A ata é registrada durante ou após a realização da assembleia.");
            }
            ata = texto;
        }

        public void exigirEmAndamento() {
            exigir(Situacao.IN_PROGRESS, "Disponível apenas com a assembleia em andamento.");
        }

        private void exigir(Situacao esperada, String mensagem) {
            if (situacao != esperada) {
                throw ApiException.regraNegocio("ASSEMBLY_INVALID_STATUS", mensagem);
            }
        }

        public UUID getIdAdministradora() {
            return idAdministradora;
        }

        public UUID getIdCondominio() {
            return idCondominio;
        }

        public String getTitulo() {
            return titulo;
        }

        public Tipo getTipo() {
            return tipo;
        }

        public Instant getPrimeiraConvocacaoEm() {
            return primeiraConvocacaoEm;
        }

        public Instant getSegundaConvocacaoEm() {
            return segundaConvocacaoEm;
        }

        public String getLocal() {
            return local;
        }

        public CriterioVoto getCriterioVoto() {
            return criterioVoto;
        }

        public Situacao getSituacao() {
            return situacao;
        }

        public String getAta() {
            return ata;
        }

        public Instant getConvocadaEm() {
            return convocadaEm;
        }

        public Instant getEncerradaEm() {
            return encerradaEm;
        }

        public String getMotivoCancelamento() {
            return motivoCancelamento;
        }
    }

    @Entity
    @Table(name = "assembly_agenda_item")
    public static class ItemPauta {

        @Id
        @Column(name = "id")
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID idAssembleia;

        @Column(name = "position", nullable = false)
        private int posicao;

        @Column(name = "title", nullable = false)
        private String titulo;

        @Column(name = "description")
        private String descricao;

        @Column(name = "voting", nullable = false)
        private boolean deliberativo;

        @Column(name = "deliberation")
        private String deliberacao;

        @Column(name = "responsible")
        private String responsavel;

        @Column(name = "deadline")
        private LocalDate prazo;

        protected ItemPauta() {
        }

        public ItemPauta(UUID idAssembleia, int posicao, String titulo, String descricao, boolean deliberativo) {
            this.idAssembleia = idAssembleia;
            this.posicao = posicao;
            this.titulo = titulo;
            this.descricao = descricao;
            this.deliberativo = deliberativo;
        }

        public void deliberar(String deliberacao, String responsavel, LocalDate prazo) {
            this.deliberacao = deliberacao;
            this.responsavel = responsavel;
            this.prazo = prazo;
        }

        public UUID getId() {
            return id;
        }

        public UUID getIdAssembleia() {
            return idAssembleia;
        }

        public int getPosicao() {
            return posicao;
        }

        public String getTitulo() {
            return titulo;
        }

        public String getDescricao() {
            return descricao;
        }

        public boolean isDeliberativo() {
            return deliberativo;
        }

        public String getDeliberacao() {
            return deliberacao;
        }

        public String getResponsavel() {
            return responsavel;
        }

        public LocalDate getPrazo() {
            return prazo;
        }
    }

    @Entity
    @Table(name = "assembly_attendance")
    public static class Presenca {

        @Id
        @Column(name = "id")
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID idAssembleia;

        @Column(name = "unit_id", nullable = false, updatable = false)
        private UUID idUnidade;

        @Column(name = "attendee_name", nullable = false, updatable = false)
        private String nomePresente;

        @Column(name = "by_proxy", nullable = false, updatable = false)
        private boolean porProcuracao;

        @Column(name = "proxy_name", updatable = false)
        private String nomeProcurador;

        @Column(name = "registered_at", nullable = false, updatable = false)
        private Instant registradoEm;

        @Column(name = "registered_by", updatable = false)
        private UUID registradoPor;

        protected Presenca() {
        }

        public Presenca(UUID idAssembleia, UUID idUnidade, String nomePresente, boolean porProcuracao, String nomeProcurador, UUID registradoPor) {
            if (porProcuracao && (nomeProcurador == null || nomeProcurador.isBlank())) {
                throw ApiException.requisicaoInvalida("PROXY_NAME_REQUIRED", "Informe o nome do procurador.");
            }
            this.idAssembleia = idAssembleia;
            this.idUnidade = idUnidade;
            this.nomePresente = nomePresente;
            this.porProcuracao = porProcuracao;
            this.nomeProcurador = porProcuracao ? nomeProcurador : null;
            this.registradoPor = registradoPor;
        }

        @PrePersist
        void aoCriar() {
            registradoEm = Instant.now();
        }

        public UUID getId() {
            return id;
        }

        public UUID getIdUnidade() {
            return idUnidade;
        }

        public UUID getIdAssembleia() {
            return idAssembleia;
        }

        public String getNomePresente() {
            return nomePresente;
        }

        public boolean isPorProcuracao() {
            return porProcuracao;
        }

        public String getNomeProcurador() {
            return nomeProcurador;
        }

        public Instant getRegistradoEm() {
            return registradoEm;
        }
    }

    @Entity
    @Table(name = "assembly_vote")
    public static class Voto {

        public enum Escolha {
            YES, NO, ABSTAIN
        }

        @Id
        @Column(name = "id")
        private UUID id = UUID.randomUUID();

        @Column(name = "assembly_id", nullable = false, updatable = false)
        private UUID idAssembleia;

        @Column(name = "agenda_item_id", nullable = false, updatable = false)
        private UUID idItemPauta;

        @Column(name = "unit_id", nullable = false, updatable = false)
        private UUID idUnidade;

        @Enumerated(EnumType.STRING)
        @Column(name = "choice", nullable = false, updatable = false)
        private Escolha escolha;

        @Column(name = "weight", nullable = false, updatable = false)
        private BigDecimal peso;

        @Column(name = "registered_at", nullable = false, updatable = false)
        private Instant registradoEm;

        @Column(name = "registered_by", updatable = false)
        private UUID registradoPor;

        protected Voto() {
        }

        public Voto(UUID idAssembleia, UUID idItemPauta, UUID idUnidade, Escolha escolha, BigDecimal peso, UUID registradoPor) {
            this.idAssembleia = idAssembleia;
            this.idItemPauta = idItemPauta;
            this.idUnidade = idUnidade;
            this.escolha = escolha;
            this.peso = peso;
            this.registradoPor = registradoPor;
        }

        @PrePersist
        void aoCriar() {
            registradoEm = Instant.now();
        }

        public UUID getIdItemPauta() {
            return idItemPauta;
        }

        public UUID getIdUnidade() {
            return idUnidade;
        }

        public Escolha getEscolha() {
            return escolha;
        }

        public BigDecimal getPeso() {
            return peso;
        }
    }
}

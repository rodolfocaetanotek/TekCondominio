package br.com.tekcondo.condominio.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "condominium")
public class Condominio extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID idAdministradora;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "legal_name")
    private String razaoSocial;

    @Column(name = "cnpj")
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TipoCondominio tipo;

    @Embedded
    private Endereco endereco;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SituacaoCondominio situacao = SituacaoCondominio.ACTIVE;

    @Column(name = "inactivated_at")
    private Instant inativadoEm;

    @Column(name = "inactivation_reason")
    private String motivoInativacao;

    @Column(name = "fine_percent", nullable = false)
    private BigDecimal percentualMulta = new BigDecimal("2.00");

    @Column(name = "interest_monthly_percent", nullable = false)
    private BigDecimal percentualJurosMensal = new BigDecimal("1.00");

    @Column(name = "billing_due_day", nullable = false)
    private int diaVencimento = 10;

    @Column(name = "require_payable_approval", nullable = false)
    private boolean exigeAprovacaoContasPagar = true;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "updated_by")
    private UUID atualizadoPor;

    protected Condominio() {
    }

    public Condominio(UUID idAdministradora, UUID criadoPor) {
        this.idAdministradora = idAdministradora;
        this.criadoPor = criadoPor;
        this.atualizadoPor = criadoPor;
    }

    /** Atualiza os dados cadastrais. Condomínio inativo deve ser reativado antes de ser editado. */
    public void atualizarDados(String nome, String razaoSocial, String cnpj, TipoCondominio tipo, Endereco endereco,
                               String email, String telefone, UUID atualizadoPor) {
        if (situacao == SituacaoCondominio.INACTIVE && getVersao() != null) {
            throw ApiException.regraNegocio("CONDOMINIUM_INACTIVE",
                    "Condomínio inativo não pode ser editado. Reative-o antes de alterar os dados.");
        }
        this.nome = nome;
        this.razaoSocial = razaoSocial;
        this.cnpj = cnpj;
        this.tipo = tipo;
        this.endereco = endereco;
        this.email = email;
        this.telefone = telefone;
        this.atualizadoPor = atualizadoPor;
    }

    public void inativar(String motivo, Instant agora, UUID por) {
        if (situacao == SituacaoCondominio.INACTIVE) {
            throw ApiException.regraNegocio("CONDOMINIUM_ALREADY_INACTIVE", "O condomínio já está inativo.");
        }
        situacao = SituacaoCondominio.INACTIVE;
        inativadoEm = agora;
        motivoInativacao = motivo;
        atualizadoPor = por;
    }

    public void ativar(UUID por) {
        if (situacao == SituacaoCondominio.ACTIVE) {
            throw ApiException.regraNegocio("CONDOMINIUM_ALREADY_ACTIVE", "O condomínio já está ativo.");
        }
        situacao = SituacaoCondominio.ACTIVE;
        inativadoEm = null;
        motivoInativacao = null;
        atualizadoPor = por;
    }

    /** Parâmetros de cobrança e de contas a pagar (premissas P01/P02, configuráveis por condomínio). */
    public void atualizarParametrosFinanceiros(BigDecimal percentualMulta, BigDecimal percentualJurosMensal,
                                               int diaVencimento, boolean exigeAprovacaoContasPagar, UUID por) {
        if (percentualMulta.signum() < 0 || percentualMulta.compareTo(new BigDecimal("2")) > 0) {
            throw ApiException.regraNegocio("FINE_LIMIT",
                    "A multa por atraso da cota condominial é limitada a 2% (Código Civil, art. 1.336, §1º).");
        }
        this.percentualMulta = percentualMulta;
        this.percentualJurosMensal = percentualJurosMensal;
        this.diaVencimento = diaVencimento;
        this.exigeAprovacaoContasPagar = exigeAprovacaoContasPagar;
        this.atualizadoPor = por;
    }

    public BigDecimal getPercentualMulta() {
        return percentualMulta;
    }

    public BigDecimal getPercentualJurosMensal() {
        return percentualJurosMensal;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public boolean isExigeAprovacaoContasPagar() {
        return exigeAprovacaoContasPagar;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public String getNome() {
        return nome;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getCnpj() {
        return cnpj;
    }

    public TipoCondominio getTipo() {
        return tipo;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public SituacaoCondominio getSituacao() {
        return situacao;
    }

    public Instant getInativadoEm() {
        return inativadoEm;
    }

    public String getMotivoInativacao() {
        return motivoInativacao;
    }

    public UUID getCriadoPor() {
        return criadoPor;
    }

    public UUID getAtualizadoPor() {
        return atualizadoPor;
    }
}

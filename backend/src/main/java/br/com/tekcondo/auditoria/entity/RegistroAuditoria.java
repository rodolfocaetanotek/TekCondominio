package br.com.tekcondo.auditoria.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro imutável da trilha de auditoria. */
@Entity
@Immutable
@Table(name = "audit_log")
public class RegistroAuditoria {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id")
    private UUID idAdministradora;

    @Column(name = "user_id")
    private UUID idUsuario;

    @Column(name = "user_email")
    private String emailUsuario;

    @Column(name = "action", nullable = false)
    private String acao;

    @Column(name = "entity_type")
    private String tipoEntidade;

    @Column(name = "entity_id")
    private String idEntidade;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> detalhes;

    @Column(name = "ip_address")
    private String enderecoIp;

    @Column(name = "occurred_at", nullable = false)
    private Instant ocorridoEm;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(UUID idAdministradora, UUID idUsuario, String emailUsuario, String acao, String tipoEntidade,
                    String idEntidade, Map<String, Object> detalhes, String enderecoIp, Instant ocorridoEm) {
        this.id = UUID.randomUUID();
        this.idAdministradora = idAdministradora;
        this.idUsuario = idUsuario;
        this.emailUsuario = emailUsuario;
        this.acao = acao;
        this.tipoEntidade = tipoEntidade;
        this.idEntidade = idEntidade;
        this.detalhes = detalhes;
        this.enderecoIp = enderecoIp;
        this.ocorridoEm = ocorridoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public UUID getIdUsuario() {
        return idUsuario;
    }

    public String getEmailUsuario() {
        return emailUsuario;
    }

    public String getAcao() {
        return acao;
    }

    public String getTipoEntidade() {
        return tipoEntidade;
    }

    public String getIdEntidade() {
        return idEntidade;
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }

    public String getEnderecoIp() {
        return enderecoIp;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}

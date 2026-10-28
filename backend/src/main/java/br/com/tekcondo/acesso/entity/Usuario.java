package br.com.tekcondo.acesso.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.comum.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class Usuario extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID idAdministradora;

    @Column(name = "name", nullable = false)
    private String nome;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String hashSenha;

    @Column(name = "platform_admin", nullable = false)
    private boolean administradorPlataforma;

    @Column(name = "active", nullable = false)
    private boolean ativo = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int tentativasLoginFalhas;

    @Column(name = "locked_until")
    private Instant bloqueadoAte;

    @Column(name = "last_login_at")
    private Instant ultimoLoginEm;

    /** Cadastro de pessoa vinculado (morador/proprietário com acesso ao portal). */
    @Column(name = "person_id")
    private UUID idPessoa;

    protected Usuario() {
    }

    private Usuario(UUID idAdministradora, String nome, String email, String hashSenha, boolean administradorPlataforma) {
        this.idAdministradora = idAdministradora;
        this.nome = nome.trim();
        this.email = email.trim().toLowerCase();
        this.hashSenha = hashSenha;
        this.administradorPlataforma = administradorPlataforma;
    }

    public static Usuario membroAdministradora(UUID idAdministradora, String nome, String email, String hashSenha) {
        return new Usuario(idAdministradora, nome, email, hashSenha, false);
    }

    public static Usuario administradorPlataforma(String nome, String email, String hashSenha) {
        return new Usuario(null, nome, email, hashSenha, true);
    }

    public boolean isBloqueado(Instant agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    /** Registra falha de login; ao atingir o limite, bloqueia temporariamente. Retorna true se bloqueou. */
    public boolean registrarFalhaLogin(int maxTentativas, Duration bloqueio, Instant agora) {
        tentativasLoginFalhas++;
        if (tentativasLoginFalhas >= maxTentativas) {
            bloqueadoAte = agora.plus(bloqueio);
            tentativasLoginFalhas = 0;
            return true;
        }
        return false;
    }

    public void registrarLoginSucesso(Instant agora) {
        tentativasLoginFalhas = 0;
        bloqueadoAte = null;
        ultimoLoginEm = agora;
    }

    /** Nova senha (já em hash). Também remove bloqueio por tentativas, pois a identidade foi comprovada. */
    public void alterarSenha(String novoHashSenha) {
        this.hashSenha = novoHashSenha;
        this.tentativasLoginFalhas = 0;
        this.bloqueadoAte = null;
    }

    public void renomear(String novoNome) {
        this.nome = novoNome.trim();
    }

    public void desativar() {
        this.ativo = false;
    }

    public void ativar() {
        this.ativo = true;
    }

    /** Convite pendente: usuário ainda não definiu senha nem entrou no sistema. */
    public boolean nuncaAcessou() {
        return ultimoLoginEm == null;
    }

    public void vincularPessoa(UUID idPessoa) {
        this.idPessoa = idPessoa;
    }

    public UUID getIdPessoa() {
        return idPessoa;
    }

    public UUID getIdAdministradora() {
        return idAdministradora;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getHashSenha() {
        return hashSenha;
    }

    public boolean isAdministradorPlataforma() {
        return administradorPlataforma;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getTentativasLoginFalhas() {
        return tentativasLoginFalhas;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    public Instant getUltimoLoginEm() {
        return ultimoLoginEm;
    }
}

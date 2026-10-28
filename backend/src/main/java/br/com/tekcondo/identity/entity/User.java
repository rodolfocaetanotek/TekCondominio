package br.com.tekcondo.identity.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import br.com.tekcondo.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Cadastro de pessoa vinculado (morador/proprietário com acesso ao portal). */
    @Column(name = "person_id")
    private UUID personId;

    protected User() {
    }

    private User(UUID organizationId, String name, String email, String passwordHash, boolean platformAdmin) {
        this.organizationId = organizationId;
        this.name = name.trim();
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.platformAdmin = platformAdmin;
    }

    public static User organizationMember(UUID organizationId, String name, String email, String passwordHash) {
        return new User(organizationId, name, email, passwordHash, false);
    }

    public static User platformAdmin(String name, String email, String passwordHash) {
        return new User(null, name, email, passwordHash, true);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Registra falha de login; ao atingir o limite, bloqueia temporariamente. Retorna true se bloqueou. */
    public boolean registerFailedLogin(int maxAttempts, Duration lockout, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Nova senha (já em hash). Também remove bloqueio por tentativas, pois a identidade foi comprovada. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void rename(String newName) {
        this.name = newName.trim();
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    /** Convite pendente: usuário ainda não definiu senha nem entrou no sistema. */
    public boolean hasNeverSignedIn() {
        return lastLoginAt == null;
    }

    public void linkPerson(UUID personId) {
        this.personId = personId;
    }

    public UUID getPersonId() {
        return personId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}

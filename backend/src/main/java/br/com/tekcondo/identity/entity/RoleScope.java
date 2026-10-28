package br.com.tekcondo.identity.entity;

/** Onde um papel pode ser atribuído. */
public enum RoleScope {
    /** Toda a administradora (ex.: administrador, funcionário da administradora). */
    ORGANIZATION,
    /** Um condomínio específico (ex.: síndico, conselheiro, morador). */
    CONDOMINIUM
}

package br.com.tekcondo.acesso.entity;

/** Onde um papel pode ser atribuído. */
public enum EscopoPapel {
    /** Toda a administradora (ex.: administrador, funcionário da administradora). */
    ORGANIZATION,
    /** Um condomínio específico (ex.: síndico, conselheiro, morador). */
    CONDOMINIUM
}

package br.com.tekcondo.identity.access;

/**
 * Catálogo de permissões (módulo + ação). Deve estar em sincronia com a tabela {@code permission}
 * — verificado pelo teste {@code PermissionCatalogIT}.
 */
public enum Permission {
    CONDOMINIUM_VIEW,
    CONDOMINIUM_CREATE,
    CONDOMINIUM_EDIT,
    CONDOMINIUM_INACTIVATE,
    USER_VIEW,
    USER_MANAGE,
    AUDIT_VIEW,
    STRUCTURE_VIEW,
    STRUCTURE_EDIT,
    PEOPLE_VIEW,
    PEOPLE_EDIT,
    FINANCE_VIEW,
    FINANCE_EDIT,
    FINANCE_APPROVE,
    FINANCE_CANCEL,
    FINANCE_CLOSE,
    BILLING_VIEW,
    BILLING_EDIT,
    REPORT_VIEW,
    REPORT_EXPORT,
    SUPPLIER_VIEW,
    SUPPLIER_EDIT,
    OCCURRENCE_VIEW,
    OCCURRENCE_EDIT,
    ANNOUNCEMENT_VIEW,
    ANNOUNCEMENT_EDIT,
    ASSEMBLY_VIEW,
    ASSEMBLY_EDIT,
    DOCUMENT_VIEW,
    DOCUMENT_EDIT,
    RESERVATION_VIEW,
    RESERVATION_EDIT,
    ACCESS_VIEW,
    ACCESS_EDIT,
    PORTAL_ACCESS
}

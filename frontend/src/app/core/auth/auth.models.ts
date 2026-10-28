export type Permission =
  | 'CONDOMINIUM_VIEW'
  | 'CONDOMINIUM_CREATE'
  | 'CONDOMINIUM_EDIT'
  | 'CONDOMINIUM_INACTIVATE'
  | 'USER_VIEW'
  | 'USER_MANAGE'
  | 'AUDIT_VIEW'
  | 'STRUCTURE_VIEW'
  | 'STRUCTURE_EDIT'
  | 'PEOPLE_VIEW'
  | 'PEOPLE_EDIT'
  | 'FINANCE_VIEW'
  | 'FINANCE_EDIT'
  | 'FINANCE_APPROVE'
  | 'FINANCE_CANCEL'
  | 'BILLING_VIEW'
  | 'BILLING_EDIT'
  | 'REPORT_VIEW'
  | 'REPORT_EXPORT'
  | 'SUPPLIER_VIEW'
  | 'SUPPLIER_EDIT'
  | 'OCCURRENCE_VIEW'
  | 'OCCURRENCE_EDIT'
  | 'ANNOUNCEMENT_VIEW'
  | 'ANNOUNCEMENT_EDIT'
  | 'ASSEMBLY_VIEW'
  | 'ASSEMBLY_EDIT'
  | 'DOCUMENT_VIEW'
  | 'DOCUMENT_EDIT'
  | 'PORTAL_ACCESS';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

/** Perfil do usuário. As permissões orientam apenas a interface — o backend sempre revalida. */
export interface Me {
  id: string;
  name: string;
  email: string;
  platformAdmin: boolean;
  organizationId?: string;
  organizationName?: string;
  organizationPermissions: Permission[];
  condominiumPermissions: Record<string, Permission[]>;
}

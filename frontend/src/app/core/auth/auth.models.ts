export type Permissao =
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
  | 'FINANCE_CLOSE'
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
  | 'RESERVATION_VIEW'
  | 'RESERVATION_EDIT'
  | 'ACCESS_VIEW'
  | 'ACCESS_EDIT'
  | 'PORTAL_ACCESS';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiraEm: string;
}

/** Perfil do usuário. As permissões orientam apenas a interface — o backend sempre revalida. */
export interface Perfil {
  id: string;
  nome: string;
  email: string;
  administradorPlataforma: boolean;
  idAdministradora?: string;
  nomeAdministradora?: string;
  permissoesAdministradora: Permissao[];
  permissoesCondominio: Record<string, Permissao[]>;
}

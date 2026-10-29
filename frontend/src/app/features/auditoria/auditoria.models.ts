export interface RegistroAuditoria {
  id: string;
  ocorridoEm: string;
  idUsuario?: string;
  emailUsuario?: string;
  acao: string;
  tipoEntidade?: string;
  idEntidade?: string;
  detalhes?: Record<string, unknown>;
  enderecoIp?: string;
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Login realizado',
  LOGIN_FAILED: 'Falha de login',
  LOGIN_BLOCKED: 'Login bloqueado',
  ACCOUNT_LOCKED: 'Conta bloqueada',
  LOGOUT: 'Logout',
  REFRESH_TOKEN_REUSE: 'Reuso de sessão revogada',
  ORGANIZATION_CREATED: 'Administradora criada',
  USER_CREATED: 'Usuário criado',
  USER_UPDATED: 'Usuário alterado',
  USER_DEACTIVATED: 'Usuário desativado',
  USER_ACTIVATED: 'Usuário reativado',
  ROLE_ASSIGNED: 'Papel atribuído',
  ROLE_REVOKED: 'Papel removido',
  INVITE_SENT: 'Convite enviado',
  INVITE_ACCEPTED: 'Convite aceito',
  PASSWORD_RESET_REQUESTED: 'Redefinição de senha solicitada',
  PASSWORD_RESET_COMPLETED: 'Senha redefinida',
  PASSWORD_CHANGED: 'Senha alterada',
  CONDOMINIUM_CREATED: 'Condomínio cadastrado',
  CONDOMINIUM_UPDATED: 'Condomínio alterado',
  CONDOMINIUM_INACTIVATED: 'Condomínio inativado',
  CONDOMINIUM_ACTIVATED: 'Condomínio reativado',
};

export const AUDIT_ENTITY_LABELS: Record<string, string> = {
  User: 'Usuário',
  Organization: 'Administradora',
  Condominium: 'Condomínio',
};

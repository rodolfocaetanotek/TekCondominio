import { Permission } from '../../core/auth/auth.models';

export type RoleScope = 'ORGANIZATION' | 'CONDOMINIUM';

export interface RoleOption {
  code: string;
  name: string;
  description?: string;
  scope: RoleScope;
  permissions: Permission[];
}

export interface Assignment {
  id: string;
  roleCode: string;
  roleName: string;
  scope: RoleScope;
  condominiumId?: string;
  condominiumName?: string;
}

export interface UserSummary {
  id: string;
  name: string;
  email: string;
  active: boolean;
  pendingInvite: boolean;
  lastLoginAt?: string;
  roles: string[];
}

export interface UserDetail {
  id: string;
  name: string;
  email: string;
  active: boolean;
  pendingInvite: boolean;
  lastLoginAt?: string;
  createdAt: string;
  version: number;
  assignments: Assignment[];
}

export interface AssignmentRequest {
  roleCode: string;
  condominiumId: string | null;
}

export interface UserQuery {
  search?: string;
  active?: '' | 'true' | 'false';
  page: number;
  size: number;
}

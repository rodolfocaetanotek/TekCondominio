import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permissao } from './auth.models';
import { AuthService } from './auth.service';

/** Exige sessão ativa; caso contrário, redireciona ao login preservando o destino. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { retorno: state.url } });
};

/** Bloqueia a tela de login para quem já está autenticado. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;
};

export const platformAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user()?.administradorPlataforma ? true : inject(Router).createUrlTree(['/']);
};

/** Exige a permissão em ao menos um condomínio. A autorização definitiva é sempre do backend. */
export function permissionGuard(permission: Permissao): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.canAny(permission) ? true : inject(Router).createUrlTree(['/']);
  };
}

/** Página inicial adequada ao perfil: administrador da plataforma vai às organizações. */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const me = auth.user();
  if (me?.administradorPlataforma && !auth.isOrganizationMember()) {
    return inject(Router).createUrlTree(['/organizacoes']);
  }
  const residentOnly = !!me && me.permissoesAdministradora.length === 0
    && Object.values(me.permissoesCondominio).length > 0
    && Object.values(me.permissoesCondominio).every((p) => p.length === 1 && p[0] === 'PORTAL_ACCESS');
  return residentOnly ? inject(Router).createUrlTree(['/portal']) : true;
};

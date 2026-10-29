import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Permissao } from './auth.models';
import { AuthService } from './auth.service';

/** Exige sessão ativa; caso contrário, redireciona ao login preservando o destino. */
export const autenticadoGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  return auth.autenticado()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { retorno: estado.url } });
};

/** Bloqueia a tela de login para quem já está autenticado. */
export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.autenticado() ? inject(Router).createUrlTree(['/']) : true;
};

export const administradorPlataformaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.usuario()?.administradorPlataforma ? true : inject(Router).createUrlTree(['/']);
};

/** Exige a permissão em ao menos um condomínio. A autorização definitiva é sempre do backend. */
export function permissaoGuard(permissao: Permissao): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.podeAlgum(permissao) ? true : inject(Router).createUrlTree(['/']);
  };
}

/** Página inicial adequada ao perfil: administrador da plataforma vai às organizações. */
export const redirecionamentoInicialGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const perfil = auth.usuario();
  if (perfil?.administradorPlataforma && !auth.membroAdministradora()) {
    return inject(Router).createUrlTree(['/organizacoes']);
  }
  const apenasMorador = !!perfil && perfil.permissoesAdministradora.length === 0
    && Object.values(perfil.permissoesCondominio).length > 0
    && Object.values(perfil.permissoesCondominio).every((p) => p.length === 1 && p[0] === 'PORTAL_ACCESS');
  return apenasMorador ? inject(Router).createUrlTree(['/portal']) : true;
};

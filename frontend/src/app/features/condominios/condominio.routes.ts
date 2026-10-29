import { Routes } from '@angular/router';
import { permissaoGuard } from '../../core/auth/auth.guards';

export const ROTAS_CONDOMINIO: Routes = [
  {
    path: '',
    title: 'Condomínios · Tek Condomínio',
    loadComponent: () => import('./condominio-lista').then((m) => m.CondominioLista),
  },
  {
    path: 'novo',
    title: 'Novo condomínio · Tek Condomínio',
    canActivate: [permissaoGuard('CONDOMINIUM_CREATE')],
    loadComponent: () => import('./condominio-form').then((m) => m.CondominioForm),
  },
  {
    path: ':id',
    title: 'Condomínio · Tek Condomínio',
    loadComponent: () => import('./condominio-form').then((m) => m.CondominioForm),
  },
];

import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guards';

export const CONDOMINIUM_ROUTES: Routes = [
  {
    path: '',
    title: 'Condomínios · Tek Condomínio',
    loadComponent: () => import('./condominium-list').then((m) => m.CondominiumList),
  },
  {
    path: 'novo',
    title: 'Novo condomínio · Tek Condomínio',
    canActivate: [permissionGuard('CONDOMINIUM_CREATE')],
    loadComponent: () => import('./condominium-form').then((m) => m.CondominiumForm),
  },
  {
    path: ':id',
    title: 'Condomínio · Tek Condomínio',
    loadComponent: () => import('./condominium-form').then((m) => m.CondominiumForm),
  },
];

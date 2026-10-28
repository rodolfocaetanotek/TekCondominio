import { Routes } from '@angular/router';
import { authGuard, guestGuard, homeRedirectGuard, permissionGuard, platformAdminGuard } from './core/auth/auth.guards';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Entrar · Tek Condomínio',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'esqueci-senha',
    canActivate: [guestGuard],
    title: 'Esqueci minha senha · Tek Condomínio',
    loadComponent: () => import('./features/auth/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'definir-senha',
    title: 'Definir senha · Tek Condomínio',
    loadComponent: () => import('./features/auth/set-password').then((m) => m.SetPassword),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [homeRedirectGuard],
        title: 'Início · Tek Condomínio',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'condominios',
        canActivate: [permissionGuard('CONDOMINIUM_VIEW')],
        loadChildren: () => import('./features/condominiums/condominium.routes').then((m) => m.CONDOMINIUM_ROUTES),
      },
      {
        path: 'unidades',
        canActivate: [permissionGuard('STRUCTURE_VIEW')],
        title: 'Unidades · Tek Condomínio',
        loadComponent: () => import('./features/structure/unit-list').then((m) => m.UnitList),
      },
      {
        path: 'unidades/:id',
        canActivate: [permissionGuard('STRUCTURE_VIEW')],
        title: 'Unidade · Tek Condomínio',
        loadComponent: () => import('./features/structure/unit-detail').then((m) => m.UnitDetail),
      },
      {
        path: 'pessoas',
        canActivate: [permissionGuard('PEOPLE_VIEW')],
        title: 'Pessoas · Tek Condomínio',
        loadComponent: () => import('./features/structure/person-list').then((m) => m.PersonList),
      },
      {
        path: 'pessoas/:id',
        canActivate: [permissionGuard('PEOPLE_VIEW')],
        title: 'Pessoa · Tek Condomínio',
        loadComponent: () => import('./features/structure/person-detail').then((m) => m.PersonDetail),
      },
      {
        path: 'financeiro',
        canActivate: [permissionGuard('FINANCE_VIEW')],
        title: 'Financeiro · Tek Condomínio',
        loadComponent: () => import('./features/finance/finance-page').then((m) => m.FinancePage),
      },
      {
        path: 'financeiro/lancamentos/:id',
        canActivate: [permissionGuard('FINANCE_VIEW')],
        title: 'Lançamento · Tek Condomínio',
        loadComponent: () => import('./features/finance/entry-detail').then((m) => m.EntryDetailPage),
      },
      {
        path: 'cobranca',
        canActivate: [permissionGuard('BILLING_VIEW')],
        title: 'Cobrança · Tek Condomínio',
        loadComponent: () => import('./features/billing/billing-page').then((m) => m.BillingPage),
      },
      {
        path: 'cobranca/unidades/:unitId',
        canActivate: [permissionGuard('BILLING_VIEW')],
        title: 'Débitos da unidade · Tek Condomínio',
        loadComponent: () => import('./features/billing/unit-debt').then((m) => m.UnitDebtPage),
      },
      {
        path: 'fornecedores',
        canActivate: [permissionGuard('SUPPLIER_VIEW')],
        title: 'Fornecedores · Tek Condomínio',
        loadComponent: () => import('./features/finance/supplier-list').then((m) => m.SupplierList),
      },
      {
        path: 'contratos',
        canActivate: [permissionGuard('SUPPLIER_VIEW')],
        title: 'Contratos · Tek Condomínio',
        loadComponent: () => import('./features/ops/contract-list').then((m) => m.ContractList),
      },
      {
        path: 'ocorrencias',
        canActivate: [permissionGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrências · Tek Condomínio',
        loadComponent: () => import('./features/ops/occurrence-list').then((m) => m.OccurrenceList),
      },
      {
        path: 'ocorrencias/:id',
        canActivate: [permissionGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrência · Tek Condomínio',
        loadComponent: () => import('./features/ops/occurrence-detail').then((m) => m.OccurrenceDetailPage),
      },
      {
        path: 'comunicados',
        canActivate: [permissionGuard('ANNOUNCEMENT_VIEW')],
        title: 'Comunicados · Tek Condomínio',
        loadComponent: () => import('./features/ops/announcement-list').then((m) => m.AnnouncementList),
      },
      {
        path: 'assembleias',
        canActivate: [permissionGuard('ASSEMBLY_VIEW')],
        title: 'Assembleias · Tek Condomínio',
        loadComponent: () => import('./features/ops/assembly-list').then((m) => m.AssemblyList),
      },
      {
        path: 'assembleias/:id',
        canActivate: [permissionGuard('ASSEMBLY_VIEW')],
        title: 'Assembleia · Tek Condomínio',
        loadComponent: () => import('./features/ops/assembly-detail').then((m) => m.AssemblyDetailPage),
      },
      {
        path: 'documentos',
        canActivate: [permissionGuard('DOCUMENT_VIEW')],
        title: 'Documentos · Tek Condomínio',
        loadComponent: () => import('./features/ops/document-list').then((m) => m.DocumentList),
      },
      {
        path: 'relatorios',
        canActivate: [permissionGuard('REPORT_VIEW')],
        title: 'Relatórios · Tek Condomínio',
        loadComponent: () => import('./features/ops/reports-page').then((m) => m.ReportsPage),
      },
      {
        path: 'portal',
        canActivate: [permissionGuard('PORTAL_ACCESS')],
        title: 'Portal do morador · Tek Condomínio',
        loadComponent: () => import('./features/portal/portal-page').then((m) => m.PortalPage),
      },
      {
        path: 'usuarios',
        canActivate: [permissionGuard('USER_VIEW')],
        title: 'Usuários · Tek Condomínio',
        loadComponent: () => import('./features/users/user-list').then((m) => m.UserList),
      },
      {
        path: 'usuarios/:id',
        canActivate: [permissionGuard('USER_VIEW')],
        title: 'Usuário · Tek Condomínio',
        loadComponent: () => import('./features/users/user-detail').then((m) => m.UserDetail),
      },
      {
        path: 'auditoria',
        canActivate: [permissionGuard('AUDIT_VIEW')],
        title: 'Auditoria · Tek Condomínio',
        loadComponent: () => import('./features/audit/audit-list').then((m) => m.AuditList),
      },
      {
        path: 'organizacoes',
        canActivate: [platformAdminGuard],
        title: 'Administradoras · Tek Condomínio',
        loadComponent: () => import('./features/organizations/organization-list').then((m) => m.OrganizationList),
      },
      {
        path: 'organizacoes/:id',
        canActivate: [platformAdminGuard],
        title: 'Administradora · Tek Condomínio',
        loadComponent: () => import('./features/organizations/organization-detail').then((m) => m.OrganizationDetail),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

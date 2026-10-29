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
    loadComponent: () => import('./features/auth/esqueci-senha').then((m) => m.ForgotPassword),
  },
  {
    path: 'definir-senha',
    title: 'Definir senha · Tek Condomínio',
    loadComponent: () => import('./features/auth/definir-senha').then((m) => m.SetPassword),
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
        loadComponent: () => import('./features/painel/painel').then((m) => m.Home),
      },
      {
        path: 'condominios',
        canActivate: [permissionGuard('CONDOMINIUM_VIEW')],
        loadChildren: () => import('./features/condominios/condominio.routes').then((m) => m.CONDOMINIUM_ROUTES),
      },
      {
        path: 'unidades',
        canActivate: [permissionGuard('STRUCTURE_VIEW')],
        title: 'Unidades · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/unidade-lista').then((m) => m.UnitList),
      },
      {
        path: 'unidades/:id',
        canActivate: [permissionGuard('STRUCTURE_VIEW')],
        title: 'Unidade · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/unidade-detalhe').then((m) => m.UnitDetail),
      },
      {
        path: 'pessoas',
        canActivate: [permissionGuard('PEOPLE_VIEW')],
        title: 'Pessoas · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/pessoa-lista').then((m) => m.PersonList),
      },
      {
        path: 'pessoas/:id',
        canActivate: [permissionGuard('PEOPLE_VIEW')],
        title: 'Pessoa · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/pessoa-detalhe').then((m) => m.PersonDetail),
      },
      {
        path: 'financeiro',
        canActivate: [permissionGuard('FINANCE_VIEW')],
        title: 'Financeiro · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/financeiro-page').then((m) => m.FinancePage),
      },
      {
        path: 'financeiro/lancamentos/:id',
        canActivate: [permissionGuard('FINANCE_VIEW')],
        title: 'Lançamento · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/lancamento-detalhe').then((m) => m.EntryDetailPage),
      },
      {
        path: 'cobranca',
        canActivate: [permissionGuard('BILLING_VIEW')],
        title: 'Cobrança · Tek Condomínio',
        loadComponent: () => import('./features/cobranca/cobranca-page').then((m) => m.BillingPage),
      },
      {
        path: 'cobranca/unidades/:unitId',
        canActivate: [permissionGuard('BILLING_VIEW')],
        title: 'Débitos da unidade · Tek Condomínio',
        loadComponent: () => import('./features/cobranca/debitos-unidade').then((m) => m.UnitDebtPage),
      },
      {
        path: 'fornecedores',
        canActivate: [permissionGuard('SUPPLIER_VIEW')],
        title: 'Fornecedores · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/fornecedor-lista').then((m) => m.SupplierList),
      },
      {
        path: 'contratos',
        canActivate: [permissionGuard('SUPPLIER_VIEW')],
        title: 'Contratos · Tek Condomínio',
        loadComponent: () => import('./features/operacao/contrato-lista').then((m) => m.ContractList),
      },
      {
        path: 'ocorrencias',
        canActivate: [permissionGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrências · Tek Condomínio',
        loadComponent: () => import('./features/operacao/ocorrencia-lista').then((m) => m.OccurrenceList),
      },
      {
        path: 'ocorrencias/:id',
        canActivate: [permissionGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrência · Tek Condomínio',
        loadComponent: () => import('./features/operacao/ocorrencia-detalhe').then((m) => m.OccurrenceDetailPage),
      },
      {
        path: 'comunicados',
        canActivate: [permissionGuard('ANNOUNCEMENT_VIEW')],
        title: 'Comunicados · Tek Condomínio',
        loadComponent: () => import('./features/operacao/comunicado-lista').then((m) => m.AnnouncementList),
      },
      {
        path: 'assembleias',
        canActivate: [permissionGuard('ASSEMBLY_VIEW')],
        title: 'Assembleias · Tek Condomínio',
        loadComponent: () => import('./features/operacao/assembleia-lista').then((m) => m.AssemblyList),
      },
      {
        path: 'assembleias/:id',
        canActivate: [permissionGuard('ASSEMBLY_VIEW')],
        title: 'Assembleia · Tek Condomínio',
        loadComponent: () => import('./features/operacao/assembleia-detalhe').then((m) => m.AssemblyDetailPage),
      },
      {
        path: 'reservas',
        canActivate: [permissionGuard('RESERVATION_VIEW')],
        title: 'Reservas · Tek Condomínio',
        loadComponent: () => import('./features/reservas/reserva-page').then((m) => m.ReservationPage),
      },
      {
        path: 'portaria',
        canActivate: [permissionGuard('ACCESS_VIEW')],
        title: 'Portaria · Tek Condomínio',
        loadComponent: () => import('./features/portaria/portaria-page').then((m) => m.GatePage),
      },
      {
        path: 'documentos',
        canActivate: [permissionGuard('DOCUMENT_VIEW')],
        title: 'Documentos · Tek Condomínio',
        loadComponent: () => import('./features/operacao/documento-lista').then((m) => m.DocumentList),
      },
      {
        path: 'relatorios',
        canActivate: [permissionGuard('REPORT_VIEW')],
        title: 'Relatórios · Tek Condomínio',
        loadComponent: () => import('./features/operacao/relatorios-page').then((m) => m.ReportsPage),
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
        loadComponent: () => import('./features/usuarios/usuario-lista').then((m) => m.UserList),
      },
      {
        path: 'usuarios/:id',
        canActivate: [permissionGuard('USER_VIEW')],
        title: 'Usuário · Tek Condomínio',
        loadComponent: () => import('./features/usuarios/usuario-detalhe').then((m) => m.UserDetail),
      },
      {
        path: 'auditoria',
        canActivate: [permissionGuard('AUDIT_VIEW')],
        title: 'Auditoria · Tek Condomínio',
        loadComponent: () => import('./features/auditoria/auditoria-lista').then((m) => m.AuditList),
      },
      {
        path: 'organizacoes',
        canActivate: [platformAdminGuard],
        title: 'Administradoras · Tek Condomínio',
        loadComponent: () => import('./features/administradoras/administradora-lista').then((m) => m.OrganizationList),
      },
      {
        path: 'organizacoes/:id',
        canActivate: [platformAdminGuard],
        title: 'Administradora · Tek Condomínio',
        loadComponent: () => import('./features/administradoras/administradora-detalhe').then((m) => m.OrganizationDetail),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

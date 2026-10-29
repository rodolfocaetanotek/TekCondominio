import { Routes } from '@angular/router';
import { autenticadoGuard, visitanteGuard, redirecionamentoInicialGuard, permissaoGuard, administradorPlataformaGuard } from './core/auth/auth.guards';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [visitanteGuard],
    title: 'Entrar · Tek Condomínio',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'esqueci-senha',
    canActivate: [visitanteGuard],
    title: 'Esqueci minha senha · Tek Condomínio',
    loadComponent: () => import('./features/auth/esqueci-senha').then((m) => m.EsqueciSenha),
  },
  {
    path: 'definir-senha',
    title: 'Definir senha · Tek Condomínio',
    loadComponent: () => import('./features/auth/definir-senha').then((m) => m.DefinirSenha),
  },
  {
    path: '',
    component: Shell,
    canActivate: [autenticadoGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [redirecionamentoInicialGuard],
        title: 'Início · Tek Condomínio',
        loadComponent: () => import('./features/painel/painel').then((m) => m.PainelPage),
      },
      {
        path: 'condominios',
        canActivate: [permissaoGuard('CONDOMINIUM_VIEW')],
        loadChildren: () => import('./features/condominios/condominio.routes').then((m) => m.ROTAS_CONDOMINIO),
      },
      {
        path: 'unidades',
        canActivate: [permissaoGuard('STRUCTURE_VIEW')],
        title: 'Unidades · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/unidade-lista').then((m) => m.UnidadeLista),
      },
      {
        path: 'unidades/:id',
        canActivate: [permissaoGuard('STRUCTURE_VIEW')],
        title: 'Unidade · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/unidade-detalhe').then((m) => m.UnidadeDetalhe),
      },
      {
        path: 'pessoas',
        canActivate: [permissaoGuard('PEOPLE_VIEW')],
        title: 'Pessoas · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/pessoa-lista').then((m) => m.PessoaLista),
      },
      {
        path: 'pessoas/:id',
        canActivate: [permissaoGuard('PEOPLE_VIEW')],
        title: 'Pessoa · Tek Condomínio',
        loadComponent: () => import('./features/estrutura/pessoa-detalhe').then((m) => m.PessoaDetalhePage),
      },
      {
        path: 'financeiro',
        canActivate: [permissaoGuard('FINANCE_VIEW')],
        title: 'Financeiro · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/financeiro-page').then((m) => m.FinanceiroPage),
      },
      {
        path: 'financeiro/lancamentos/:id',
        canActivate: [permissaoGuard('FINANCE_VIEW')],
        title: 'Lançamento · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/lancamento-detalhe').then((m) => m.LancamentoDetalhePage),
      },
      {
        path: 'cobranca',
        canActivate: [permissaoGuard('BILLING_VIEW')],
        title: 'Cobrança · Tek Condomínio',
        loadComponent: () => import('./features/cobranca/cobranca-page').then((m) => m.CobrancaPage),
      },
      {
        path: 'cobranca/unidades/:idUnidade',
        canActivate: [permissaoGuard('BILLING_VIEW')],
        title: 'Débitos da unidade · Tek Condomínio',
        loadComponent: () => import('./features/cobranca/debitos-unidade').then((m) => m.DebitosUnidadePage),
      },
      {
        path: 'fornecedores',
        canActivate: [permissaoGuard('SUPPLIER_VIEW')],
        title: 'Fornecedores · Tek Condomínio',
        loadComponent: () => import('./features/financeiro/fornecedor-lista').then((m) => m.FornecedorLista),
      },
      {
        path: 'contratos',
        canActivate: [permissaoGuard('SUPPLIER_VIEW')],
        title: 'Contratos · Tek Condomínio',
        loadComponent: () => import('./features/operacao/contrato-lista').then((m) => m.ContratoLista),
      },
      {
        path: 'ocorrencias',
        canActivate: [permissaoGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrências · Tek Condomínio',
        loadComponent: () => import('./features/operacao/ocorrencia-lista').then((m) => m.OcorrenciaLista),
      },
      {
        path: 'ocorrencias/:id',
        canActivate: [permissaoGuard('OCCURRENCE_VIEW')],
        title: 'Ocorrência · Tek Condomínio',
        loadComponent: () => import('./features/operacao/ocorrencia-detalhe').then((m) => m.OcorrenciaDetalhePage),
      },
      {
        path: 'comunicados',
        canActivate: [permissaoGuard('ANNOUNCEMENT_VIEW')],
        title: 'Comunicados · Tek Condomínio',
        loadComponent: () => import('./features/operacao/comunicado-lista').then((m) => m.ComunicadoLista),
      },
      {
        path: 'assembleias',
        canActivate: [permissaoGuard('ASSEMBLY_VIEW')],
        title: 'Assembleias · Tek Condomínio',
        loadComponent: () => import('./features/operacao/assembleia-lista').then((m) => m.AssembleiaLista),
      },
      {
        path: 'assembleias/:id',
        canActivate: [permissaoGuard('ASSEMBLY_VIEW')],
        title: 'Assembleia · Tek Condomínio',
        loadComponent: () => import('./features/operacao/assembleia-detalhe').then((m) => m.AssembleiaDetalhePage),
      },
      {
        path: 'reservas',
        canActivate: [permissaoGuard('RESERVATION_VIEW')],
        title: 'Reservas · Tek Condomínio',
        loadComponent: () => import('./features/reservas/reserva-page').then((m) => m.ReservaPage),
      },
      {
        path: 'portaria',
        canActivate: [permissaoGuard('ACCESS_VIEW')],
        title: 'Portaria · Tek Condomínio',
        loadComponent: () => import('./features/portaria/portaria-page').then((m) => m.PortariaPage),
      },
      {
        path: 'documentos',
        canActivate: [permissaoGuard('DOCUMENT_VIEW')],
        title: 'Documentos · Tek Condomínio',
        loadComponent: () => import('./features/operacao/documento-lista').then((m) => m.DocumentoLista),
      },
      {
        path: 'relatorios',
        canActivate: [permissaoGuard('REPORT_VIEW')],
        title: 'Relatórios · Tek Condomínio',
        loadComponent: () => import('./features/operacao/relatorios-page').then((m) => m.RelatoriosPage),
      },
      {
        path: 'portal',
        canActivate: [permissaoGuard('PORTAL_ACCESS')],
        title: 'Portal do morador · Tek Condomínio',
        loadComponent: () => import('./features/portal/portal-page').then((m) => m.PortalPage),
      },
      {
        path: 'usuarios',
        canActivate: [permissaoGuard('USER_VIEW')],
        title: 'Usuários · Tek Condomínio',
        loadComponent: () => import('./features/usuarios/usuario-lista').then((m) => m.UsuarioLista),
      },
      {
        path: 'usuarios/:id',
        canActivate: [permissaoGuard('USER_VIEW')],
        title: 'Usuário · Tek Condomínio',
        loadComponent: () => import('./features/usuarios/usuario-detalhe').then((m) => m.UsuarioDetalhePage),
      },
      {
        path: 'auditoria',
        canActivate: [permissaoGuard('AUDIT_VIEW')],
        title: 'Auditoria · Tek Condomínio',
        loadComponent: () => import('./features/auditoria/auditoria-lista').then((m) => m.AuditoriaLista),
      },
      {
        path: 'organizacoes',
        canActivate: [administradorPlataformaGuard],
        title: 'Administradoras · Tek Condomínio',
        loadComponent: () => import('./features/administradoras/administradora-lista').then((m) => m.AdministradoraLista),
      },
      {
        path: 'organizacoes/:id',
        canActivate: [administradorPlataformaGuard],
        title: 'Administradora · Tek Condomínio',
        loadComponent: () => import('./features/administradoras/administradora-detalhe').then((m) => m.AdministradoraDetalhe),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

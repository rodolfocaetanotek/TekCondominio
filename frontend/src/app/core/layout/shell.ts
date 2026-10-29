import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AlterarSenhaDialog } from '../../features/auth/alterar-senha-dialog';
import { Permissao } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { CondominioContexto } from '../contexto/condominio-contexto';
import { SinoNotificacoes } from '../notificacao/sino-notificacoes';
import { NotificacaoService } from '../notificacao/notificacao.service';
import { TabelasResponsivas } from './tabelas-responsivas';
import { ModoTema, TemaService } from './tema.service';

interface ItemMenu {
  rotulo: string;
  icone: string;
  link: string;
  exato?: boolean;
  visivel: () => boolean;
}

interface GrupoMenu {
  titulo: string;
  icone: string;
  itens: ItemMenu[];
}

const CHAVE_ABERTOS = 'tc-nav-open';
const CHAVE_RECOLHIDO = 'tc-nav-collapsed';

@Component({
  selector: 'tc-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
    MatSelectModule,
    MatFormFieldModule,
    SinoNotificacoes,
    TabelasResponsivas,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly contexto = inject(CondominioContexto);
  protected readonly tema = inject(TemaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly router = inject(Router);

  protected readonly ehCelular = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  /** Menu recolhido (só ícones) — opção do desktop, guardada no navegador. */
  protected readonly recolhido = signal(lerIndicador(CHAVE_RECOLHIDO));
  protected readonly mini = computed(() => this.recolhido() && !this.ehCelular());

  /** Grupo aberto no menu (no máximo um). */
  protected readonly abertos = signal<ReadonlySet<string>>(new Set(lerLista(CHAVE_ABERTOS)));

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Permissão no condomínio selecionado. */
  private readonly noCondominio = (p: Permissao) => () => this.contexto.pode(p);

  private readonly grupos: GrupoMenu[] = [
    {
      titulo: '',
      icone: '',
      itens: [
        { rotulo: 'Início', icone: 'space_dashboard', link: '/', exato: true, visivel: () => this.auth.membroAdministradora() && !this.apenasMorador() },
        { rotulo: 'Portal do morador', icone: 'home', link: '/portal', visivel: () => this.contexto.podeAlgum('PORTAL_ACCESS') },
      ],
    },
    {
      titulo: 'Cadastros',
      icone: 'apartment',
      itens: [
        { rotulo: 'Condomínios', icone: 'apartment', link: '/condominios', visivel: () => this.auth.podeAlgum('CONDOMINIUM_VIEW') },
        { rotulo: 'Unidades', icone: 'door_front', link: '/unidades', visivel: this.noCondominio('STRUCTURE_VIEW') },
        { rotulo: 'Pessoas', icone: 'contacts', link: '/pessoas', visivel: () => this.contexto.podeAlgum('PEOPLE_VIEW') },
      ],
    },
    {
      titulo: 'Financeiro',
      icone: 'account_balance',
      itens: [
        { rotulo: 'Financeiro', icone: 'account_balance', link: '/financeiro', visivel: this.noCondominio('FINANCE_VIEW') },
        { rotulo: 'Cobrança e inadimplência', icone: 'request_quote', link: '/cobranca', visivel: this.noCondominio('BILLING_VIEW') },
        { rotulo: 'Fornecedores', icone: 'storefront', link: '/fornecedores', visivel: () => this.contexto.podeAlgum('SUPPLIER_VIEW') },
        { rotulo: 'Relatórios', icone: 'bar_chart', link: '/relatorios', visivel: this.noCondominio('REPORT_VIEW') },
      ],
    },
    {
      titulo: 'Administração',
      icone: 'home_work',
      itens: [
        { rotulo: 'Ocorrências', icone: 'build', link: '/ocorrencias', visivel: this.noCondominio('OCCURRENCE_VIEW') },
        { rotulo: 'Comunicados', icone: 'campaign', link: '/comunicados', visivel: this.noCondominio('ANNOUNCEMENT_VIEW') },
        { rotulo: 'Reservas', icone: 'event_available', link: '/reservas', visivel: this.noCondominio('RESERVATION_VIEW') },
        { rotulo: 'Portaria', icone: 'badge', link: '/portaria', visivel: this.noCondominio('ACCESS_VIEW') },
        { rotulo: 'Assembleias', icone: 'groups', link: '/assembleias', visivel: this.noCondominio('ASSEMBLY_VIEW') },
        { rotulo: 'Contratos', icone: 'contract', link: '/contratos', visivel: this.noCondominio('SUPPLIER_VIEW') },
        { rotulo: 'Documentos', icone: 'folder', link: '/documentos', visivel: this.noCondominio('DOCUMENT_VIEW') },
      ],
    },
    {
      titulo: 'Acesso e segurança',
      icone: 'admin_panel_settings',
      itens: [
        { rotulo: 'Usuários', icone: 'group', link: '/usuarios', visivel: () => this.auth.pode('USER_VIEW') },
        { rotulo: 'Auditoria', icone: 'policy', link: '/auditoria', visivel: () => this.auth.pode('AUDIT_VIEW') },
        { rotulo: 'Administradoras', icone: 'corporate_fare', link: '/organizacoes', visivel: () => !!this.auth.usuario()?.administradorPlataforma },
      ],
    },
  ];

  protected readonly menu = computed(() => {
    this.auth.usuario();
    this.contexto.selecionado();
    this.contexto.opcoes();
    return this.grupos
      .map((g) => ({ ...g, itens: g.itens.filter((i) => i.visivel()) }))
      .filter((g) => g.itens.length > 0);
  });

  /** Grupo da página atual: fica sempre aberto. */
  protected readonly grupoAtivo = computed(() => {
    const caminho = this.url().split(/[?#]/)[0];
    return this.menu().find((g) => g.titulo && g.itens.some((i) => (i.exato ? caminho === i.link : caminho === i.link || caminho.startsWith(i.link + '/'))))?.titulo;
  });

  protected readonly mostrarSeletor = computed(() => this.auth.membroAdministradora() && this.contexto.opcoes().length > 0);

  protected readonly iniciais = computed(() => {
    const nome = this.auth.usuario()?.nome ?? '';
    return nome
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase())
      .join('');
  });

  protected readonly iconeTema = computed(() => ({ system: 'brightness_auto', light: 'light_mode', dark: 'dark_mode' })[this.tema.modo()]);

  constructor() {
    // Ao navegar para outra seção, abre o grupo dela (o usuário pode fechá-lo depois).
    effect(() => {
      const grupo = this.grupoAtivo();
      if (grupo && !untracked(this.abertos).has(grupo)) this.salvarAbertos(new Set([grupo]));
    });
  }

  estaAberto(titulo: string): boolean {
    return this.abertos().has(titulo);
  }

  /** Um grupo aberto por vez, como um acordeão. */
  alternarGrupo(titulo: string): void {
    this.salvarAbertos(this.abertos().has(titulo) ? new Set() : new Set([titulo]));
  }

  private salvarAbertos(proximo: Set<string>): void {
    this.abertos.set(proximo);
    gravarValor(CHAVE_ABERTOS, JSON.stringify([...proximo]));
  }

  alternarRecolhido(): void {
    this.recolhido.update((v) => !v);
    gravarValor(CHAVE_RECOLHIDO, this.recolhido() ? '1' : null);
  }

  definirTema(modo: ModoTema): void {
    this.tema.definir(modo);
  }

  /** Usuário cujo único acesso é o portal do morador. */
  apenasMorador(): boolean {
    const perfil = this.auth.usuario();
    if (!perfil || perfil.permissoesAdministradora.length > 0) return false;
    return Object.values(perfil.permissoesCondominio).every((p) => p.length === 1 && p[0] === 'PORTAL_ACCESS');
  }

  alterarSenha(): void {
    this.dialog
      .open<AlterarSenhaDialog, void, boolean>(AlterarSenhaDialog, { width: '440px' })
      .afterClosed()
      .subscribe((ok) => ok && this.notificacao.sucesso('Senha alterada. As outras sessões foram encerradas.'));
  }
}

function lerIndicador(chave: string): boolean {
  try {
    return localStorage.getItem(chave) === '1';
  } catch {
    return false;
  }
}

function lerLista(chave: string): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(chave) ?? '[]') as unknown;
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

function gravarValor(chave: string, valor: string | null): void {
  try {
    if (valor === null) localStorage.removeItem(chave);
    else localStorage.setItem(chave, valor);
  } catch {
    // armazenamento indisponível: a preferência vale só nesta sessão
  }
}

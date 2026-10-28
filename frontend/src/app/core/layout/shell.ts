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
import { ChangePasswordDialog } from '../../features/auth/change-password-dialog';
import { Permission } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { CondominiumContext } from '../context/condominium-context';
import { NotificationBell } from '../notify/notification-bell';
import { NotificationService } from '../notify/notification.service';
import { ResponsiveTables } from './responsive-tables';
import { ThemeMode, ThemeService } from './theme.service';

interface NavItem {
  label: string;
  icon: string;
  link: string;
  exact?: boolean;
  visible: () => boolean;
}

interface NavGroup {
  title: string;
  icon: string;
  items: NavItem[];
}

const OPEN_KEY = 'tc-nav-open';
const COLLAPSED_KEY = 'tc-nav-collapsed';

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
    NotificationBell,
    ResponsiveTables,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly context = inject(CondominiumContext);
  protected readonly theme = inject(ThemeService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  /** Menu recolhido (só ícones) — opção do desktop, guardada no navegador. */
  protected readonly collapsed = signal(readFlag(COLLAPSED_KEY));
  protected readonly mini = computed(() => this.collapsed() && !this.isHandset());

  /** Grupo aberto no menu (no máximo um). */
  protected readonly open = signal<ReadonlySet<string>>(new Set(readList(OPEN_KEY)));

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Permissão no condomínio selecionado. */
  private readonly inCondo = (p: Permission) => () => this.context.can(p);

  private readonly groups: NavGroup[] = [
    {
      title: '',
      icon: '',
      items: [
        { label: 'Início', icon: 'space_dashboard', link: '/', exact: true, visible: () => this.auth.isOrganizationMember() && !this.isResidentOnly() },
        { label: 'Portal do morador', icon: 'home', link: '/portal', visible: () => this.context.canAny('PORTAL_ACCESS') },
      ],
    },
    {
      title: 'Cadastros',
      icon: 'apartment',
      items: [
        { label: 'Condomínios', icon: 'apartment', link: '/condominios', visible: () => this.auth.canAny('CONDOMINIUM_VIEW') },
        { label: 'Unidades', icon: 'door_front', link: '/unidades', visible: this.inCondo('STRUCTURE_VIEW') },
        { label: 'Pessoas', icon: 'contacts', link: '/pessoas', visible: () => this.context.canAny('PEOPLE_VIEW') },
      ],
    },
    {
      title: 'Financeiro',
      icon: 'account_balance',
      items: [
        { label: 'Financeiro', icon: 'account_balance', link: '/financeiro', visible: this.inCondo('FINANCE_VIEW') },
        { label: 'Cobrança e inadimplência', icon: 'request_quote', link: '/cobranca', visible: this.inCondo('BILLING_VIEW') },
        { label: 'Fornecedores', icon: 'storefront', link: '/fornecedores', visible: () => this.context.canAny('SUPPLIER_VIEW') },
        { label: 'Relatórios', icon: 'bar_chart', link: '/relatorios', visible: this.inCondo('REPORT_VIEW') },
      ],
    },
    {
      title: 'Administração',
      icon: 'home_work',
      items: [
        { label: 'Ocorrências', icon: 'build', link: '/ocorrencias', visible: this.inCondo('OCCURRENCE_VIEW') },
        { label: 'Comunicados', icon: 'campaign', link: '/comunicados', visible: this.inCondo('ANNOUNCEMENT_VIEW') },
        { label: 'Reservas', icon: 'event_available', link: '/reservas', visible: this.inCondo('RESERVATION_VIEW') },
        { label: 'Portaria', icon: 'badge', link: '/portaria', visible: this.inCondo('ACCESS_VIEW') },
        { label: 'Assembleias', icon: 'groups', link: '/assembleias', visible: this.inCondo('ASSEMBLY_VIEW') },
        { label: 'Contratos', icon: 'contract', link: '/contratos', visible: this.inCondo('SUPPLIER_VIEW') },
        { label: 'Documentos', icon: 'folder', link: '/documentos', visible: this.inCondo('DOCUMENT_VIEW') },
      ],
    },
    {
      title: 'Acesso e segurança',
      icon: 'admin_panel_settings',
      items: [
        { label: 'Usuários', icon: 'group', link: '/usuarios', visible: () => this.auth.can('USER_VIEW') },
        { label: 'Auditoria', icon: 'policy', link: '/auditoria', visible: () => this.auth.can('AUDIT_VIEW') },
        { label: 'Administradoras', icon: 'corporate_fare', link: '/organizacoes', visible: () => !!this.auth.user()?.platformAdmin },
      ],
    },
  ];

  protected readonly nav = computed(() => {
    this.auth.user();
    this.context.selected();
    this.context.options();
    return this.groups
      .map((g) => ({ ...g, items: g.items.filter((i) => i.visible()) }))
      .filter((g) => g.items.length > 0);
  });

  /** Grupo da página atual: fica sempre aberto. */
  protected readonly activeGroup = computed(() => {
    const path = this.url().split(/[?#]/)[0];
    return this.nav().find((g) => g.title && g.items.some((i) => (i.exact ? path === i.link : path === i.link || path.startsWith(i.link + '/'))))?.title;
  });

  protected readonly showSelector = computed(() => this.auth.isOrganizationMember() && this.context.options().length > 0);

  protected readonly initials = computed(() => {
    const name = this.auth.user()?.name ?? '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase())
      .join('');
  });

  protected readonly themeIcon = computed(() => ({ system: 'brightness_auto', light: 'light_mode', dark: 'dark_mode' })[this.theme.mode()]);

  constructor() {
    // Ao navegar para outra seção, abre o grupo dela (o usuário pode fechá-lo depois).
    effect(() => {
      const group = this.activeGroup();
      if (group && !untracked(this.open).has(group)) this.saveOpen(new Set([group]));
    });
  }

  isOpen(title: string): boolean {
    return this.open().has(title);
  }

  /** Um grupo aberto por vez, como um acordeão. */
  toggleGroup(title: string): void {
    this.saveOpen(this.open().has(title) ? new Set() : new Set([title]));
  }

  private saveOpen(next: Set<string>): void {
    this.open.set(next);
    writeValue(OPEN_KEY, JSON.stringify([...next]));
  }

  toggleCollapsed(): void {
    this.collapsed.update((v) => !v);
    writeValue(COLLAPSED_KEY, this.collapsed() ? '1' : null);
  }

  setTheme(mode: ThemeMode): void {
    this.theme.set(mode);
  }

  /** Usuário cujo único acesso é o portal do morador. */
  isResidentOnly(): boolean {
    const me = this.auth.user();
    if (!me || me.organizationPermissions.length > 0) return false;
    return Object.values(me.condominiumPermissions).every((p) => p.length === 1 && p[0] === 'PORTAL_ACCESS');
  }

  changePassword(): void {
    this.dialog
      .open<ChangePasswordDialog, void, boolean>(ChangePasswordDialog, { width: '440px' })
      .afterClosed()
      .subscribe((ok) => ok && this.notify.success('Senha alterada. As outras sessões foram encerradas.'));
  }
}

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

function readList(key: string): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(key) ?? '[]') as unknown;
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

function writeValue(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // armazenamento indisponível: a preferência vale só nesta sessão
  }
}

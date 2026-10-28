import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { ChangePasswordDialog } from '../../features/auth/change-password-dialog';
import { Permission } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { CondominiumContext } from '../context/condominium-context';
import { NotificationBell } from '../notify/notification-bell';
import { NotificationService } from '../notify/notification.service';

interface NavItem {
  label: string;
  icon: string;
  link: string;
  exact?: boolean;
  visible: () => boolean;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

@Component({
  selector: 'tc-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
    MatSelectModule,
    MatFormFieldModule,
    NotificationBell,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly context = inject(CondominiumContext);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  /** Permissão no condomínio selecionado. */
  private readonly inCondo = (p: Permission) => () => this.context.can(p);

  private readonly groups: NavGroup[] = [
    {
      title: '',
      items: [
        { label: 'Início', icon: 'space_dashboard', link: '/', exact: true, visible: () => this.auth.isOrganizationMember() && !this.isResidentOnly() },
        { label: 'Portal do morador', icon: 'home', link: '/portal', visible: () => this.context.canAny('PORTAL_ACCESS') },
      ],
    },
    {
      title: 'Cadastros',
      items: [
        { label: 'Condomínios', icon: 'apartment', link: '/condominios', visible: () => this.auth.canAny('CONDOMINIUM_VIEW') },
        { label: 'Unidades', icon: 'door_front', link: '/unidades', visible: this.inCondo('STRUCTURE_VIEW') },
        { label: 'Pessoas', icon: 'contacts', link: '/pessoas', visible: () => this.context.canAny('PEOPLE_VIEW') },
      ],
    },
    {
      title: 'Financeiro',
      items: [
        { label: 'Financeiro', icon: 'account_balance', link: '/financeiro', visible: this.inCondo('FINANCE_VIEW') },
        { label: 'Cobrança e inadimplência', icon: 'request_quote', link: '/cobranca', visible: this.inCondo('BILLING_VIEW') },
        { label: 'Fornecedores', icon: 'storefront', link: '/fornecedores', visible: () => this.context.canAny('SUPPLIER_VIEW') },
        { label: 'Relatórios', icon: 'bar_chart', link: '/relatorios', visible: this.inCondo('REPORT_VIEW') },
      ],
    },
    {
      title: 'Administração',
      items: [
        { label: 'Ocorrências', icon: 'build', link: '/ocorrencias', visible: this.inCondo('OCCURRENCE_VIEW') },
        { label: 'Comunicados', icon: 'campaign', link: '/comunicados', visible: this.inCondo('ANNOUNCEMENT_VIEW') },
        { label: 'Assembleias', icon: 'groups', link: '/assembleias', visible: this.inCondo('ASSEMBLY_VIEW') },
        { label: 'Contratos', icon: 'contract', link: '/contratos', visible: this.inCondo('SUPPLIER_VIEW') },
        { label: 'Documentos', icon: 'folder', link: '/documentos', visible: this.inCondo('DOCUMENT_VIEW') },
      ],
    },
    {
      title: 'Acesso e segurança',
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
      .map((g) => ({ title: g.title, items: g.items.filter((i) => i.visible()) }))
      .filter((g) => g.items.length > 0);
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

import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Observable, filter } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notify/notification.service';
import { FieldError } from '../../shared/forms/field-error';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { RoleFields } from './role-fields';
import { Assignment, UserDetail as User } from './user.models';
import { UserService } from './user.service';

@Component({
  selector: 'tc-user-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    FieldError,
    PageHeader,
    EmptyState,
    RoleFields,
  ],
  templateUrl: './user-detail.html',
  styleUrl: './user-detail.scss',
})
export class UserDetail implements OnInit {
  private readonly service = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly user = signal<User | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);

  protected readonly canManage = computed(() => this.auth.can('USER_MANAGE'));
  protected readonly isSelf = computed(() => this.user()?.id === this.auth.user()?.id);

  protected readonly name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(150)] });
  protected readonly roleCode = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly condominiumId = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.service.get(this.id()).subscribe({
      next: (u) => {
        this.apply(u);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loadError.set(toProblem(err).detail);
        this.loading.set(false);
      },
    });
  }

  saveName(): void {
    const u = this.user();
    this.name.markAsTouched();
    if (!u || this.name.invalid) return;
    this.run(this.service.rename(u.id, this.name.value.trim(), u.version), 'Nome atualizado.');
  }

  addAssignment(): void {
    const u = this.user();
    this.roleCode.markAsTouched();
    this.condominiumId.markAsTouched();
    if (!u || this.roleCode.invalid || this.condominiumId.invalid) return;
    this.run(
      this.service.addAssignment(u.id, { roleCode: this.roleCode.value, condominiumId: this.condominiumId.value }),
      'Papel atribuído.',
      () => {
        this.roleCode.reset('');
        this.condominiumId.reset(null);
      },
    );
  }

  removeAssignment(a: Assignment): void {
    const u = this.user();
    if (!u) return;
    this.confirm({
      title: 'Remover papel',
      message: `Remover "${a.roleName}"${a.condominiumName ? ' em ' + a.condominiumName : ''} de ${u.name}? O acesso correspondente é retirado imediatamente.`,
      confirmLabel: 'Remover',
      danger: true,
    }).subscribe(() => this.run(this.service.removeAssignment(u.id, a.id), 'Papel removido.'));
  }

  toggleActive(): void {
    const u = this.user();
    if (!u) return;
    const deactivating = u.active;
    this.confirm(
      deactivating
        ? { title: 'Desativar usuário', message: `${u.name} perderá o acesso imediatamente e todas as sessões serão encerradas.`, confirmLabel: 'Desativar', danger: true }
        : { title: 'Reativar usuário', message: `Reativar o acesso de ${u.name}?`, confirmLabel: 'Reativar' },
    ).subscribe(() =>
      this.run(deactivating ? this.service.deactivate(u.id) : this.service.activate(u.id), deactivating ? 'Usuário desativado.' : 'Usuário reativado.'),
    );
  }

  resendInvite(): void {
    const u = this.user();
    if (!u) return;
    this.busy.set(true);
    this.actionError.set(null);
    this.service.resendInvite(u.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.notify.success(`Novo convite enviado para ${u.email}. O convite anterior deixou de valer.`);
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private run(call$: Observable<User>, success: string, after?: () => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    call$.subscribe({
      next: (u) => {
        this.apply(u);
        this.busy.set(false);
        this.notify.success(success);
        after?.();
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  private fail(err: unknown): void {
    this.busy.set(false);
    const problem = toProblem(err);
    if (problem.status !== 403 && problem.status !== 0 && problem.status < 500) {
      this.actionError.set(problem.code === 'VALIDATION_ERROR' ? (problem.errors ?? []).map((e) => e.message).join(' ') : problem.detail);
    }
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter((r): r is true => r === true));
  }

  private apply(u: User): void {
    this.user.set(u);
    this.name.reset(u.name);
    if (this.canManage()) {
      this.name.enable();
    } else {
      this.name.disable();
    }
  }
}

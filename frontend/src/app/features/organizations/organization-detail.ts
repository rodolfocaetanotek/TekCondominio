import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { DocPipe } from '../../shared/format/doc.pipe';
import { FieldError } from '../../shared/forms/field-error';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Organization, OrganizationService, OrganizationUser } from './organization.service';

/**
 * Ficha da administradora para o administrador da plataforma: usuários, convite de administradores e situação.
 * A gestão completa de usuários (papéis por condomínio) é feita pelos administradores da própria administradora.
 */
@Component({
  selector: 'tc-organization-detail',
  imports: [DatePipe, DocPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressBarModule, FieldError, PageHeader, EmptyState],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/organizacoes" class="back"><mat-icon>arrow_back</mat-icon> Administradoras</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir" [description]="message" /></section>
      } @else if (org(); as o) {
        <tc-page-header eyebrow="Administradora" [title]="o.name" [subtitle]="'CNPJ ' + (o.cnpj | doc: 'cnpj')">
          <span class="tc-status big" [class.is-active]="o.active" [class.is-inactive]="!o.active">{{ o.active ? 'Ativa' : 'Inativa' }}</span>
          <button mat-stroked-button (click)="toggleActive(o)" [class.danger-text]="o.active">
            <mat-icon>{{ o.active ? 'block' : 'check_circle' }}</mat-icon>{{ o.active ? 'Inativar' : 'Reativar' }}
          </button>
        </tc-page-header>

        <div class="layout">
          <section class="tc-table-card">
            <h2 class="tc-section-title pad">Usuários ({{ users().length }})</h2>
            @for (u of users(); track u.id) {
              <div class="row">
                <div class="who">
                  <strong>{{ u.name }}</strong>
                  <span class="tc-muted">{{ u.email }} · {{ u.roles.join(', ') || 'sem papel' }}</span>
                </div>
                @if (u.pendingInvite && u.active) {
                  <span class="tc-status is-pending">Convite pendente</span>
                } @else if (!u.active) {
                  <span class="tc-status is-inactive">Inativo</span>
                } @else {
                  <span class="tc-muted small">{{ u.lastLoginAt ? 'Último acesso ' + (u.lastLoginAt | date: 'dd/MM/yyyy') : '' }}</span>
                }
              </div>
            } @empty {
              <tc-empty-state icon="group" title="Nenhum usuário" />
            }
          </section>

          <section class="tc-card">
            <h2 class="tc-section-title">Convidar administrador</h2>
            <p class="tc-muted small">A pessoa recebe um e-mail para definir a própria senha e passa a gerenciar condomínios,
              usuários e papéis desta administradora (menu <strong>Usuários</strong>).</p>
            <form [formGroup]="form" (ngSubmit)="invite(o)" novalidate>
              <mat-form-field class="full">
                <mat-label>Nome</mat-label>
                <input matInput formControlName="name" required />
                <mat-error><tc-field-error [control]="form.controls.name" /></mat-error>
              </mat-form-field>
              <mat-form-field class="full">
                <mat-label>E-mail</mat-label>
                <input matInput type="email" formControlName="email" required autocomplete="off" />
                <mat-error><tc-field-error [control]="form.controls.email" /></mat-error>
              </mat-form-field>
              <button mat-flat-button type="submit" [disabled]="busy()"><mat-icon>send</mat-icon> Enviar convite</button>
            </form>
          </section>
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .pad { padding: 16px 20px 4px; margin: 0; }
    .row { display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-top: 1px solid var(--mat-sys-outline-variant); }
    .who { flex: 1; display: grid; }
    .small { font: var(--mat-sys-body-small); }
    .full { width: 100%; }
    .tc-section-title { margin-top: 0; }
    .tc-status.big { font: var(--mat-sys-label-large); padding: 4px 14px; align-self: center; }
    .danger-text { --mat-button-outlined-label-text-color: var(--mat-sys-error); }
  `,
})
export class OrganizationDetail implements OnInit {
  private readonly service = inject(OrganizationService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  readonly id = input.required<string>();
  protected readonly org = signal<Organization | null>(null);
  protected readonly users = signal<OrganizationUser[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.service.get(this.id()).subscribe({
      next: (o) => this.org.set(o),
      error: (err: unknown) => this.error.set(toProblem(err).detail),
    });
    this.loadUsers();
  }

  invite(o: Organization): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    const { name, email } = this.form.getRawValue();
    this.service.inviteAdmin(o.id, name, email).subscribe({
      next: (u) => {
        this.busy.set(false);
        this.form.reset();
        this.notify.success(`Convite enviado para ${u.email}.`);
        this.loadUsers();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        const p = toProblem(err);
        if (p.code === 'USER_EMAIL_TAKEN') this.form.controls.email.setErrors({ server: p.detail });
        else this.notify.error(p.detail);
      },
    });
  }

  toggleActive(o: Organization): void {
    const data: ConfirmDialogData = o.active
      ? { title: 'Inativar administradora', message: `Todos os usuários de "${o.name}" perderão o acesso imediatamente. Os dados são preservados.`, confirmLabel: 'Inativar', danger: true }
      : { title: 'Reativar administradora', message: `Reativar o acesso de "${o.name}"?`, confirmLabel: 'Reativar' };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter((r) => r === true),
        switchMap(() => this.service.setActive(o.id, !o.active)),
      )
      .subscribe({
        next: (updated) => {
          this.org.set(updated);
          this.notify.success(updated.active ? 'Administradora reativada.' : 'Administradora inativada.');
        },
        error: (err: unknown) => this.notify.error(toProblem(err).detail),
      });
  }

  private loadUsers(): void {
    this.service.users(this.id()).subscribe({ next: (u) => this.users.set(u) });
  }
}

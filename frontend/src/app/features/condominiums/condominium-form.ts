import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Observable, filter, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { formatCnpj, formatPhone, formatZipCode } from '../../shared/format/br-format';
import { MaskDirective } from '../../shared/format/mask.directive';
import { FieldError } from '../../shared/forms/field-error';
import { applyServerErrors } from '../../shared/forms/server-errors';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import {
  BRAZILIAN_STATES,
  CONDOMINIUM_STATUS_LABELS,
  CONDOMINIUM_TYPE_LABELS,
  Condominium,
  CondominiumRequest,
  CondominiumType,
} from './condominium.models';
import { CondominiumService } from './condominium.service';

const blank = (v: string | null | undefined): string | null => (v && v.trim() ? v.trim() : null);

@Component({
  selector: 'tc-condominium-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MaskDirective,
    FieldError,
    PageHeader,
    EmptyState,
  ],
  templateUrl: './condominium-form.html',
  styleUrl: './condominium-form.scss',
})
export class CondominiumForm implements OnInit {
  private readonly service = inject(CondominiumService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);
  private readonly context = inject(CondominiumContext);

  /** Parâmetro de rota; ausente em /condominios/novo. */
  readonly id = input<string>();

  protected readonly types = Object.entries(CONDOMINIUM_TYPE_LABELS) as [CondominiumType, string][];
  protected readonly states = BRAZILIAN_STATES;
  protected readonly statusLabels = CONDOMINIUM_STATUS_LABELS;

  protected readonly condominium = signal<Condominium | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly stale = signal(false);

  protected readonly isNew = computed(() => !this.id());
  protected readonly isInactive = computed(() => this.condominium()?.status === 'INACTIVE');
  protected readonly canEdit = computed(() => {
    const id = this.id();
    if (!id) return this.auth.can('CONDOMINIUM_CREATE');
    return this.auth.canIn('CONDOMINIUM_EDIT', id) && !this.isInactive();
  });
  protected readonly canChangeStatus = computed(() => {
    const id = this.id();
    return !!id && this.auth.canIn('CONDOMINIUM_INACTIVATE', id);
  });

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    legalName: ['', [Validators.maxLength(200)]],
    // Apenas formato; dígitos verificadores são validados pelo backend.
    cnpj: ['', [Validators.pattern(/^[0-9A-Z]{2}\.[0-9A-Z]{3}\.[0-9A-Z]{3}\/[0-9A-Z]{4}-\d{2}$/)]],
    type: ['RESIDENTIAL' as CondominiumType, [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.pattern(/^\(\d{2}\) \d{4,5}-\d{4}$/)]],
    address: this.fb.group({
      zipCode: ['', [Validators.pattern(/^\d{5}-\d{3}$/)]],
      street: ['', [Validators.maxLength(200)]],
      number: ['', [Validators.maxLength(20)]],
      complement: ['', [Validators.maxLength(100)]],
      district: ['', [Validators.maxLength(100)]],
      city: ['', [Validators.maxLength(100)]],
      state: [''],
    }),
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.form.disable();
      this.load(id);
    }
  }

  load(id: string): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.service.get(id).subscribe({
      next: (c) => {
        this.apply(c);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loadError.set(toProblem(err).detail);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving() || !this.canEdit()) {
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.stale.set(false);
    const current = this.condominium();
    const request = this.toRequest(current?.version);
    const call$: Observable<Condominium> = current
      ? this.service.update(current.id, request)
      : this.service.create(request);
    call$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notify.success(current ? 'Alterações salvas.' : 'Condomínio cadastrado.');
        this.context.reload();
        if (current) {
          this.apply(saved);
        } else {
          void this.router.navigate(['/condominios', saved.id], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const problem = toProblem(err);
        if (problem.code === 'STALE_VERSION') {
          this.stale.set(true);
        } else if (problem.code === 'CONDOMINIUM_CNPJ_TAKEN') {
          this.form.controls.cnpj.setErrors({ server: problem.detail });
        } else if (problem.code === 'VALIDATION_ERROR') {
          const rest = applyServerErrors(this.form, problem);
          this.formError.set(rest.length ? rest.join(' ') : problem.detail);
        } else if (problem.status !== 403 && problem.status !== 0 && problem.status < 500) {
          this.formError.set(problem.detail);
        }
      },
    });
  }

  inactivate(): void {
    const c = this.condominium();
    if (!c) return;
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, {
        data: {
          title: 'Inativar condomínio',
          message: `"${c.name}" deixará de receber novos lançamentos e cobranças. O histórico será preservado.`,
          confirmLabel: 'Inativar',
          danger: true,
          reasonLabel: 'Justificativa',
        },
        width: '480px',
      })
      .afterClosed()
      .pipe(
        filter((reason): reason is string => typeof reason === 'string'),
        switchMap((reason) => this.service.inactivate(c.id, reason)),
      )
      .subscribe({
        next: (updated) => {
          this.apply(updated);
          this.notify.success('Condomínio inativado.');
          this.context.reload();
        },
        error: (err: unknown) => this.formError.set(toProblem(err).detail),
      });
  }

  activate(): void {
    const c = this.condominium();
    if (!c) return;
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, {
        data: { title: 'Reativar condomínio', message: `Reativar "${c.name}"?`, confirmLabel: 'Reativar' },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        filter((ok) => ok === true),
        switchMap(() => this.service.activate(c.id)),
      )
      .subscribe({
        next: (updated) => {
          this.apply(updated);
          this.notify.success('Condomínio reativado.');
          this.context.reload();
        },
        error: (err: unknown) => this.formError.set(toProblem(err).detail),
      });
  }

  reload(): void {
    const id = this.id();
    if (id) {
      this.stale.set(false);
      this.load(id);
    }
  }

  private apply(c: Condominium): void {
    this.condominium.set(c);
    this.form.reset({
      name: c.name,
      legalName: c.legalName ?? '',
      cnpj: formatCnpj(c.cnpj),
      type: c.type,
      email: c.email ?? '',
      phone: formatPhone(c.phone),
      address: {
        zipCode: formatZipCode(c.address?.zipCode),
        street: c.address?.street ?? '',
        number: c.address?.number ?? '',
        complement: c.address?.complement ?? '',
        district: c.address?.district ?? '',
        city: c.address?.city ?? '',
        state: c.address?.state ?? '',
      },
    });
    if (this.canEdit()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  private toRequest(version: number | undefined): CondominiumRequest {
    const v = this.form.getRawValue();
    const a = v.address;
    const hasAddress = Object.values(a).some((x) => !!x?.trim());
    return {
      name: v.name.trim(),
      legalName: blank(v.legalName),
      cnpj: blank(v.cnpj),
      type: v.type,
      email: blank(v.email),
      phone: blank(v.phone),
      address: hasAddress
        ? {
            zipCode: blank(a.zipCode),
            street: blank(a.street),
            number: blank(a.number),
            complement: blank(a.complement),
            district: blank(a.district),
            city: blank(a.city),
            state: blank(a.state),
          }
        : null,
      version,
    };
  }
}

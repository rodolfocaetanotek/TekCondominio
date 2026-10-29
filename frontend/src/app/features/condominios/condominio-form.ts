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
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { formatCnpj, formatPhone, formatZipCode } from '../../comum/formato/formato-br';
import { MaskDirective } from '../../comum/formato/mascara.directive';
import { FieldError } from '../../comum/formularios/erro-campo';
import { applyServerErrors } from '../../comum/formularios/erros-servidor';
import { ConfirmDialog, ConfirmDialogData } from '../../comum/ui/confirmacao-dialog';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { PageHeader } from '../../comum/ui/cabecalho-pagina';
import {
  BRAZILIAN_STATES,
  CONDOMINIUM_STATUS_LABELS,
  CONDOMINIUM_TYPE_LABELS,
  Condominio,
  CondominioRequest,
  TipoCondominio,
} from './condominio.models';
import { CondominiumService } from './condominio.service';

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
  templateUrl: './condominio-form.html',
  styleUrl: './condominio-form.scss',
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

  protected readonly types = Object.entries(CONDOMINIUM_TYPE_LABELS) as [TipoCondominio, string][];
  protected readonly states = BRAZILIAN_STATES;
  protected readonly statusLabels = CONDOMINIUM_STATUS_LABELS;

  protected readonly condominium = signal<Condominio | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly stale = signal(false);

  protected readonly isNew = computed(() => !this.id());
  protected readonly isInactive = computed(() => this.condominium()?.situacao === 'INACTIVE');
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
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    razaoSocial: ['', [Validators.maxLength(200)]],
    // Apenas formato; dígitos verificadores são validados pelo backend.
    cnpj: ['', [Validators.pattern(/^[0-9A-Z]{2}\.[0-9A-Z]{3}\.[0-9A-Z]{3}\/[0-9A-Z]{4}-\d{2}$/)]],
    tipo: ['RESIDENTIAL' as TipoCondominio, [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    telefone: ['', [Validators.pattern(/^\(\d{2}\) \d{4,5}-\d{4}$/)]],
    endereco: this.fb.group({
      cep: ['', [Validators.pattern(/^\d{5}-\d{3}$/)]],
      logradouro: ['', [Validators.maxLength(200)]],
      numero: ['', [Validators.maxLength(20)]],
      complemento: ['', [Validators.maxLength(100)]],
      bairro: ['', [Validators.maxLength(100)]],
      cidade: ['', [Validators.maxLength(100)]],
      uf: [''],
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
    const request = this.toRequest(current?.versao);
    const call$: Observable<Condominio> = current
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
        if (problem.codigo === 'STALE_VERSION') {
          this.stale.set(true);
        } else if (problem.codigo === 'CONDOMINIUM_CNPJ_TAKEN') {
          this.form.controls.cnpj.setErrors({ server: problem.detail });
        } else if (problem.codigo === 'VALIDATION_ERROR') {
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
          message: `"${c.nome}" deixará de receber novos lançamentos e cobranças. O histórico será preservado.`,
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
        data: { title: 'Reativar condomínio', message: `Reativar "${c.nome}"?`, confirmLabel: 'Reativar' },
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

  private apply(c: Condominio): void {
    this.condominium.set(c);
    this.form.reset({
      nome: c.nome,
      razaoSocial: c.razaoSocial ?? '',
      cnpj: formatCnpj(c.cnpj),
      tipo: c.tipo,
      email: c.email ?? '',
      telefone: formatPhone(c.telefone),
      endereco: {
        cep: formatZipCode(c.endereco?.cep),
        logradouro: c.endereco?.logradouro ?? '',
        numero: c.endereco?.numero ?? '',
        complemento: c.endereco?.complemento ?? '',
        bairro: c.endereco?.bairro ?? '',
        cidade: c.endereco?.cidade ?? '',
        uf: c.endereco?.uf ?? '',
      },
    });
    if (this.canEdit()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  private toRequest(versao: number | undefined): CondominioRequest {
    const v = this.form.getRawValue();
    const a = v.endereco;
    const hasAddress = Object.values(a).some((x) => !!x?.trim());
    return {
      nome: v.nome.trim(),
      razaoSocial: blank(v.razaoSocial),
      cnpj: blank(v.cnpj),
      tipo: v.tipo,
      email: blank(v.email),
      telefone: blank(v.telefone),
      endereco: hasAddress
        ? {
            cep: blank(a.cep),
            logradouro: blank(a.logradouro),
            numero: blank(a.numero),
            complemento: blank(a.complemento),
            bairro: blank(a.bairro),
            cidade: blank(a.cidade),
            uf: blank(a.uf),
          }
        : null,
      versao,
    };
  }
}

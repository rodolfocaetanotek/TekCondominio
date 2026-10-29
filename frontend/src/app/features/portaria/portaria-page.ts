import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { LabelPipe } from '../../comum/rotulo.pipe';
import { options } from '../../comum/rotulos';
import { CondominiumRequired } from '../../comum/ui/condominio-obrigatorio';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { openForm } from '../../comum/ui/formulario-dialog';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { PageHeader } from '../../comum/ui/cabecalho-pagina';
import { StructureService } from '../estrutura/estrutura.service';
import { AutorizacaoVisita, VisitorApi } from './portaria-api';

/** Painel da portaria: entrada por código, chegadas sem aviso, quem está no condomínio e saídas. */
@Component({
  selector: 'tc-gate-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, PageHeader,
    CondominiumRequired, EmptyState, IsoDatePipe, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Administração" title="Portaria" [subtitle]="context.selected()?.nome">
        @if (canEdit()) { <button mat-flat-button (click)="arrival()"><mat-icon>person_add</mat-icon> Registrar chegada</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (canEdit()) {
          <section class="tc-card code">
            <span class="code-icon" aria-hidden="true"><mat-icon>pin</mat-icon></span>
            <mat-form-field class="code-field" subscriptSizing="dynamic"><mat-label>Código de acesso (6 dígitos)</mat-label>
              <input matInput [formControl]="code" inputmode="numeric" maxlength="6" autocomplete="off" (keydown.enter)="checkInByCode()" /></mat-form-field>
            <button mat-flat-button class="code-btn" (click)="checkInByCode()" [disabled]="code.invalid"><mat-icon>login</mat-icon> Liberar entrada</button>
          </section>
        }
        @for (group of groups(); track group.title; let gi = $index) {
          <section class="tc-card group" [class.g-inside]="gi === 0" [class.g-expected]="gi === 1" [class.g-left]="gi === 2">
            <h3 class="tc-section-title">{{ group.title }} ({{ group.items.length }})</h3>
            @for (v of group.items; track v.id) {
              <div class="row">
                <div class="who"><strong>{{ v.nome }}</strong> · {{ v.tipo | label: 'visitorKind' }} · unid. {{ v.rotuloUnidade }}<br />
                  <span class="tc-muted small">{{ v.empresa ?? '' }}{{ v.documento ? ' · doc. ' + v.documento : '' }}{{ v.placaVeiculo ? ' · placa ' + v.placaVeiculo : '' }}
                    · autorizado por {{ v.nomeAutorizadoPor }}{{ v.validaDe !== v.validaAte ? ' · válido até ' + (v.validaAte | isoDate) : '' }}
                    {{ v.entradaEm ? ' · entrada ' + (v.entradaEm | date: 'HH:mm') : '' }}{{ v.saidaEm ? ' · saída ' + (v.saidaEm | date: 'HH:mm') : '' }}</span></div>
                @if (canEdit()) {
                  @if (v.situacao === 'EXPECTED') { <button mat-flat-button class="act" (click)="act(v, 'in')"><mat-icon>login</mat-icon> Entrada</button> }
                  @if (v.situacao === 'INSIDE') { <button mat-stroked-button class="act" (click)="act(v, 'out')"><mat-icon>logout</mat-icon> Saída</button> }
                }
              </div>
            } @empty { <p class="tc-muted none">Nenhum registro.</p> }
          </section>
        }
        @if ((list() ?? []).length === 0) { <section class="tc-card"><tc-empty-state icon="badge" title="Sem movimentação hoje" /></section> }
      }
    </div>
  `,
  styles: `
    :host .tc-page > section { margin-bottom: 16px; }
    .code { display: flex; gap: 12px 16px; align-items: center; flex-wrap: wrap; padding: 20px 24px;
      border-color: color-mix(in srgb, var(--mat-sys-primary) 35%, var(--tc-border));
      background: color-mix(in srgb, var(--mat-sys-primary) 4%, var(--tc-surface)); }
    .code-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; flex: 0 0 auto;
      background: color-mix(in srgb, var(--mat-sys-primary) 14%, transparent); color: var(--mat-sys-primary); }
    .code-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .code-field { flex: 1 1 260px; max-width: 420px; min-width: 0; }
    .code-field input { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 26px; font-weight: 650; letter-spacing: .3em; }
    .code-btn { min-height: 52px; padding-inline: 24px; font-size: 15px; }
    .group .tc-section-title { margin-top: 0; padding-left: 10px; border-left: 3px solid var(--tc-border-strong); line-height: 1.2; }
    .g-inside .tc-section-title { border-left-color: var(--tc-success); }
    .g-expected .tc-section-title { border-left-color: var(--tc-warning); }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; padding-bottom: 0; }
    .who { flex: 1 1 260px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .who strong { font-size: 15px; }
    .act { min-width: 120px; }
    .none { margin: 0; }
    .small { font: var(--mat-sys-body-small); }
    @media (max-width: 600px) {
      .code { padding: 16px; }
      .code-icon { display: none; }
      .code-field { flex: 1 1 100%; max-width: none; }
      .code-btn, .act { flex: 1 1 100%; }
    }
  `,
})
export class GatePage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(VisitorApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly code = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] });

  protected readonly canEdit = computed(() => this.context.can('ACCESS_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.today(c!).pipe(catchError(() => of([] as AutorizacaoVisita[]))))));
  protected readonly groups = computed(() => {
    const all = this.list() ?? [];
    return [
      { title: 'No condomínio agora', items: all.filter((v) => v.situacao === 'INSIDE') },
      { title: 'Esperados hoje', items: all.filter((v) => v.situacao === 'EXPECTED') },
      { title: 'Saíram hoje', items: all.filter((v) => v.situacao === 'LEFT') },
    ];
  });

  checkInByCode(): void {
    if (this.code.invalid) return;
    this.api.checkInByCode(this.context.id()!, this.code.value).subscribe({
      next: (v) => {
        this.notify.success(`Entrada liberada: ${v.nome} (unid. ${v.rotuloUnidade}).`);
        this.code.reset();
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  act(v: AutorizacaoVisita, action: 'in' | 'out'): void {
    const c = this.context.id()!;
    (action === 'in' ? this.api.checkIn(c, v.id) : this.api.checkOut(c, v.id)).subscribe({
      next: () => this.tick.update((t) => t + 1),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  arrival(): void {
    const c = this.context.id()!;
    this.structure.units(c, { page: 0, size: 500, active: 'true' }).pipe(map((p) => p.conteudo)).subscribe((units) =>
      openForm(this.dialog, {
        title: 'Registrar chegada',
        intro: 'Colete apenas os dados necessários. O documento é exibido mascarado e os registros são anonimizados após o prazo de retenção.',
        fields: [
          { key: 'idUnidade', label: 'Unidade de destino', type: 'select', required: true, span: 6, options: units.map((u) => ({ value: u.id, label: u.rotulo })) },
          { key: 'tipo', label: 'Tipo', type: 'select', required: true, value: 'VISITOR', span: 6, options: options('visitorKind') },
          { key: 'nome', label: 'Nome', required: true, maxLength: 150 },
          { key: 'documento', label: 'Documento (opcional)', maxLength: 20, span: 6 },
          { key: 'placaVeiculo', label: 'Placa (opcional)', maxLength: 8, span: 6 },
          { key: 'empresa', label: 'Empresa (prestador/entrega)', maxLength: 120 },
        ],
        submitLabel: 'Registrar entrada',
        submit: (v) => this.api.arrival(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1)));
  }
}

import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { todayIso } from '../../shared/ui/dates';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { AutorizacaoVisita, VisitorApi } from './visitor-api';

/** Autorizações de visitantes/prestadores/entregas no portal do morador. */
@Component({
  selector: 'tc-portal-visitors',
  imports: [MatButtonModule, MatIconModule, IsoDatePipe, LabelPipe],
  template: `
    <section class="tc-card mt">
      <div class="head">
        <p class="tc-muted small">Autorize a entrada com antecedência. Informe o <strong>código</strong> ao visitante: a portaria libera a entrada com ele
          e você recebe um aviso quando ele chegar.</p>
        <button mat-flat-button class="primary" (click)="authorize()"><mat-icon>person_add</mat-icon> Autorizar entrada</button>
      </div>
      @for (v of list() ?? []; track v.id) {
        <div class="row">
          <div class="who"><strong>{{ v.nome }}</strong> · {{ v.tipo | label: 'visitorKind' }} · unid. {{ v.rotuloUnidade }}<br />
            <span class="tc-muted small">{{ v.validaDe | isoDate }}{{ v.validaAte !== v.validaDe ? ' a ' + (v.validaAte | isoDate) : '' }}</span>
            <span class="tc-status" [class.is-pending]="v.situacao === 'EXPECTED'" [class.is-success]="v.situacao === 'INSIDE'"
              [class.is-inactive]="v.situacao === 'LEFT' || v.situacao === 'CANCELLED'">{{ v.situacao | label: 'visitorStatus' }}</span></div>
          <div class="right">
            @if (v.codigoAcesso) { <span class="code" aria-label="Código de acesso">{{ v.codigoAcesso }}</span> }
            @if (v.situacao === 'EXPECTED') { <button mat-stroked-button (click)="cancel(v)">Cancelar</button> }
          </div>
        </div>
      } @empty { <p class="tc-muted">Nenhuma autorização.</p> }
    </section>
  `,
  styles: `
    .mt { display: block; margin-top: 16px; } .small { font: var(--mat-sys-body-small); }
    .head { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 4px; border-bottom: 1px solid var(--tc-border); }
    .head p { margin: 0; flex: 1 1 280px; max-width: 60ch; line-height: 1.5; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; }
    .who { flex: 1 1 220px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .who .tc-status { margin-left: 8px; vertical-align: middle; }
    .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .code { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 22px; font-weight: 650; letter-spacing: .2em; font-variant-numeric: tabular-nums;
      padding: 4px 6px 4px 12px; border-radius: var(--tc-radius-sm); color: var(--mat-sys-primary);
      background: color-mix(in srgb, var(--mat-sys-primary) 8%, var(--tc-surface)); border: 1px dashed color-mix(in srgb, var(--mat-sys-primary) 40%, var(--tc-border)); }
    @media (max-width: 600px) {
      .head .primary { flex: 1 1 100%; }
      .right { flex: 1 1 100%; justify-content: space-between; }
    }
  `,
})
export class PortalVisitors {
  readonly condominiumId = input.required<string>();
  readonly units = input.required<{ idUnidade: string; rotulo: string }[]>();
  private readonly api = inject(VisitorApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);

  protected readonly list = toSignal(combineLatest([toObservable(this.condominiumId), toObservable(this.tick)]).pipe(
    switchMap(([c]) => this.api.portalList(c).pipe(catchError(() => of([] as AutorizacaoVisita[]))))));

  authorize(): void {
    openForm(this.dialog, {
      title: 'Autorizar entrada',
      fields: [
        { key: 'idUnidade', label: 'Unidade', type: 'select', required: true, value: this.units()[0]?.idUnidade, span: 6,
          options: this.units().map((u) => ({ value: u.idUnidade, label: u.rotulo })) },
        { key: 'tipo', label: 'Tipo', type: 'select', required: true, value: 'VISITOR', span: 6, options: options('visitorKind') },
        { key: 'nome', label: 'Nome do visitante', required: true, maxLength: 150 },
        { key: 'validaDe', label: 'De', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'validaAte', label: 'Até', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'empresa', label: 'Empresa (prestador/entrega)', maxLength: 120 },
        { key: 'observacoes', label: 'Observações para a portaria', maxLength: 500 },
      ],
      submitLabel: 'Gerar código',
      submit: (v) => this.api.portalAuthorize(this.condominiumId(), v),
    }).pipe(filter((r): r is AutorizacaoVisita => !!r)).subscribe((r) => {
      this.notify.success(`Autorização criada. Código: ${r.codigoAcesso}`);
      this.tick.update((t) => t + 1);
    });
  }

  cancel(v: AutorizacaoVisita): void {
    this.api.portalCancel(this.condominiumId(), v.id).subscribe({
      next: () => this.tick.update((t) => t + 1),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}

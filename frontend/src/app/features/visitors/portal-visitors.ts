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
import { VisitorApi, VisitorPass } from './visitor-api';

/** Autorizações de visitantes/prestadores/entregas no portal do morador. */
@Component({
  selector: 'tc-portal-visitors',
  imports: [MatButtonModule, MatIconModule, IsoDatePipe, LabelPipe],
  template: `
    <section class="tc-card mt">
      <div class="head">
        <p class="tc-muted small">Autorize a entrada com antecedência. Informe o <strong>código</strong> ao visitante: a portaria libera a entrada com ele
          e você recebe um aviso quando ele chegar.</p>
        <button mat-flat-button (click)="authorize()"><mat-icon>person_add</mat-icon> Autorizar entrada</button>
      </div>
      @for (v of list() ?? []; track v.id) {
        <div class="row">
          <div><strong>{{ v.name }}</strong> · {{ v.kind | label: 'visitorKind' }} · unid. {{ v.unitLabel }}<br />
            <span class="tc-muted small">{{ v.validFrom | isoDate }}{{ v.validUntil !== v.validFrom ? ' a ' + (v.validUntil | isoDate) : '' }}
              · {{ v.status | label: 'visitorStatus' }}</span></div>
          <div class="right">
            @if (v.accessCode) { <span class="code" aria-label="Código de acesso">{{ v.accessCode }}</span> }
            @if (v.status === 'EXPECTED') { <button mat-button (click)="cancel(v)">Cancelar</button> }
          </div>
        </div>
      } @empty { <p class="tc-muted">Nenhuma autorização.</p> }
    </section>
  `,
  styles: `
    .mt { margin-top: 16px; } .small { font: var(--mat-sys-body-small); }
    .head { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; }
    .head p { margin: 0; max-width: 60ch; }
    .row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); flex-wrap: wrap; }
    .right { display: flex; align-items: center; gap: 8px; }
    .code { font: var(--mat-sys-title-large); letter-spacing: .2em; font-variant-numeric: tabular-nums; }
  `,
})
export class PortalVisitors {
  readonly condominiumId = input.required<string>();
  readonly units = input.required<{ unitId: string; label: string }[]>();
  private readonly api = inject(VisitorApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);

  protected readonly list = toSignal(combineLatest([toObservable(this.condominiumId), toObservable(this.tick)]).pipe(
    switchMap(([c]) => this.api.portalList(c).pipe(catchError(() => of([] as VisitorPass[]))))));

  authorize(): void {
    openForm(this.dialog, {
      title: 'Autorizar entrada',
      fields: [
        { key: 'unitId', label: 'Unidade', type: 'select', required: true, value: this.units()[0]?.unitId, span: 6,
          options: this.units().map((u) => ({ value: u.unitId, label: u.label })) },
        { key: 'kind', label: 'Tipo', type: 'select', required: true, value: 'VISITOR', span: 6, options: options('visitorKind') },
        { key: 'name', label: 'Nome do visitante', required: true, maxLength: 150 },
        { key: 'validFrom', label: 'De', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'validUntil', label: 'Até', type: 'date', required: true, value: todayIso(), span: 6 },
        { key: 'company', label: 'Empresa (prestador/entrega)', maxLength: 120 },
        { key: 'notes', label: 'Observações para a portaria', maxLength: 500 },
      ],
      submitLabel: 'Gerar código',
      submit: (v) => this.api.portalAuthorize(this.condominiumId(), v),
    }).pipe(filter((r): r is VisitorPass => !!r)).subscribe((r) => {
      this.notify.success(`Autorização criada. Código: ${r.accessCode}`);
      this.tick.update((t) => t + 1);
    });
  }

  cancel(v: VisitorPass): void {
    this.api.portalCancel(this.condominiumId(), v.id).subscribe({
      next: () => this.tick.update((t) => t + 1),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}

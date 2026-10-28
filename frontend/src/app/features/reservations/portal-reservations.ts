import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { askReason } from '../../shared/ui/confirm-dialog';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { CommonArea, Reservation, ReservationApi, Slot } from './reservation-api';

/** Reservas no portal do morador: consulta de horários ocupados, pedido de reserva e cancelamento. */
@Component({
  selector: 'tc-portal-reservations',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, EmptyState, IsoDatePipe, LabelPipe],
  template: `
    <section class="tc-card mt">
      <div class="bar">
        <mat-form-field class="grow"><mat-label>Área comum</mat-label>
          <mat-select [formControl]="area">
            @for (a of areas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.name }}</mat-option> }
          </mat-select></mat-form-field>
        @if (selectedArea()) { <button mat-flat-button class="primary" (click)="book(selectedArea()!)"><mat-icon>event_available</mat-icon> Reservar</button> }
      </div>
      @if (selectedArea(); as a) {
        <p class="small info">Funciona das {{ a.openTime.slice(0, 5) }} às {{ a.closeTime.slice(0, 5) }} · até {{ a.maxDurationMinutes / 60 }} h por reserva
          · antecedência mínima de {{ a.minAdvanceHours }} h{{ a.fee > 0 ? ' · taxa de uso ' : '' }}{{ a.fee > 0 ? (a.fee | currency: 'BRL') : '' }}
          {{ a.requiresApproval ? ' · sujeita à aprovação da administração' : '' }}</p>
        @if (a.rules) { <p class="rules small">{{ a.rules }}</p> }
        <h4 class="tc-section-label">Horários já reservados (próximos 60 dias)</h4>
        <ul class="slots">
          @for (s of slots() ?? []; track s.date + s.startTime) {
            <li><mat-icon aria-hidden="true">event_busy</mat-icon><span>{{ s.date | isoDate }} · {{ s.startTime.slice(0, 5) }}–{{ s.endTime.slice(0, 5) }}{{ s.status === 'PENDING' ? ' (aguardando aprovação)' : '' }}</span></li>
          } @empty { <li class="tc-muted none">Nenhum horário reservado.</li> }
        </ul>
      } @else if ((areas() ?? []).length === 0) {
        <tc-empty-state icon="deck" title="Nenhuma área disponível para reserva" />
      }
    </section>

    <section class="tc-card mt">
      <h3 class="tc-section-title first">Minhas reservas</h3>
      @for (r of mine() ?? []; track r.id) {
        <div class="row">
          <div class="who"><strong>{{ r.areaName }}</strong> · unid. {{ r.unitLabel }}<br />
            <span class="tc-muted small">{{ r.date | isoDate }} · {{ r.startTime.slice(0, 5) }}–{{ r.endTime.slice(0, 5) }}
              {{ r.fee > 0 ? ' · taxa ' : '' }}{{ r.fee > 0 ? (r.fee | currency: 'BRL') : '' }}</span>
            @if (r.decisionNote || r.cancelReason) { <br /><span class="small note">{{ r.cancelReason ?? r.decisionNote }}</span> }</div>
          <div class="right">
            <span class="tc-status" [class.is-success]="r.status === 'CONFIRMED'" [class.is-pending]="r.status === 'PENDING'"
              [class.is-danger]="r.status === 'REJECTED'" [class.is-inactive]="r.status === 'CANCELLED'">{{ r.status | label: 'reservationStatus' }}</span>
            @if ((r.status === 'PENDING' || r.status === 'CONFIRMED') && r.date >= today) {
              <button mat-stroked-button (click)="cancel(r)">Cancelar</button>
            }
          </div>
        </div>
      } @empty { <p class="tc-muted">Você ainda não fez reservas.</p> }
    </section>
  `,
  styles: `
    .mt { display: block; margin-top: 16px; }
    .bar { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .grow { flex: 1 1 240px; min-width: 0; }
    .bar .primary { margin-bottom: 20px; }
    .small { font: var(--mat-sys-body-small); }
    .info { margin: 0 0 12px; color: var(--tc-text-muted); }
    .rules { white-space: pre-line; margin: 0 0 16px; padding: 12px 14px; border-radius: var(--tc-radius-sm); background: var(--tc-surface-muted); border: 1px solid var(--tc-border); }
    h4.tc-section-label { margin: 8px 0 6px; }
    .slots { list-style: none; margin: 0; padding: 0; }
    .slots li { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--tc-border); font-variant-numeric: tabular-nums; }
    .slots li:last-child { border-bottom: 0; }
    .slots li mat-icon { color: var(--tc-text-muted); font-size: 20px; width: 20px; height: 20px; flex: 0 0 auto; }
    .slots li.none { padding: 6px 0; }
    .tc-section-title.first { margin-top: 0; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; }
    .who { flex: 1 1 220px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .note { display: inline-block; margin-top: 4px; }
    .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    @media (max-width: 600px) {
      .bar .primary { flex: 1 1 100%; margin-bottom: 8px; }
      .right { flex: 1 1 100%; justify-content: space-between; }
    }
  `,
})
export class PortalReservations {
  readonly condominiumId = input.required<string>();
  readonly units = input.required<{ unitId: string; label: string }[]>();
  private readonly api = inject(ReservationApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly today = todayIso();
  protected readonly area = new FormControl<string | null>(null);
  private readonly areaValue = toSignal(this.area.valueChanges, { initialValue: null });

  protected readonly areas = toSignal(toObservable(this.condominiumId).pipe(
    switchMap((c) => this.api.portalAreas(c).pipe(catchError(() => of([] as CommonArea[]))))));
  protected readonly selectedArea = computed(() => (this.areas() ?? []).find((a) => a.id === this.areaValue()) ?? null);
  protected readonly slots = toSignal(combineLatest([toObservable(this.areaValue), toObservable(this.tick)]).pipe(
    filter(([a]) => !!a),
    switchMap(([a]) => this.api.portalOccupancy(this.condominiumId(), a!, this.today, plusDays(this.today, 60)).pipe(catchError(() => of([] as Slot[]))))));
  protected readonly mine = toSignal(combineLatest([toObservable(this.condominiumId), toObservable(this.tick)]).pipe(
    switchMap(([c]) => this.api.portalMine(c).pipe(catchError(() => of([] as Reservation[]))))));

  book(a: CommonArea): void {
    openForm(this.dialog, {
      title: 'Reservar ' + a.name,
      intro: a.requiresApproval ? 'A reserva ficará aguardando a aprovação da administração.' : undefined,
      fields: [
        { key: 'unitId', label: 'Unidade', type: 'select', required: true, value: this.units()[0]?.unitId,
          options: this.units().map((u) => ({ value: u.unitId, label: u.label })) },
        { key: 'date', label: 'Data', type: 'date', required: true, span: 4 },
        { key: 'startTime', label: 'Início', type: 'time', required: true, span: 4 },
        { key: 'endTime', label: 'Fim', type: 'time', required: true, span: 4 },
        { key: 'guests', label: 'Convidados', type: 'number', span: 4 },
        { key: 'notes', label: 'Observações', maxLength: 500, span: 8 },
      ],
      submitLabel: 'Reservar',
      submit: (v) => this.api.portalBook(this.condominiumId(), { ...v, areaId: a.id }),
    }).pipe(filter((r): r is Reservation => !!r)).subscribe((r) => {
      this.notify.success(r.status === 'PENDING' ? 'Pedido enviado para aprovação.' : 'Reserva confirmada.');
      this.tick.update((t) => t + 1);
    });
  }

  cancel(r: Reservation): void {
    askReason(this.dialog, 'Cancelar reserva', `${r.areaName} em ${r.date.split('-').reverse().join('/')}.`, 'Cancelar reserva')
      .pipe(switchMap((reason) => this.api.portalCancel(this.condominiumId(), r.id, reason)))
      .subscribe({
        next: () => {
          this.notify.success('Reserva cancelada.');
          this.tick.update((t) => t + 1);
        },
        error: (err: unknown) => this.notify.error(toProblem(err).detail),
      });
  }
}

function plusDays(iso: string, days: number): string {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

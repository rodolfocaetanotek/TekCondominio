import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, combineLatest, filter, map, merge, of, scan, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { askReason } from '../../shared/ui/confirm-dialog';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { todayIso } from '../../shared/ui/dates';
import { EmptyState } from '../../shared/ui/empty-state';
import { FieldDef, openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { StructureService } from '../structure/structure.service';
import { AreaComum, Reserva, ReservationApi } from './reservation-api';

/** Administração das áreas comuns e das reservas (aprovação, cancelamento, reserva em nome da unidade). */
@Component({
  selector: 'tc-reservation-page',
  imports: [CurrencyPipe, ReactiveFormsModule, MatTabsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTooltipModule, MatMenuModule, PageHeader, CondominiumRequired, EmptyState, IsoDatePipe, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Administração" title="Reservas de áreas comuns" [subtitle]="context.selected()?.nome">
        @if (canEdit()) {
          <button mat-stroked-button (click)="editArea()"><mat-icon>add_business</mat-icon> Nova área</button>
          <button mat-flat-button (click)="book()" [disabled]="!(areas()?.length)"><mat-icon>event_available</mat-icon> Nova reserva</button>
        }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Reservas">
            <div class="filters">
              <mat-form-field><mat-label>De</mat-label><input matInput type="date" [formControl]="from" /></mat-form-field>
              <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="to" /></mat-form-field>
              <mat-form-field class="scope"><mat-label>Área</mat-label>
                <mat-select [formControl]="area"><mat-option [value]="''">Todas</mat-option>
                  @for (a of areas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}</mat-option> }
                </mat-select></mat-form-field>
            </div>
            <section class="tc-table-card">
              <div class="table-scroll">
              <table class="lines tc-cards-mobile">
                <thead><tr><th>Data</th><th>Horário</th><th>Área</th><th>Unidade</th><th>Solicitante</th><th class="money">Taxa</th><th>Situação</th><th></th></tr></thead>
                <tbody>
                  @for (r of reservations() ?? []; track r.id) {
                    <tr>
                      <td>{{ r.data | isoDate }}</td><td>{{ r.horaInicio.slice(0, 5) }}–{{ r.horaFim.slice(0, 5) }}</td>
                      <td>{{ r.nomeArea }}</td><td>{{ r.rotuloUnidade }}</td>
                      <td>{{ r.nomeSolicitante }}{{ r.peloMorador ? ' (portal)' : '' }}</td>
                      <td class="money">{{ r.taxa | currency: 'BRL' }}</td>
                      <td><span class="status-cell"><span class="tc-status" [class.is-success]="r.situacao === 'CONFIRMED'" [class.is-pending]="r.situacao === 'PENDING'"
                        [class.is-danger]="r.situacao === 'REJECTED'" [class.is-inactive]="r.situacao === 'CANCELLED'">{{ r.situacao | label: 'reservationStatus' }}</span>
                        @if (r.motivoCancelamento || r.observacaoDecisao) { <span class="tc-muted small">{{ r.motivoCancelamento ?? r.observacaoDecisao }}</span> }</span></td>
                      <td class="acts">
                        @if (canEdit()) {
                          @if (r.situacao === 'PENDING') {
                            <button mat-icon-button class="approve" (click)="approve(r)" matTooltip="Aprovar" aria-label="Aprovar"><mat-icon>check_circle</mat-icon></button>
                            <button mat-icon-button [matMenuTriggerFor]="more" matTooltip="Mais ações" [attr.aria-label]="'Mais ações da reserva de ' + r.nomeArea + ', unidade ' + r.rotuloUnidade">
                              <mat-icon>more_vert</mat-icon></button>
                            <mat-menu #more="matMenu" xPosition="before">
                              <button mat-menu-item (click)="reject(r)"><mat-icon>do_not_disturb_on</mat-icon><span>Recusar</span></button>
                              <button mat-menu-item (click)="cancel(r)"><mat-icon>event_busy</mat-icon><span>Cancelar</span></button>
                            </mat-menu>
                          } @else if (r.situacao === 'CONFIRMED') {
                            <button mat-icon-button (click)="cancel(r)" matTooltip="Cancelar" aria-label="Cancelar"><mat-icon>event_busy</mat-icon></button>
                          }
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr class="empty-row"><td colspan="8"><tc-empty-state icon="event" title="Nenhuma reserva no período" /></td></tr>
                  }
                </tbody>
              </table>
              </div>
            </section>
          </mat-tab>
          <mat-tab label="Áreas comuns">
            <div class="areas">
              @for (a of areas() ?? []; track a.id) {
                <section class="tc-card area" [class.inactive]="!a.ativa">
                  <div class="head"><strong class="name">{{ a.nome }}</strong>
                    @if (canEdit()) { <button mat-icon-button (click)="editArea(a)" aria-label="Editar área"><mat-icon>edit</mat-icon></button> }</div>
                  <span class="tc-muted meta">{{ a.horaAbertura.slice(0, 5) }}–{{ a.horaFechamento.slice(0, 5) }} · até {{ a.duracaoMaximaMinutos / 60 }} h
                    · taxa {{ a.taxa | currency: 'BRL' }}{{ a.capacidade ? ' · ' + a.capacidade + ' pessoas' : '' }}</span>
                  <span class="small">{{ a.exigeAprovacao ? 'Exige aprovação' : 'Confirmação automática' }} · antecedência mín. {{ a.antecedenciaMinimaHoras }} h
                    {{ a.limiteMensalPorUnidade ? ' · ' + a.limiteMensalPorUnidade + '/mês por unidade' : '' }}{{ a.bloqueiaInadimplente ? ' · bloqueia inadimplentes' : '' }}</span>
                  @if (!a.ativa) { <span class="tc-status is-inactive">Inativa</span> }
                </section>
              } @empty {
                <section class="tc-card empty">
                  <tc-empty-state icon="deck" title="Nenhuma área cadastrada" description="Cadastre salão de festas, churrasqueira, quadra etc." />
                </section>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
  styles: `
    table.lines th:first-child, table.lines td:first-child { padding-left: 20px; }
    table.lines th:last-child, table.lines td:last-child { padding-right: 20px; }
    .acts { white-space: nowrap; text-align: right; width: 1%; }
    .approve { color: var(--tc-success); }
    .status-cell { display: grid; gap: 2px; justify-items: start; }
    .small { font: var(--mat-sys-body-small); }
    .empty-row td { padding: 0; }
    .empty-row:hover td { background: transparent; }
    .areas { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr)); gap: 12px; }
    .area { display: grid; gap: 6px; align-content: start; }
    .area.inactive { opacity: .6; }
    .empty { grid-column: 1 / -1; }
    .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin: -4px -8px 0 0; }
    .name { font: var(--mat-sys-title-small); font-weight: 600; min-width: 0; overflow-wrap: anywhere; }
    .meta { font: var(--mat-sys-body-small); }
    .area .tc-status { justify-self: start; }
    @media (max-width: 720px) {
      .status-cell { justify-items: end; text-align: right; }
      table.lines td.acts { justify-content: flex-end; width: auto; }
      table.lines td.acts:empty { display: none; }
      table.lines tr.empty-row { padding: 0; }
      table.lines tr.empty-row td { display: block; }
    }
  `,
})
export class ReservationPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(ReservationApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly from = new FormControl(todayIso(), { nonNullable: true });
  protected readonly to = new FormControl(addDays(todayIso(), 60), { nonNullable: true });
  protected readonly area = new FormControl('', { nonNullable: true });
  private readonly filters = toSignal(merge(this.from.valueChanges, this.to.valueChanges, this.area.valueChanges).pipe(scan((n) => n + 1, 0)), { initialValue: 0 });

  protected readonly canEdit = computed(() => this.context.can('RESERVATION_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  private readonly source = combineLatest([toObservable(this.context.id), toObservable(this.tick), toObservable(this.filters)]).pipe(filter(([c]) => !!c));
  protected readonly areas = toSignal(this.source.pipe(switchMap(([c]) => this.api.areas(c!).pipe(catchError(() => of([] as AreaComum[]))))));
  protected readonly reservations = toSignal(this.source.pipe(switchMap(([c]) =>
    this.api.list(c!, this.from.value, this.to.value, this.area.value || undefined).pipe(catchError(() => of([] as Reserva[]))))));

  editArea(a?: AreaComum): void {
    const fields: FieldDef[] = [
      { key: 'nome', label: 'Nome', required: true, maxLength: 80, value: a?.nome, span: 8 },
      { key: 'capacidade', label: 'Capacidade (pessoas)', type: 'number', value: a?.capacidade ?? null, span: 4 },
      { key: 'horaAbertura', label: 'Abre às', type: 'time', required: true, value: a?.horaAbertura.slice(0, 5) ?? '08:00', span: 4 },
      { key: 'horaFechamento', label: 'Fecha às', type: 'time', required: true, value: a?.horaFechamento.slice(0, 5) ?? '22:00', span: 4 },
      { key: 'duracaoMaximaMinutos', label: 'Duração máx. (min)', type: 'number', value: a?.duracaoMaximaMinutos ?? 720, span: 4 },
      { key: 'taxa', label: 'Taxa de uso (R$)', type: 'number', value: a?.taxa ?? 0, span: 4, hint: 'Gera cobrança na unidade ao confirmar' },
      { key: 'antecedenciaMinimaHoras', label: 'Antecedência mínima (h)', type: 'number', value: a?.antecedenciaMinimaHoras ?? 24, span: 4 },
      { key: 'antecedenciaMaximaDias', label: 'Antecedência máxima (dias)', type: 'number', value: a?.antecedenciaMaximaDias ?? 90, span: 4 },
      { key: 'limiteMensalPorUnidade', label: 'Limite por unidade/mês', type: 'number', value: a?.limiteMensalPorUnidade ?? null, span: 6 },
      { key: 'exigeAprovacao', label: 'Reservas do portal exigem aprovação', type: 'checkbox', value: a?.exigeAprovacao ?? false },
      { key: 'bloqueiaInadimplente', label: 'Bloquear unidades com cobranças vencidas', type: 'checkbox', value: a?.bloqueiaInadimplente ?? false },
      { key: 'regras', label: 'Regras de uso (exibidas ao morador)', type: 'textarea', maxLength: 2000, value: a?.regras ?? null },
    ];
    if (a) fields.push({ key: 'ativa', label: 'Área ativa', type: 'checkbox', value: a.ativa });
    openForm(this.dialog, {
      title: a ? 'Editar área comum' : 'Nova área comum',
      fields,
      submit: (v) => this.api.saveArea(this.context.id()!, v, a?.id),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notify.success('Área salva.');
      this.tick.update((t) => t + 1);
    });
  }

  book(): void {
    const c = this.context.id()!;
    this.structure.units(c, { page: 0, size: 500, active: 'true' }).pipe(map((p) => p.conteudo)).subscribe((units) =>
      openForm(this.dialog, {
        title: 'Nova reserva',
        intro: 'Reservas lançadas pela administração são confirmadas na hora (a antecedência mínima não se aplica).',
        fields: [
          { key: 'idArea', label: 'Área', type: 'select', required: true, span: 6,
            options: (this.areas() ?? []).filter((a) => a.ativa).map((a) => ({ value: a.id, label: a.nome })) },
          { key: 'idUnidade', label: 'Unidade', type: 'select', required: true, span: 6, options: units.map((u) => ({ value: u.id, label: u.rotulo })) },
          { key: 'data', label: 'Data', type: 'date', required: true, value: todayIso(), span: 4 },
          { key: 'horaInicio', label: 'Início', type: 'time', required: true, span: 4 },
          { key: 'horaFim', label: 'Fim', type: 'time', required: true, span: 4 },
          { key: 'convidados', label: 'Convidados', type: 'number', span: 4 },
          { key: 'observacoes', label: 'Observações', maxLength: 500, span: 8 },
        ],
        submit: (v) => this.api.create(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notify.success('Reserva registrada.');
        this.tick.update((t) => t + 1);
      }));
  }

  approve(r: Reserva): void {
    this.run(this.api.approve(this.context.id()!, r.id, null), 'Reserva aprovada.');
  }

  reject(r: Reserva): void {
    askReason(this.dialog, 'Recusar reserva', `${r.nomeArea} · unidade ${r.rotuloUnidade}. O morador será notificado.`, 'Recusar')
      .subscribe((reason) => this.run(this.api.reject(this.context.id()!, r.id, reason), 'Reserva recusada.'));
  }

  cancel(r: Reserva): void {
    askReason(this.dialog, 'Cancelar reserva', 'A taxa de uso ainda não paga também será cancelada.', 'Cancelar reserva')
      .subscribe((reason) => this.run(this.api.cancel(this.context.id()!, r.id, reason), 'Reserva cancelada.'));
  }

  private run(call: ReturnType<ReservationApi['approve']>, message: string): void {
    call.subscribe({
      next: () => {
        this.notify.success(message);
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}

function addDays(iso: string, days: number): string {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

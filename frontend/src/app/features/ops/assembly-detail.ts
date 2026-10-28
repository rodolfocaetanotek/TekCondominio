import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { askConfirm, askReason } from '../../shared/ui/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { StructureService } from '../structure/structure.service';
import { openAssemblyForm } from './assembly-form';
import { Attachments } from './attachments';
import { AgendaItem, AssemblyDetail, OpsApi } from './ops-api';

@Component({
  selector: 'tc-assembly-detail',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressBarModule, PageHeader, EmptyState, LabelPipe, IsoDatePipe, Attachments],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/assembleias" class="back"><mat-icon>arrow_back</mat-icon> Assembleias</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir" [description]="message" /></section>
      } @else if (a(); as d) {
        <tc-page-header [eyebrow]="'Assembleia ' + (d.kind | label: 'assemblyKind')" [title]="d.title"
          [subtitle]="(d.scheduledAt | date: 'dd/MM/yyyy HH:mm') + ' · ' + d.location + ' · ' + (d.voteWeight | label: 'voteWeight')">
          <span class="tc-status big" [class.is-success]="d.status === 'CLOSED'" [class.is-pending]="d.status === 'CALLED'" [class.is-info]="d.status === 'IN_PROGRESS'"
            [class.is-inactive]="d.status === 'CANCELLED' || d.status === 'DRAFT'">{{ d.status | label: 'assemblyStatus' }}</span>
        </tc-page-header>

        @if (canEdit()) {
          <div class="tc-actions bar">
            @switch (d.status) {
              @case ('DRAFT') {
                <button mat-stroked-button (click)="edit(d)"><mat-icon>edit</mat-icon> Editar</button>
                <button mat-flat-button (click)="act('call', 'Convocar assembleia', 'Será publicado um comunicado de convocação com a pauta para todos os condôminos (notificação e e-mail).')"><mat-icon>campaign</mat-icon> Convocar</button>
              }
              @case ('CALLED') {
                <button mat-stroked-button (click)="edit(d)"><mat-icon>edit</mat-icon> Editar</button>
                <button mat-flat-button (click)="act('start', 'Iniciar assembleia', 'Abre o registro de presença e votação.')"><mat-icon>play_arrow</mat-icon> Iniciar</button>
              }
              @case ('IN_PROGRESS') {
                <button mat-flat-button (click)="act('close', 'Encerrar assembleia', 'Após encerrada, presença e votos não podem ser alterados.')"><mat-icon>stop</mat-icon> Encerrar</button>
              }
            }
            @if (d.status !== 'CLOSED' && d.status !== 'CANCELLED') { <button mat-button (click)="cancel()">Cancelar assembleia</button> }
          </div>
        }

        <section class="tc-kpis" aria-label="Quórum">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">door_front</mat-icon></span>
            <span class="label">Unidades presentes</span><span class="value">{{ d.quorum.unitsPresent }} / {{ d.quorum.totalUnits }}</span>
          </div>
          <div class="tc-kpi tc-card tone-info">
            <span class="icon"><mat-icon aria-hidden="true">pie_chart</mat-icon></span>
            <span class="label">Frações presentes</span><span class="value">{{ d.quorum.fractionPresent | number: '1.2-4' }}%</span>
          </div>
          @if (d.cancelReason) {
            <div class="tc-kpi tc-card tone-danger reason">
              <span class="icon"><mat-icon aria-hidden="true">event_busy</mat-icon></span>
              <span class="label">Cancelamento</span><span class="value">{{ d.cancelReason }}</span>
            </div>
          }
        </section>

        <div class="layout">
          <section class="tc-card">
            <h3 class="tc-section-title first">Pauta e votação</h3>
            @for (i of d.agenda; track i.id) {
              <div class="item">
                <strong class="item-title">{{ i.position }}. {{ i.title }}</strong>
                @if (i.voting) {
                  <div class="tally">
                    <span class="vote yes">Sim <b>{{ i.tally.yes | number: '1.0-4' }}</b></span><span class="vote no">Não <b>{{ i.tally.no | number: '1.0-4' }}</b></span>
                    <span class="vote">Abst. <b>{{ i.tally.abstain | number: '1.0-4' }}</b></span><span class="tc-muted">{{ i.tally.votes }} voto(s)</span>
                    @if (canEdit() && d.status === 'IN_PROGRESS') { <button mat-button (click)="vote(d, i)"><mat-icon>how_to_vote</mat-icon> Registrar voto</button> }
                  </div>
                } @else { <small class="tc-chip-soft info"> informativo</small> }
                @if (i.deliberation) { <p class="delib">{{ i.deliberation }}{{ i.responsible ? ' — resp.: ' + i.responsible : '' }}{{ i.deadline ? ' (prazo ' + (i.deadline | isoDate) + ')' : '' }}</p> }
                @if (canEdit() && (d.status === 'IN_PROGRESS' || d.status === 'CLOSED')) { <button mat-button (click)="deliberate(i)">Deliberação</button> }
              </div>
            }
          </section>
          <section class="tc-card">
            <div class="head"><h3 class="tc-section-title">Lista de presença</h3>
              @if (canEdit() && d.status === 'IN_PROGRESS') { <button mat-button (click)="attend()"><mat-icon>person_add</mat-icon> Registrar</button> }</div>
            @for (p of d.attendance; track p.id) {
              <div class="att"><span><strong>{{ p.unitLabel }}</strong> · {{ p.attendeeName }}{{ p.byProxy ? ' (procurador: ' + p.proxyName + ')' : '' }}</span>
                @if (canEdit() && d.status === 'IN_PROGRESS') { <button mat-icon-button (click)="removeAttendance(p.id)" aria-label="Remover presença"><mat-icon>close</mat-icon></button> }</div>
            } @empty { <p class="tc-muted">Nenhuma presença registrada.</p> }
          </section>
        </div>

        @if (d.status === 'IN_PROGRESS' || d.status === 'CLOSED') {
          <section class="tc-card minutes">
            <div class="head"><h3 class="tc-section-title">Ata</h3>
              @if (canEdit()) { <button mat-button (click)="draft()"><mat-icon>auto_awesome</mat-icon> Gerar rascunho a partir dos dados</button> }</div>
            <mat-form-field class="full"><mat-label>Texto da ata</mat-label><textarea matInput rows="14" [formControl]="minutes" [readonly]="!canEdit()"></textarea></mat-form-field>
            @if (canEdit()) { <div class="tc-actions"><button mat-flat-button (click)="saveMinutes()" [disabled]="minutes.pristine">Salvar ata</button></div> }
          </section>
        }
        <tc-attachments [condominiumId]="context.id()!" entityType="Assembly" [entityId]="d.id" category="MINUTES" [canEdit]="canEdit()" />
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; } .back { margin: -8px 0 8px -12px; }
    .bar { justify-content: flex-start; margin-bottom: 16px; }
    .reason .value { font-size: 15px; font-weight: 600; letter-spacing: 0; overflow-wrap: anywhere; }
    .layout { display: grid; grid-template-columns: minmax(0, 3fr) minmax(260px, 2fr); gap: 16px; align-items: start; margin-bottom: 16px; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .tc-section-title.first { margin-top: 0; }
    .item { display: grid; gap: 4px; justify-items: start; padding: 12px 0; border-bottom: 1px solid var(--tc-border); }
    .item:last-child { border-bottom: 0; padding-bottom: 0; }
    .item-title { overflow-wrap: anywhere; }
    .info { margin-left: 4px; }
    .tally { display: flex; gap: 8px 14px; align-items: center; flex-wrap: wrap; font-variant-numeric: tabular-nums; }
    .vote b { font-weight: 650; }
    .vote.yes b { color: var(--tc-success); }
    .vote.no b { color: var(--tc-danger); }
    .delib { background: var(--tc-surface-muted); border: 1px solid var(--tc-border); padding: 8px 12px; border-radius: var(--tc-radius-sm); margin: 4px 0; justify-self: stretch; white-space: pre-wrap; }
    .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
    .head h3 { margin: 0; }
    .att { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--tc-border); }
    .att:last-child { border-bottom: 0; }
    .att span { min-width: 0; overflow-wrap: anywhere; }
    .minutes { margin-bottom: 16px; } .full { width: 100%; margin-top: 8px; }
  `,
})
export class AssemblyDetailPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  readonly id = input.required<string>();
  private readonly tick = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly minutes = new FormControl('', { nonNullable: true });
  protected readonly canEdit = computed(() => this.context.can('ASSEMBLY_EDIT'));
  protected readonly a = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, id]) => this.api.assembly(c!, id).pipe(
      map((d) => { if (this.minutes.pristine) this.minutes.reset(d.minutes ?? ''); return d; }),
      catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); })))));

  private c(): string {
    return this.context.id()!;
  }

  act(action: 'call' | 'start' | 'close', title: string, message: string): void {
    askConfirm(this.dialog, title, message, title.split(' ')[0]).pipe(switchMap(() => this.api.assemblyAction(this.c(), this.id(), action)))
      .subscribe({ next: () => this.done('Assembleia atualizada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  cancel(): void {
    askReason(this.dialog, 'Cancelar assembleia', 'Informe o motivo do cancelamento.', 'Cancelar assembleia')
      .pipe(switchMap((r) => this.api.cancelAssembly(this.c(), this.id(), r)))
      .subscribe({ next: () => this.done('Assembleia cancelada.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  edit(d: AssemblyDetail): void {
    openAssemblyForm(this.dialog, this.api, this.c(), d).pipe(filter((r) => !!r)).subscribe(() => this.done('Assembleia atualizada.'));
  }

  attend(): void {
    this.units().subscribe((units) => openForm(this.dialog, {
      title: 'Registrar presença',
      fields: [
        { key: 'unitId', label: 'Unidade', type: 'select', required: true, options: units },
        { key: 'attendeeName', label: 'Nome de quem está presente', required: true, maxLength: 150 },
        { key: 'byProxy', label: 'Representado por procuração', type: 'checkbox' },
        { key: 'proxyName', label: 'Nome do procurador', maxLength: 150 },
      ],
      submit: (v) => this.api.attend(this.c(), this.id(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.done('Presença registrada.')));
  }

  removeAttendance(attendanceId: string): void {
    this.api.removeAttendance(this.c(), this.id(), attendanceId)
      .subscribe({ next: () => this.done('Presença removida.'), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  vote(d: AssemblyDetail, item: AgendaItem): void {
    openForm(this.dialog, {
      title: `Voto — ${item.position}. ${item.title}`,
      intro: 'Somente unidades presentes votam; cada unidade vota uma vez por item.',
      fields: [
        { key: 'unitId', label: 'Unidade', type: 'select', required: true, options: d.attendance.map((p) => ({ value: p.unitId, label: p.unitLabel + ' · ' + p.attendeeName })) },
        { key: 'choice', label: 'Voto', type: 'select', required: true, value: 'YES', options: [{ value: 'YES', label: 'Sim' }, { value: 'NO', label: 'Não' }, { value: 'ABSTAIN', label: 'Abstenção' }] },
      ],
      submitLabel: 'Registrar voto',
      submit: (v) => this.api.vote(this.c(), this.id(), { ...v, agendaItemId: item.id }),
    }, '460px').pipe(filter((r) => !!r)).subscribe(() => this.done('Voto registrado.'));
  }

  deliberate(item: AgendaItem): void {
    openForm(this.dialog, {
      title: `Deliberação — ${item.position}. ${item.title}`,
      fields: [
        { key: 'deliberation', label: 'Deliberação', type: 'textarea', value: item.deliberation, maxLength: 2000 },
        { key: 'responsible', label: 'Responsável pela execução', value: item.responsible, span: 6, maxLength: 150 },
        { key: 'deadline', label: 'Prazo', type: 'date', value: item.deadline, span: 6 },
      ],
      submit: (v) => this.api.deliberate(this.c(), this.id(), item.id, v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.done('Deliberação registrada.'));
  }

  draft(): void {
    this.api.minutesDraft(this.c(), this.id()).subscribe((r) => {
      this.minutes.setValue(r.minutes);
      this.minutes.markAsDirty();
    });
  }

  saveMinutes(): void {
    this.api.saveMinutes(this.c(), this.id(), this.minutes.value).subscribe({
      next: () => { this.minutes.markAsPristine(); this.done('Ata salva.'); },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  private units(): Observable<{ value: string; label: string }[]> {
    return this.structure.units(this.c(), { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.conteudo.map((u) => ({ value: u.id, label: u.label }))));
  }

  private done(m: string): void {
    this.notify.success(m);
    this.tick.update((t) => t + 1);
  }
}

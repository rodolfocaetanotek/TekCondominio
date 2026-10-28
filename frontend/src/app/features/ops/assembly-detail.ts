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
          <span class="tc-status big" [class.is-active]="d.status === 'CLOSED'" [class.is-pending]="d.status === 'CALLED' || d.status === 'IN_PROGRESS'">{{ d.status | label: 'assemblyStatus' }}</span>
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

        <section class="quorum tc-card">
          <div><span class="tc-muted">Unidades presentes</span><strong>{{ d.quorum.unitsPresent }} / {{ d.quorum.totalUnits }}</strong></div>
          <div><span class="tc-muted">Frações presentes</span><strong>{{ d.quorum.fractionPresent | number: '1.2-4' }}%</strong></div>
          @if (d.cancelReason) { <div><span class="tc-muted">Cancelamento</span><strong>{{ d.cancelReason }}</strong></div> }
        </section>

        <div class="layout">
          <section class="tc-card">
            <h3 class="tc-section-title">Pauta e votação</h3>
            @for (i of d.agenda; track i.id) {
              <div class="item">
                <strong>{{ i.position }}. {{ i.title }}</strong>
                @if (i.voting) {
                  <div class="tally">
                    <span>Sim <b>{{ i.tally.yes | number: '1.0-4' }}</b></span><span>Não <b>{{ i.tally.no | number: '1.0-4' }}</b></span>
                    <span>Abst. <b>{{ i.tally.abstain | number: '1.0-4' }}</b></span><span class="tc-muted">{{ i.tally.votes }} voto(s)</span>
                    @if (canEdit() && d.status === 'IN_PROGRESS') { <button mat-button (click)="vote(d, i)"><mat-icon>how_to_vote</mat-icon> Registrar voto</button> }
                  </div>
                } @else { <small class="tc-muted"> informativo</small> }
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
    .quorum { display: flex; gap: 32px; flex-wrap: wrap; margin-bottom: 16px; } .quorum div { display: grid; }
    .layout { display: grid; grid-template-columns: minmax(0, 3fr) minmax(260px, 2fr); gap: 16px; align-items: start; margin-bottom: 16px; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .item { padding: 10px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .tally { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; margin-top: 4px; }
    .delib { background: var(--mat-sys-surface-container-low); padding: 8px; border-radius: 8px; margin: 6px 0; }
    .head { display: flex; justify-content: space-between; align-items: center; } .head h3 { margin: 0; }
    .att { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
    .minutes { margin-bottom: 16px; } .full { width: 100%; margin-top: 8px; }
    .tc-status.big { font: var(--mat-sys-label-large); padding: 4px 14px; align-self: center; }
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
    return this.structure.units(this.c(), { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.content.map((u) => ({ value: u.id, label: u.label }))));
  }

  private done(m: string): void {
    this.notify.success(m);
    this.tick.update((t) => t + 1);
  }
}

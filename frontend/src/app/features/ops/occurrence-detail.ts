import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Attachments } from './attachments';
import { OccurrenceDetail, OpsApi } from './ops-api';

@Component({
  selector: 'tc-occurrence-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatCheckboxModule,
    MatProgressBarModule, PageHeader, EmptyState, LabelPipe, IsoDatePipe, Attachments],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/ocorrencias" class="back"><mat-icon>arrow_back</mat-icon> Ocorrências</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir" [description]="message" /></section>
      } @else if (o(); as d) {
        <tc-page-header [eyebrow]="'Ocorrência nº ' + d.summary.number" [title]="d.summary.title"
          [subtitle]="(d.summary.category | label: 'occurrenceCategory') + ' · prioridade ' + (d.summary.priority | label: 'priority') + (d.summary.unitLabel ? ' · Unid. ' + d.summary.unitLabel : '')">
          <span class="tc-status big" [class.is-active]="d.summary.status === 'RESOLVED'" [class.is-pending]="d.summary.status === 'OPEN'">{{ d.summary.status | label: 'occurrenceStatus' }}</span>
        </tc-page-header>
        <div class="layout">
          <div class="main">
            <section class="tc-card">
              <p class="desc">{{ d.description }}</p>
              <p class="tc-muted small">Aberta por {{ d.summary.reporterName }} em {{ d.summary.createdAt | date: 'dd/MM/yyyy HH:mm' }}
                · responsável: {{ d.summary.assigneeName ?? 'não definido' }} · prazo: {{ d.summary.dueDate ? (d.summary.dueDate | isoDate) : '—' }}</p>
              @if (d.resolution) { <p class="resolution"><strong>Solução:</strong> {{ d.resolution }}</p> }
              @if (canEdit()) {
                <div class="tc-actions">
                  <button mat-button (click)="triage(d)">Triagem</button>
                  @for (s of nextStatuses(d); track s.value) {
                    <button mat-stroked-button (click)="changeStatus(s.value)">{{ s.label }}</button>
                  }
                </div>
              }
            </section>
            <section class="tc-card">
              <h3 class="tc-section-title">Andamento</h3>
              @for (i of d.interactions; track i.id) {
                <div class="msg" [class.internal]="i.internal">
                  <strong>{{ i.authorName }}</strong> <small class="tc-muted">{{ i.createdAt | date: 'dd/MM/yy HH:mm' }}
                    {{ i.statusChange ? ' · ' + (i.statusChange | label: 'occurrenceStatus') : '' }}{{ i.internal ? ' · interna' : '' }}</small>
                  <p>{{ i.message }}</p>
                </div>
              } @empty { <p class="tc-muted">Sem interações.</p> }
              @if (canEdit()) {
                <mat-form-field class="full"><mat-label>Nova mensagem</mat-label><textarea matInput rows="3" [formControl]="message" maxlength="2000"></textarea></mat-form-field>
                <div class="tc-actions">
                  <mat-checkbox [formControl]="internal">Interna (não aparece para o morador)</mat-checkbox>
                  <button mat-flat-button (click)="send()" [disabled]="message.invalid">Enviar</button>
                </div>
              }
            </section>
          </div>
          <tc-attachments [condominiumId]="context.id()!" entityType="Occurrence" [entityId]="d.summary.id" category="OCCURRENCE" [canEdit]="canEdit()" />
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; } .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .main { display: grid; gap: 16px; }
    .desc { white-space: pre-wrap; margin-top: 0; }
    .small { font: var(--mat-sys-body-small); }
    .resolution { background: var(--tc-success-container); padding: 10px 12px; border-radius: 10px; }
    .msg { border-left: 3px solid var(--mat-sys-primary); padding: 4px 12px; margin-bottom: 12px; }
    .msg.internal { border-color: var(--mat-sys-tertiary); background: var(--mat-sys-surface-container-low); }
    .msg p { margin: 4px 0 0; white-space: pre-wrap; }
    .full { width: 100%; }
    .tc-status.big { font: var(--mat-sys-label-large); padding: 4px 14px; align-self: center; }
  `,
})
export class OccurrenceDetailPage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  readonly id = input.required<string>();
  private readonly tick = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly message = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly internal = new FormControl(false, { nonNullable: true });
  protected readonly canEdit = computed(() => this.context.can('OCCURRENCE_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly o = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, id]) => this.api.occurrence(c!, id).pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); })))));

  nextStatuses(d: OccurrenceDetail): { value: string; label: string }[] {
    const s = d.summary.status;
    if (s === 'RESOLVED' || s === 'CANCELLED') return [{ value: 'OPEN', label: 'Reabrir' }];
    return [
      ...(s !== 'IN_PROGRESS' ? [{ value: 'IN_PROGRESS', label: 'Em atendimento' }] : []),
      ...(s !== 'WAITING' ? [{ value: 'WAITING', label: 'Aguardando' }] : []),
      { value: 'RESOLVED', label: 'Resolver' },
      { value: 'CANCELLED', label: 'Cancelar' },
    ];
  }

  changeStatus(status: string): void {
    const needsText = status === 'RESOLVED' || status === 'CANCELLED';
    openForm(this.dialog, {
      title: status === 'RESOLVED' ? 'Resolver ocorrência' : 'Alterar situação',
      fields: [{ key: 'message', label: status === 'RESOLVED' ? 'Solução aplicada' : 'Mensagem', type: 'textarea', required: needsText, maxLength: 2000 }],
      submit: (v) => this.api.occurrenceStatus(this.context.id()!, this.id(), { status, message: v['message'] }),
    }, '480px').pipe(filter((r) => !!r)).subscribe(() => this.done('Situação atualizada.'));
  }

  triage(d: OccurrenceDetail): void {
    openForm(this.dialog, {
      title: 'Triagem',
      fields: [
        { key: 'category', label: 'Categoria', type: 'select', required: true, value: d.summary.category, span: 6, options: options('occurrenceCategory') },
        { key: 'priority', label: 'Prioridade', type: 'select', required: true, value: d.summary.priority, span: 6, options: options('priority') },
        { key: 'assigneeName', label: 'Responsável', value: d.summary.assigneeName, span: 6, maxLength: 150 },
        { key: 'dueDate', label: 'Prazo', type: 'date', value: d.summary.dueDate, span: 6 },
      ],
      submit: (v) => this.api.updateOccurrence(this.context.id()!, this.id(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.done('Triagem salva.'));
  }

  send(): void {
    this.api.comment(this.context.id()!, this.id(), this.message.value.trim(), this.internal.value).subscribe({
      next: () => { this.message.reset(); this.done('Mensagem registrada.'); },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  private done(m: string): void {
    this.notify.success(m);
    this.tick.update((t) => t + 1);
  }
}

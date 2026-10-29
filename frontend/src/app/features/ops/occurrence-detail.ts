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
import { DetalheOcorrencia, OpsApi } from './ops-api';

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
        <tc-page-header [eyebrow]="'Ocorrência nº ' + d.resumo.numero" [title]="d.resumo.titulo"
          [subtitle]="(d.resumo.categoria | label: 'occurrenceCategory') + ' · prioridade ' + (d.resumo.prioridade | label: 'priority') + (d.resumo.rotuloUnidade ? ' · Unid. ' + d.resumo.rotuloUnidade : '')">
          <span class="tc-status big" [class.is-success]="d.resumo.situacao === 'RESOLVED'"
            [class.is-pending]="d.resumo.situacao === 'OPEN' || d.resumo.situacao === 'WAITING'" [class.is-info]="d.resumo.situacao === 'IN_PROGRESS'"
            [class.is-inactive]="d.resumo.situacao === 'CANCELLED'">{{ d.resumo.situacao | label: 'occurrenceStatus' }}</span>
        </tc-page-header>
        <div class="layout">
          <div class="main">
            <section class="tc-card">
              <p class="desc">{{ d.descricao }}</p>
              <p class="tc-muted small">Aberta por {{ d.resumo.nomeSolicitante }} em {{ d.resumo.criadoEm | date: 'dd/MM/yyyy HH:mm' }}
                · responsável: {{ d.resumo.nomeResponsavel ?? 'não definido' }} · prazo: {{ d.resumo.prazo ? (d.resumo.prazo | isoDate) : '—' }}</p>
              @if (d.solucao) { <p class="resolution"><strong>Solução:</strong> {{ d.solucao }}</p> }
              @if (canEdit()) {
                <div class="tc-actions status-acts">
                  <button mat-button (click)="triage(d)">Triagem</button>
                  @for (s of nextStatuses(d); track s.value) {
                    <button mat-stroked-button (click)="changeStatus(s.value)">{{ s.label }}</button>
                  }
                </div>
              }
            </section>
            <section class="tc-card">
              <h3 class="tc-section-title first">Andamento</h3>
              @for (i of d.interacoes; track i.id) {
                <div class="msg" [class.internal]="i.interna">
                  <strong>{{ i.nomeAutor }}</strong> <small class="tc-muted">{{ i.criadoEm | date: 'dd/MM/yy HH:mm' }}
                    {{ i.situacaoAlterada ? ' · ' + (i.situacaoAlterada | label: 'occurrenceStatus') : '' }}{{ i.interna ? ' · interna' : '' }}</small>
                  <p>{{ i.mensagem }}</p>
                </div>
              } @empty { <p class="tc-muted">Sem interações.</p> }
              @if (canEdit()) {
                <mat-form-field class="full"><mat-label>Nova mensagem</mat-label><textarea matInput rows="3" [formControl]="message" maxlength="2000"></textarea></mat-form-field>
                <div class="tc-actions send">
                  <mat-checkbox [formControl]="internal">Interna (não aparece para o morador)</mat-checkbox>
                  <button mat-flat-button (click)="send()" [disabled]="message.invalid">Enviar</button>
                </div>
              }
            </section>
          </div>
          <tc-attachments [condominiumId]="context.id()!" entityType="Occurrence" [entityId]="d.resumo.id" category="OCCURRENCE" [canEdit]="canEdit()" />
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; } .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .main { display: grid; gap: 16px; min-width: 0; }
    .desc { white-space: pre-wrap; margin-top: 0; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); }
    .resolution { background: var(--tc-success-container); border-left: 3px solid var(--tc-success); padding: 10px 12px; border-radius: var(--tc-radius-sm); white-space: pre-wrap; }
    .status-acts { justify-content: flex-start; gap: 8px; padding-top: 12px; border-top: 1px solid var(--tc-border); }
    .tc-section-title.first { margin-top: 0; }
    .msg { border-left: 3px solid var(--mat-sys-primary); padding: 6px 12px; margin-bottom: 12px; border-radius: 0 var(--tc-radius-sm) var(--tc-radius-sm) 0; }
    .msg.internal { border-color: var(--tc-accent); background: var(--tc-accent-soft); }
    .msg p { margin: 4px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .full { width: 100%; margin-top: 4px; }
    .send { justify-content: space-between; align-items: center; }
    @media (max-width: 600px) {
      .status-acts > button { flex: 1 1 auto; }
      .send > button { flex: 1 1 100%; }
    }
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
  protected readonly canEdit = computed(() => this.context.can('OCCURRENCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly o = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, id]) => this.api.occurrence(c!, id).pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); })))));

  nextStatuses(d: DetalheOcorrencia): { value: string; label: string }[] {
    const s = d.resumo.situacao;
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
      fields: [{ key: 'mensagem', label: status === 'RESOLVED' ? 'Solução aplicada' : 'Mensagem', type: 'textarea', required: needsText, maxLength: 2000 }],
      submit: (v) => this.api.occurrenceStatus(this.context.id()!, this.id(), { situacao: status, mensagem: v['mensagem'] }),
    }, '480px').pipe(filter((r) => !!r)).subscribe(() => this.done('Situação atualizada.'));
  }

  triage(d: DetalheOcorrencia): void {
    openForm(this.dialog, {
      title: 'Triagem',
      fields: [
        { key: 'categoria', label: 'Categoria', type: 'select', required: true, value: d.resumo.categoria, span: 6, options: options('occurrenceCategory') },
        { key: 'prioridade', label: 'Prioridade', type: 'select', required: true, value: d.resumo.prioridade, span: 6, options: options('priority') },
        { key: 'nomeResponsavel', label: 'Responsável', value: d.resumo.nomeResponsavel, span: 6, maxLength: 150 },
        { key: 'prazo', label: 'Prazo', type: 'date', value: d.resumo.prazo, span: 6 },
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

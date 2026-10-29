import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, filter, map, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { PageHeader } from '../../shared/ui/page-header';
import { StructureService } from '../structure/structure.service';
import { DetalheOcorrencia, OpsApi, ResumoOcorrencia } from './ops-api';

@Component({
  selector: 'tc-occurrence-list',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, PageHeader, EmptyState, CondominiumRequired, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Manutenção" title="Ocorrências" [subtitle]="context.selected()?.nome">
        @if (canEdit()) { <button mat-flat-button (click)="create()"><mat-icon>add_alert</mat-icon> Registrar ocorrência</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (stats(); as s) {
          <section class="tc-kpis" aria-label="Indicadores de ocorrências">
            <div class="tc-kpi tc-card tone-warning">
              <span class="icon"><mat-icon aria-hidden="true">pending_actions</mat-icon></span>
              <span class="label">Pendentes</span><span class="value">{{ s.abertas }}</span>
            </div>
            <div class="tc-kpi tc-card" [class.tone-danger]="s.atrasadas > 0" [class.tone-success]="s.atrasadas === 0">
              <span class="icon"><mat-icon aria-hidden="true">{{ s.atrasadas > 0 ? 'alarm' : 'verified' }}</mat-icon></span>
              <span class="label">Fora do prazo</span><span class="value">{{ s.atrasadas }}</span>
            </div>
            <div class="tc-kpi tc-card tone-success">
              <span class="icon"><mat-icon aria-hidden="true">task_alt</mat-icon></span>
              <span class="label">Resolvidas</span><span class="value">{{ s.porSituacao['RESOLVED'] ?? 0 }}</span>
            </div>
            <div class="tc-kpi tc-card tone-info">
              <span class="icon"><mat-icon aria-hidden="true">timer</mat-icon></span>
              <span class="label">Tempo médio de solução</span>
              <span class="value">{{ s.mediaHorasSolucao !== null && s.mediaHorasSolucao !== undefined ? (s.mediaHorasSolucao | number: '1.0-1') + ' h' : '—' }}</span>
            </div>
          </section>
        }
        <div class="filters">
          <mat-form-field class="search"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon><input matInput [formControl]="search" /></mat-form-field>
          <mat-form-field><mat-label>Situação</mat-label>
            <mat-select [formControl]="status">
              <mat-option value="pending">Pendentes</mat-option>
              <mat-option value="RESOLVED">Resolvidas</mat-option>
              <mat-option value="CANCELLED">Canceladas</mat-option>
              <mat-option value="">Todas</mat-option>
            </mat-select></mat-form-field>
        </div>
        <section class="tc-table-card" aria-live="polite" [attr.aria-busy]="page() === undefined">
          <mat-progress-bar [mode]="page() === undefined ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="page() !== undefined" />
          @if (page() === undefined) {
            <span class="sr-only">Carregando ocorrências…</span>
          } @else if ((page()?.conteudo ?? []).length === 0) {
            <tc-empty-state icon="build" title="Nenhuma ocorrência" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page()?.conteudo ?? []">
                <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Nº</th><td mat-cell *matCellDef="let o" class="mono">{{ o.numero }}</td></ng-container>
                <ng-container matColumnDef="title"><th mat-header-cell *matHeaderCellDef>Ocorrência</th>
                  <td mat-cell *matCellDef="let o"><span class="title-cell"><strong>{{ o.titulo }}</strong>
                    <span class="tc-muted sub">{{ o.categoria | label: 'occurrenceCategory' }}{{ o.rotuloUnidade ? ' · Unid. ' + o.rotuloUnidade : '' }} · {{ o.nomeSolicitante }} · {{ o.criadoEm | date: 'dd/MM/yy HH:mm' }}</span></span></td></ng-container>
                <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Prioridade</th>
                  <td mat-cell *matCellDef="let o"><span [class.urgent]="o.prioridade === 'URGENT' || o.prioridade === 'HIGH'">{{ o.prioridade | label: 'priority' }}</span></td></ng-container>
                <ng-container matColumnDef="assignee"><th mat-header-cell *matHeaderCellDef>Responsável / prazo</th>
                  <td mat-cell *matCellDef="let o"><span class="two-line">{{ o.nomeResponsavel ?? '—' }}<span class="sub" [class.negative]="o.atrasada">{{ o.prazo ? 'até ' + o.prazo.slice(8, 10) + '/' + o.prazo.slice(5, 7) : '' }}</span></span></td></ng-container>
                <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let o"><span class="tc-status" [class.is-success]="o.situacao === 'RESOLVED'"
                    [class.is-danger]="o.atrasada && o.situacao !== 'RESOLVED' && o.situacao !== 'CANCELLED'"
                    [class.is-pending]="!o.atrasada && (o.situacao === 'OPEN' || o.situacao === 'WAITING')" [class.is-info]="!o.atrasada && o.situacao === 'IN_PROGRESS'"
                    [class.is-inactive]="o.situacao === 'CANCELLED'">{{ o.situacao | label: 'occurrenceStatus' }}</span></td></ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ocorrência nº ' + row.numero" (click)="open(row)" (keydown.enter)="open(row)"></tr>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .title-cell, .two-line { display: grid; gap: 2px; padding-block: 6px; }
    .sub { font: var(--mat-sys-body-small); }
    .negative { color: var(--tc-danger); font-weight: 600; }
    .urgent { color: var(--tc-danger); font-weight: 600; }
    @media (max-width: 720px) {
      .title-cell, .two-line { justify-items: end; padding-block: 0; }
    }
  `,
})
export class OccurrenceList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  protected readonly columns = ['number', 'title', 'priority', 'assignee', 'status'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl('pending', { nonNullable: true });
  private readonly params = signal({ search: '', status: 'pending', tick: 0 });
  protected readonly canEdit = computed(() => this.context.can('OCCURRENCE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  private readonly source = combineLatest([toObservable(this.context.id), toObservable(this.params)]).pipe(filter(([c]) => !!c));

  protected readonly page = toSignal(this.source.pipe(switchMap(([c, p]) => this.api.occurrences(c!, {
    busca: p.search, tamanho: '100', ...(p.status === 'pending' ? { situacao: ['OPEN', 'IN_PROGRESS', 'WAITING'] } : p.status ? { situacao: p.status } : {}),
  }).pipe(catchError(() => of(null))))));
  protected readonly stats = toSignal(this.source.pipe(switchMap(([c]) => this.api.occurrenceStats(c!).pipe(catchError(() => of(null))))));

  constructor() {
    this.search.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((search) => this.params.update((p) => ({ ...p, search })));
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe((status) => this.params.update((p) => ({ ...p, status })));
  }

  open(o: ResumoOcorrencia): void {
    void this.router.navigate(['/ocorrencias', o.id]);
  }

  create(): void {
    const c = this.context.id()!;
    this.structure.units(c, { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.conteudo)).subscribe((units) =>
      openForm<DetalheOcorrencia>(this.dialog, {
        title: 'Registrar ocorrência',
        fields: [
          { key: 'titulo', label: 'Título', required: true, maxLength: 150 },
          { key: 'descricao', label: 'Descrição', type: 'textarea', required: true, maxLength: 4000 },
          { key: 'categoria', label: 'Categoria', type: 'select', required: true, value: 'MAINTENANCE', span: 6, options: options('occurrenceCategory') },
          { key: 'prioridade', label: 'Prioridade', type: 'select', value: 'MEDIUM', span: 6, options: options('priority') },
          { key: 'idUnidade', label: 'Unidade relacionada', type: 'select', value: null,
            options: [{ value: null, label: '— (área comum)' }, ...units.map((u) => ({ value: u.id, label: u.rotulo }))] },
        ],
        submit: (v) => this.api.createOccurrence(c, v),
      }).pipe(filter((r): r is DetalheOcorrencia => !!r)).subscribe((r) => {
        this.notify.success(`Ocorrência nº ${r.resumo.numero} registrada.`);
        void this.router.navigate(['/ocorrencias', r.resumo.id]);
      }));
  }
}

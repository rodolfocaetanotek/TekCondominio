import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
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
import { OccurrenceDetail, OccurrenceSummary, OpsApi } from './ops-api';

@Component({
  selector: 'tc-occurrence-list',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, PageHeader, EmptyState, CondominiumRequired, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Manutenção" title="Ocorrências" [subtitle]="context.selected()?.name">
        @if (canEdit()) { <button mat-flat-button (click)="create()"><mat-icon>add_alert</mat-icon> Registrar ocorrência</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (stats(); as s) {
          <section class="kpis">
            <div class="tc-card kpi"><span class="tc-muted">Pendentes</span><strong>{{ s.open }}</strong></div>
            <div class="tc-card kpi"><span class="tc-muted">Fora do prazo</span><strong [class.negative]="s.overdue > 0">{{ s.overdue }}</strong></div>
            <div class="tc-card kpi"><span class="tc-muted">Resolvidas</span><strong>{{ s.byStatus['RESOLVED'] ?? 0 }}</strong></div>
            <div class="tc-card kpi"><span class="tc-muted">Tempo médio de solução</span>
              <strong>{{ s.averageResolutionHours !== null && s.averageResolutionHours !== undefined ? (s.averageResolutionHours | number: '1.0-1') + ' h' : '—' }}</strong></div>
          </section>
        }
        <div class="bar">
          <mat-form-field class="grow"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon><input matInput [formControl]="search" /></mat-form-field>
          <mat-form-field class="w"><mat-label>Situação</mat-label>
            <mat-select [formControl]="status">
              <mat-option value="pending">Pendentes</mat-option>
              <mat-option value="RESOLVED">Resolvidas</mat-option>
              <mat-option value="CANCELLED">Canceladas</mat-option>
              <mat-option value="">Todas</mat-option>
            </mat-select></mat-form-field>
        </div>
        <section class="tc-table-card">
          @if ((page()?.content ?? []).length === 0) {
            <tc-empty-state icon="build" title="Nenhuma ocorrência" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page()?.content ?? []">
                <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Nº</th><td mat-cell *matCellDef="let o">{{ o.number }}</td></ng-container>
                <ng-container matColumnDef="title"><th mat-header-cell *matHeaderCellDef>Ocorrência</th>
                  <td mat-cell *matCellDef="let o"><strong>{{ o.title }}</strong><br />
                    <span class="tc-muted">{{ o.category | label: 'occurrenceCategory' }}{{ o.unitLabel ? ' · Unid. ' + o.unitLabel : '' }} · {{ o.reporterName }} · {{ o.createdAt | date: 'dd/MM/yy HH:mm' }}</span></td></ng-container>
                <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Prioridade</th>
                  <td mat-cell *matCellDef="let o"><span [class.urgent]="o.priority === 'URGENT' || o.priority === 'HIGH'">{{ o.priority | label: 'priority' }}</span></td></ng-container>
                <ng-container matColumnDef="assignee"><th mat-header-cell *matHeaderCellDef>Responsável / prazo</th>
                  <td mat-cell *matCellDef="let o">{{ o.assigneeName ?? '—' }}<br /><span [class.negative]="o.overdue">{{ o.dueDate ? 'até ' + o.dueDate.slice(8, 10) + '/' + o.dueDate.slice(5, 7) : '' }}</span></td></ng-container>
                <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let o"><span class="tc-status" [class.is-active]="o.status === 'RESOLVED'" [class.is-pending]="o.status === 'OPEN' || o.overdue"
                    [class.is-inactive]="o.status === 'CANCELLED'">{{ o.status | label: 'occurrenceStatus' }}</span></td></ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" (click)="open(row)" (keydown.enter)="open(row)"></tr>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .kpi { display: grid; } .kpi strong { font: var(--mat-sys-headline-small); }
    .bar { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
    .grow { flex: 1 1 260px; } .w { flex: 0 1 200px; }
    .urgent { color: var(--mat-sys-error); font-weight: 600; }
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
  protected readonly canEdit = computed(() => this.context.can('OCCURRENCE_EDIT') && this.context.selected()?.status === 'ACTIVE');
  private readonly source = combineLatest([toObservable(this.context.id), toObservable(this.params)]).pipe(filter(([c]) => !!c));

  protected readonly page = toSignal(this.source.pipe(switchMap(([c, p]) => this.api.occurrences(c!, {
    search: p.search, size: '100', ...(p.status === 'pending' ? { status: ['OPEN', 'IN_PROGRESS', 'WAITING'] } : p.status ? { status: p.status } : {}),
  }).pipe(catchError(() => of(null))))));
  protected readonly stats = toSignal(this.source.pipe(switchMap(([c]) => this.api.occurrenceStats(c!).pipe(catchError(() => of(null))))));

  constructor() {
    this.search.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((search) => this.params.update((p) => ({ ...p, search })));
    this.status.valueChanges.pipe(takeUntilDestroyed()).subscribe((status) => this.params.update((p) => ({ ...p, status })));
  }

  open(o: OccurrenceSummary): void {
    void this.router.navigate(['/ocorrencias', o.id]);
  }

  create(): void {
    const c = this.context.id()!;
    this.structure.units(c, { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.content)).subscribe((units) =>
      openForm<OccurrenceDetail>(this.dialog, {
        title: 'Registrar ocorrência',
        fields: [
          { key: 'title', label: 'Título', required: true, maxLength: 150 },
          { key: 'description', label: 'Descrição', type: 'textarea', required: true, maxLength: 4000 },
          { key: 'category', label: 'Categoria', type: 'select', required: true, value: 'MAINTENANCE', span: 6, options: options('occurrenceCategory') },
          { key: 'priority', label: 'Prioridade', type: 'select', value: 'MEDIUM', span: 6, options: options('priority') },
          { key: 'unitId', label: 'Unidade relacionada', type: 'select', value: null,
            options: [{ value: null, label: '— (área comum)' }, ...units.map((u) => ({ value: u.id, label: u.label }))] },
        ],
        submit: (v) => this.api.createOccurrence(c, v),
      }).pipe(filter((r): r is OccurrenceDetail => !!r)).subscribe((r) => {
        this.notify.success(`Ocorrência nº ${r.summary.number} registrada.`);
        void this.router.navigate(['/ocorrencias', r.summary.id]);
      }));
  }
}

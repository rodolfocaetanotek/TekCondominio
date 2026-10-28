import { DatePipe, KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, finalize, of, switchMap, tap } from 'rxjs';
import { PageResponse, toProblem } from '../../core/api/api.models';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { AUDIT_ACTION_LABELS, AUDIT_ENTITY_LABELS, AuditEntry } from './audit.models';
import { AuditQuery, AuditService } from './audit.service';

@Component({
  selector: 'tc-audit-list',
  imports: [
    DatePipe,
    KeyValuePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
  ],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Segurança" title="Trilha de auditoria"
        subtitle="Registro das ações críticas realizadas na sua organização." />

      <div class="filters">
        <mat-form-field>
          <mat-label>Ação</mat-label>
          <mat-select [formControl]="action">
            <mat-option value="">Todas</mat-option>
            @for (a of actions; track a[0]) {
              <mat-option [value]="a[0]">{{ a[1] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      <section class="tc-table-card" [attr.aria-busy]="loading()">
        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }
        @if (error(); as message) {
          <tc-empty-state icon="cloud_off" title="Não foi possível carregar a auditoria" [description]="message" />
        } @else if (result(); as page) {
          @if (page.totalElements === 0) {
            <tc-empty-state icon="policy" title="Nenhum registro encontrado" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page.content">
                <caption class="sr-only">Registros de auditoria</caption>
                <ng-container matColumnDef="when">
                  <th mat-header-cell *matHeaderCellDef>Data/hora</th>
                  <td mat-cell *matCellDef="let e" class="nowrap">{{ e.occurredAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                </ng-container>
                <ng-container matColumnDef="user">
                  <th mat-header-cell *matHeaderCellDef>Usuário</th>
                  <td mat-cell *matCellDef="let e">{{ e.userEmail ?? '—' }}</td>
                </ng-container>
                <ng-container matColumnDef="action">
                  <th mat-header-cell *matHeaderCellDef>Ação</th>
                  <td mat-cell *matCellDef="let e">
                    <strong>{{ actionLabel(e.action) }}</strong>
                    <span class="tc-muted sub">{{ entityLabel(e.entityType) }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="details">
                  <th mat-header-cell *matHeaderCellDef>Detalhes</th>
                  <td mat-cell *matCellDef="let e" class="details">
                    @for (d of e.details | keyvalue; track d.key) {
                      <div><span class="tc-muted">{{ d.key }}:</span> <span class="mono">{{ format(d.value) }}</span></div>
                    } @empty { — }
                  </td>
                </ng-container>
                <ng-container matColumnDef="ip">
                  <th mat-header-cell *matHeaderCellDef>IP</th>
                  <td mat-cell *matCellDef="let e" class="mono">{{ e.ipAddress ?? '—' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns"></tr>
              </table>
            </div>
            <mat-paginator [length]="page.totalElements" [pageIndex]="page.page" [pageSize]="page.size"
              [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" aria-label="Paginação da auditoria" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .filters mat-form-field { width: min(320px, 100%); }
    .sub { display: block; font: var(--mat-sys-body-small); }
    .nowrap { white-space: nowrap; }
    .details { max-width: 420px; font: var(--mat-sys-body-small); word-break: break-word; }
  `,
})
export class AuditList {
  private readonly service = inject(AuditService);

  protected readonly columns = ['when', 'user', 'action', 'details', 'ip'];
  protected readonly actions = Object.entries(AUDIT_ACTION_LABELS);
  protected readonly action = new FormControl('', { nonNullable: true });
  protected readonly query = signal<AuditQuery>({ page: 0, size: 50 });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly result = toSignal(
    toObservable(this.query).pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((q) =>
        this.service.list(q).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of<PageResponse<AuditEntry> | null>(null);
          }),
          finalize(() => this.loading.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  constructor() {
    this.action.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((action) => this.query.update((q) => ({ ...q, action, page: 0 })));
  }

  onPage(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  actionLabel(action: string): string {
    return AUDIT_ACTION_LABELS[action] ?? action;
  }

  entityLabel(entity: string | undefined): string {
    return entity ? (AUDIT_ENTITY_LABELS[entity] ?? entity) : '';
  }

  format(value: unknown): string {
    return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value);
  }
}

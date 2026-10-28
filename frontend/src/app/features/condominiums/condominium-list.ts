import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { DocPipe } from '../../shared/format/doc.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import {
  CONDOMINIUM_STATUS_LABELS,
  CONDOMINIUM_TYPE_LABELS,
  CondominiumQuery,
  SituacaoCondominio,
  CondominioResumo,
} from './condominium.models';
import { CondominiumService } from './condominium.service';

@Component({
  selector: 'tc-condominium-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    DocPipe,
  ],
  templateUrl: './condominium-list.html',
  styleUrl: './condominium-list.scss',
})
export class CondominiumList {
  private readonly service = inject(CondominiumService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly typeLabels = CONDOMINIUM_TYPE_LABELS;
  protected readonly statusLabels = CONDOMINIUM_STATUS_LABELS;

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<SituacaoCondominio | ''>('', { nonNullable: true });

  protected readonly query = signal<CondominiumQuery>({ page: 0, size: 20, sort: 'nome,asc', search: '', status: '' });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 720px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
  protected readonly columns = computed(() =>
    this.isHandset() ? ['nome', 'situacao'] : ['nome', 'tipo', 'cidade', 'situacao'],
  );

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
            return of<PaginaResponse<CondominioResumo> | null>(null);
          }),
          finalize(() => this.loading.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly hasFilters = computed(() => !!this.query().search || !!this.query().status);

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), map((v) => v.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.query.update((q) => ({ ...q, search, page: 0 })));
    this.status.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((status) => this.query.update((q) => ({ ...q, status, page: 0 })));
  }

  onSort(sort: Sort): void {
    const value = sort.direction ? `${sort.active},${sort.direction}` : 'nome,asc';
    this.query.update((q) => ({ ...q, sort: value, page: 0 }));
  }

  onPage(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  clearFilters(): void {
    this.search.setValue('', { emitEvent: false });
    this.status.setValue('', { emitEvent: false });
    this.query.update((q) => ({ ...q, search: '', status: '', page: 0 }));
  }

  retry(): void {
    this.query.update((q) => ({ ...q }));
  }

  typeLabel(c: CondominioResumo): string {
    return this.typeLabels[c.tipo];
  }

  statusLabel(c: CondominioResumo): string {
    return this.statusLabels[c.situacao];
  }

  open(row: CondominioResumo): void {
    void this.router.navigate(['/condominios', row.id]);
  }
}

import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { BuildingDialog } from './building-dialog';
import { ImportDialog } from './import-dialog';
import { Building, Unit, UNIT_TYPE_LABELS } from './structure.models';
import { StructureService } from './structure.service';
import { UnitDialog, UnitDialogData } from './unit-dialog';

@Component({
  selector: 'tc-unit-list',
  imports: [DecimalPipe, ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule, MatTooltipModule,
    PageHeader, EmptyState, CondominiumRequired],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Estrutura" title="Unidades" [subtitle]="context.selected()?.name">
        @if (canEdit()) {
          <button mat-stroked-button (click)="openImport()"><mat-icon>upload_file</mat-icon> Importar planilha</button>
          <button mat-flat-button (click)="openUnit()"><mat-icon>add</mat-icon> Nova unidade</button>
        }
      </tc-page-header>

      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (data(); as d) {
          <section class="summary">
            <div class="fraction" [class.ok]="d.summary.balanced" role="status">
              <mat-icon>{{ d.summary.balanced ? 'check_circle' : 'warning' }}</mat-icon>
              <div>
                <strong>Frações ideais: {{ d.summary.total | number: '1.2-6' }}%</strong>
                <span>{{ d.summary.activeUnits }} unidade(s) ativa(s) ·
                  {{ d.summary.balanced ? 'soma correta, rateio por fração liberado' : 'faltam ' + (d.summary.difference | number: '1.2-6') + ' p.p. para 100%' }}</span>
              </div>
            </div>
            <div class="buildings">
              <span class="tc-muted label">Blocos:</span>
              @for (b of d.buildings; track b.id) {
                <button mat-stroked-button class="chip" [class.inactive]="!b.active" [matMenuTriggerFor]="bm" [disabled]="!canEdit()"
                  [matTooltip]="b.activeUnits + ' unidade(s) ativa(s)'">{{ b.name }} ({{ b.activeUnits }})</button>
                <mat-menu #bm="matMenu">
                  <button mat-menu-item (click)="openBuilding(b)"><mat-icon>edit</mat-icon>Editar</button>
                  <button mat-menu-item (click)="toggleBuilding(b)"><mat-icon>{{ b.active ? 'block' : 'check_circle' }}</mat-icon>{{ b.active ? 'Inativar' : 'Reativar' }}</button>
                </mat-menu>
              } @empty {
                <span class="tc-muted">nenhum</span>
              }
              @if (canEdit()) {
                <button mat-button (click)="openBuilding()"><mat-icon>add</mat-icon> Bloco</button>
              }
            </div>
          </section>
        }

        <div class="filters">
          <mat-form-field class="search">
            <mat-label>Buscar unidade</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput [formControl]="search" autocomplete="off" />
          </mat-form-field>
          <mat-form-field class="narrow">
            <mat-label>Bloco</mat-label>
            <mat-select [formControl]="building">
              <mat-option value="">Todos</mat-option>
              @for (b of data()?.buildings ?? []; track b.id) {
                <mat-option [value]="b.id">{{ b.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="narrow">
            <mat-label>Situação</mat-label>
            <mat-select [formControl]="active">
              <mat-option value="">Todas</mat-option>
              <mat-option value="true">Ativas</mat-option>
              <mat-option value="false">Inativas</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <section class="tc-table-card" [attr.aria-busy]="loading()">
          @if (loading()) {
            <mat-progress-bar mode="indeterminate" />
          }
          @if (error(); as message) {
            <tc-empty-state icon="cloud_off" title="Não foi possível carregar" [description]="message" />
          } @else if (data(); as d) {
            @if (d.page.totalElements === 0) {
              <tc-empty-state icon="door_front" title="Nenhuma unidade"
                description="Cadastre as unidades uma a uma ou importe uma planilha com unidades e proprietários." />
            } @else {
              <div class="table-scroll">
                <table mat-table [dataSource]="d.page.content">
                  <caption class="sr-only">Unidades</caption>
                  <ng-container matColumnDef="label">
                    <th mat-header-cell *matHeaderCellDef>Unidade</th>
                    <td mat-cell *matCellDef="let u"><strong>{{ u.label }}</strong></td>
                  </ng-container>
                  <ng-container matColumnDef="type">
                    <th mat-header-cell *matHeaderCellDef>Tipo</th>
                    <td mat-cell *matCellDef="let u">{{ typeLabel(u) }}</td>
                  </ng-container>
                  <ng-container matColumnDef="fraction">
                    <th mat-header-cell *matHeaderCellDef class="num">Fração (%)</th>
                    <td mat-cell *matCellDef="let u" class="num mono">{{ u.idealFraction | number: '1.2-6' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="owner">
                    <th mat-header-cell *matHeaderCellDef>Proprietário</th>
                    <td mat-cell *matCellDef="let u">{{ u.ownerName ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="financial">
                    <th mat-header-cell *matHeaderCellDef>Resp. financeiro</th>
                    <td mat-cell *matCellDef="let u">{{ u.financialResponsibleName ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Situação</th>
                    <td mat-cell *matCellDef="let u"><span class="tc-status" [class.is-active]="u.active" [class.is-inactive]="!u.active">{{ u.active ? 'Ativa' : 'Inativa' }}</span></td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="columns"></tr>
                  <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                    [attr.aria-label]="'Abrir unidade ' + row.label" (click)="open(row)" (keydown.enter)="open(row)"></tr>
                </table>
              </div>
              <mat-paginator [length]="d.page.totalElements" [pageIndex]="d.page.page" [pageSize]="d.page.size"
                [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" aria-label="Paginação de unidades" />
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    .summary { display: flex; gap: 16px; flex-wrap: wrap; align-items: center; margin-bottom: 16px; }
    .fraction { display: flex; gap: 10px; align-items: center; padding: 10px 14px; border-radius: 12px;
      background: var(--mat-sys-tertiary-container); color: var(--mat-sys-on-tertiary-container); }
    .fraction.ok { background: var(--tc-success-container); color: var(--tc-success); }
    .fraction div { display: grid; }
    .fraction span { font: var(--mat-sys-body-small); }
    .buildings { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .buildings .label { margin-right: 4px; }
    .chip.inactive { opacity: .6; text-decoration: line-through; }
    .filters { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
    .filters .search { flex: 1 1 240px; }
    .filters .narrow { flex: 0 1 180px; }
    .num { text-align: right; }
  `,
})
export class UnitList {
  protected readonly context = inject(CondominiumContext);
  private readonly service = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly columns = ['label', 'type', 'fraction', 'owner', 'financial', 'status'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly building = new FormControl('', { nonNullable: true });
  protected readonly active = new FormControl('true', { nonNullable: true });
  private readonly paging = signal({ page: 0, size: 50, search: '', buildingId: '', active: 'true', tick: 0 });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canEdit = computed(() => this.context.can('STRUCTURE_EDIT') && this.context.selected()?.status === 'ACTIVE');

  private readonly query = computed(() => ({ condo: this.context.id(), ...this.paging() }));

  protected readonly data = toSignal(
    toObservable(this.query).pipe(
      filter((q) => !!q.condo),
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((q) =>
        forkJoin({
          page: this.service.units(q.condo!, q),
          buildings: this.service.buildings(q.condo!),
          summary: this.service.fractionSummary(q.condo!),
        }).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of(null);
          }),
          finalize(() => this.loading.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), map((v) => v.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.paging.update((p) => ({ ...p, search, page: 0 })));
    this.building.valueChanges.pipe(takeUntilDestroyed()).subscribe((buildingId) => this.paging.update((p) => ({ ...p, buildingId, page: 0 })));
    this.active.valueChanges.pipe(takeUntilDestroyed()).subscribe((active) => this.paging.update((p) => ({ ...p, active, page: 0 })));
  }

  typeLabel(u: Unit): string {
    return UNIT_TYPE_LABELS[u.type];
  }

  onPage(e: PageEvent): void {
    this.paging.update((p) => ({ ...p, page: e.pageIndex, size: e.pageSize }));
  }

  open(u: Unit): void {
    void this.router.navigate(['/unidades', u.id]);
  }

  openUnit(): void {
    const condominiumId = this.context.id()!;
    this.dialog
      .open<UnitDialog, UnitDialogData, Unit>(UnitDialog, { data: { condominiumId, buildings: this.data()?.buildings ?? [] }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((u): u is Unit => !!u))
      .subscribe((u) => {
        this.notify.success(`Unidade ${u.label} cadastrada.`);
        this.refresh();
      });
  }

  openBuilding(building?: Building): void {
    this.dialog
      .open(BuildingDialog, { data: { condominiumId: this.context.id()!, building }, width: '440px' })
      .afterClosed()
      .pipe(filter((b) => !!b))
      .subscribe(() => {
        this.notify.success('Bloco salvo.');
        this.refresh();
      });
  }

  toggleBuilding(b: Building): void {
    this.service.setBuildingActive(this.context.id()!, b.id, !b.active).subscribe({
      next: () => this.refresh(),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  openImport(): void {
    this.dialog
      .open(ImportDialog, { data: { condominiumId: this.context.id()! }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe(() => this.refresh());
  }

  private refresh(): void {
    this.paging.update((p) => ({ ...p, tick: p.tick + 1 }));
  }
}

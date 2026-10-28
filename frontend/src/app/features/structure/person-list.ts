import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';
import { PageResponse, toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { DocPipe } from '../../shared/format/doc.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { PersonDialog } from './person-dialog';
import { PersonDetail, PersonSummary } from './structure.models';
import { StructureService } from './structure.service';

@Component({
  selector: 'tc-person-list',
  imports: [ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatCheckboxModule, PageHeader, EmptyState, DocPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Cadastros" title="Pessoas" subtitle="Proprietários, inquilinos e moradores — cadastro único por CPF/CNPJ.">
        @if (canEdit()) {
          <button mat-flat-button (click)="create()"><mat-icon>person_add</mat-icon> Nova pessoa</button>
        }
      </tc-page-header>
      <div class="filters">
        <mat-form-field class="search">
          <mat-label>Buscar</mat-label>
          <mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="search" placeholder="Nome, e-mail ou CPF/CNPJ" autocomplete="off" />
        </mat-form-field>
        @if (context.id()) {
          <mat-checkbox [formControl]="onlyCondo">Somente do condomínio selecionado</mat-checkbox>
        }
      </div>
      <section class="tc-table-card" [attr.aria-busy]="loading()">
        <mat-progress-bar [mode]="loading() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!loading()" />
        @if (error(); as message) {
          <tc-empty-state icon="cloud_off" title="Não foi possível carregar" [description]="message" />
        } @else if (result(); as page) {
          @if (page.totalElements === 0) {
            <tc-empty-state icon="contacts" title="Nenhuma pessoa encontrada" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page.content">
                <caption class="sr-only">Pessoas</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Nome</th>
                  <td mat-cell *matCellDef="let p"><strong class="block">{{ p.name }}</strong>
                    <span class="tc-muted mono">{{ p.document ? (p.document | doc: 'cpfCnpj') : '' }}</span></td>
                </ng-container>
                <ng-container matColumnDef="contact">
                  <th mat-header-cell *matHeaderCellDef>Contato</th>
                  <td mat-cell *matCellDef="let p"><span class="block">{{ p.email ?? '' }}</span><span class="tc-muted">{{ p.phone ? (p.phone | doc: 'phone') : '' }}</span></td>
                </ng-container>
                <ng-container matColumnDef="units">
                  <th mat-header-cell *matHeaderCellDef>Unidades (vínculo vigente)</th>
                  <td mat-cell *matCellDef="let p">{{ unitsOf(p) }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + row.name" (click)="open(row)" (keydown.enter)="open(row)"></tr>
              </table>
            </div>
            <mat-paginator [length]="page.totalElements" [pageIndex]="page.page" [pageSize]="page.size"
              [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" aria-label="Paginação de pessoas" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .block { display: block; }
  `,
})
export class PersonList {
  protected readonly context = inject(CondominiumContext);
  private readonly service = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly columns = ['name', 'contact', 'units'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly onlyCondo = new FormControl(false, { nonNullable: true });
  private readonly params = signal({ page: 0, size: 20, search: '', only: false });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canEdit = computed(() => this.context.canAny('PEOPLE_EDIT'));
  private readonly query = computed(() => ({ ...this.params(), condo: this.params().only ? this.context.id() : null }));

  protected readonly result = toSignal(
    toObservable(this.query).pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((q) =>
        this.service.people({ search: q.search, page: q.page, size: q.size, condominiumId: q.condo ?? undefined }).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of<PageResponse<PersonSummary> | null>(null);
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
      .subscribe((search) => this.params.update((p) => ({ ...p, search, page: 0 })));
    this.onlyCondo.valueChanges.pipe(takeUntilDestroyed()).subscribe((only) => this.params.update((p) => ({ ...p, only, page: 0 })));
  }

  unitsOf(p: PersonSummary): string {
    return p.units.length ? p.units.join(', ') : '—';
  }

  onPage(e: PageEvent): void {
    this.params.update((p) => ({ ...p, page: e.pageIndex, size: e.pageSize }));
  }

  open(p: PersonSummary): void {
    void this.router.navigate(['/pessoas', p.id]);
  }

  create(): void {
    this.dialog
      .open(PersonDialog, { data: {}, width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((p): p is PersonDetail => !!p))
      .subscribe((p) => {
        this.notify.success(`${p.name} cadastrado(a).`);
        void this.router.navigate(['/pessoas', p.id]);
      });
  }
}

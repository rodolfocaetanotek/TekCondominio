import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notify/notification.service';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { UserCreateDialog } from './user-create-dialog';
import { UsuarioDetalhe, UserQuery, UsuarioResumo } from './user.models';
import { UserService } from './user.service';

@Component({
  selector: 'tc-user-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
  ],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Acesso" title="Usuários" subtitle="Pessoas com acesso ao sistema e seus papéis.">
        @if (auth.can('USER_MANAGE')) {
          <button mat-flat-button (click)="create()"><mat-icon>person_add</mat-icon> Novo usuário</button>
        }
      </tc-page-header>

      <div class="filters">
        <mat-form-field class="search">
          <mat-label>Buscar</mat-label>
          <mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="search" placeholder="Nome ou e-mail" autocomplete="off" />
        </mat-form-field>
        <mat-form-field class="status">
          <mat-label>Situação</mat-label>
          <mat-select [formControl]="active">
            <mat-option value="">Todos</mat-option>
            <mat-option value="true">Ativos</mat-option>
            <mat-option value="false">Inativos</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <section class="tc-table-card" [attr.aria-busy]="loading()">
        <mat-progress-bar [mode]="loading() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!loading()" />
        @if (error(); as message) {
          <tc-empty-state icon="cloud_off" title="Não foi possível carregar os usuários" [description]="message" />
        } @else if (result(); as page) {
          @if (page.totalElementos === 0) {
            <tc-empty-state icon="group" title="Nenhum usuário encontrado" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page.conteudo">
                <caption class="sr-only">Usuários</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Usuário</th>
                  <td mat-cell *matCellDef="let u">
                    <strong class="block">{{ u.name }}</strong>
                    <span class="tc-muted">{{ u.email }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="roles">
                  <th mat-header-cell *matHeaderCellDef>Papéis</th>
                  <td mat-cell *matCellDef="let u">{{ rolesOf(u) }}</td>
                </ng-container>
                <ng-container matColumnDef="last">
                  <th mat-header-cell *matHeaderCellDef>Último acesso</th>
                  <td mat-cell *matCellDef="let u">
                    @if (u.pendingInvite) {
                      <span class="tc-status is-pending">Convite pendente</span>
                    } @else {
                      {{ u.lastLoginAt | date: 'dd/MM/yyyy HH:mm' }}
                    }
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let u">
                    <span class="tc-status" [class.is-active]="u.active" [class.is-inactive]="!u.active">{{ u.active ? 'Ativo' : 'Inativo' }}</span>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + row.name" (click)="open(row)" (keydown.enter)="open(row)"></tr>
              </table>
            </div>
            <mat-paginator [length]="page.totalElementos" [pageIndex]="page.pagina" [pageSize]="page.tamanho"
              [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" aria-label="Paginação de usuários" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .block { display: block; }
  `,
})
export class UserList {
  private readonly service = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  protected readonly columns = ['name', 'roles', 'last', 'status'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly active = new FormControl<'' | 'true' | 'false'>('', { nonNullable: true });
  protected readonly query = signal<UserQuery>({ page: 0, size: 20 });
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
            return of<PaginaResponse<UsuarioResumo> | null>(null);
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
      .subscribe((search) => this.query.update((q) => ({ ...q, search, page: 0 })));
    this.active.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((active) => this.query.update((q) => ({ ...q, active, page: 0 })));
  }

  rolesOf(u: UsuarioResumo): string {
    return u.papeis.length ? u.papeis.join(', ') : '—';
  }

  onPage(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  open(u: UsuarioResumo): void {
    void this.router.navigate(['/usuarios', u.id]);
  }

  create(): void {
    this.dialog
      .open<UserCreateDialog, void, UsuarioDetalhe>(UserCreateDialog, { width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((u): u is UsuarioDetalhe => !!u))
      .subscribe((u) => {
        this.notify.success(`Convite enviado para ${u.email}.`);
        void this.router.navigate(['/usuarios', u.id]);
      });
  }
}

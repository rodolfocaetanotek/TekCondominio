import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, filter, finalize, of, switchMap, tap } from 'rxjs';
import { PageResponse, toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { DocPipe } from '../../shared/format/doc.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { OrganizationDialog } from './organization-dialog';
import { Organization, OrganizationService } from './organization.service';

@Component({
  selector: 'tc-organization-list',
  imports: [DatePipe, DocPipe, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatProgressBarModule, PageHeader, EmptyState],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Plataforma" title="Administradoras"
        subtitle="Organizações clientes. Cada uma tem dados totalmente isolados das demais.">
        <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon> Nova administradora</button>
      </tc-page-header>

      <section class="tc-table-card" [attr.aria-busy]="loading()">
        <mat-progress-bar [mode]="loading() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!loading()" />
        @if (error(); as message) {
          <tc-empty-state icon="cloud_off" title="Não foi possível carregar" [description]="message" />
        } @else if (result(); as page) {
          @if (page.totalElements === 0) {
            <tc-empty-state icon="corporate_fare" title="Nenhuma administradora cadastrada"
              description="Cadastre a primeira administradora e seu administrador para iniciar o uso da plataforma.">
              <button mat-flat-button (click)="create()">Cadastrar administradora</button>
            </tc-empty-state>
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="page.content">
                <caption class="sr-only">Administradoras</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Nome</th>
                  <td mat-cell *matCellDef="let o"><strong>{{ o.name }}</strong></td>
                </ng-container>
                <ng-container matColumnDef="cnpj">
                  <th mat-header-cell *matHeaderCellDef>CNPJ</th>
                  <td mat-cell *matCellDef="let o" class="mono">{{ o.cnpj | doc: 'cnpj' }}</td>
                </ng-container>
                <ng-container matColumnDef="created">
                  <th mat-header-cell *matHeaderCellDef>Cadastro</th>
                  <td mat-cell *matCellDef="let o">{{ o.createdAt | date: 'dd/MM/yyyy' }}</td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let o">
                    <span class="tc-status" [class.is-active]="o.active" [class.is-inactive]="!o.active">{{ o.active ? 'Ativa' : 'Inativa' }}</span>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-row *matRowDef="let row; columns: columns" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + row.name" (click)="open(row)" (keydown.enter)="open(row)"></tr>
              </table>
            </div>
            <mat-paginator [length]="page.totalElements" [pageIndex]="page.page" [pageSize]="page.size"
              [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" aria-label="Paginação" />
          }
        }
      </section>
    </div>
  `,
})
export class OrganizationList {
  private readonly service = inject(OrganizationService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly columns = ['name', 'cnpj', 'created', 'status'];
  protected readonly query = signal({ page: 0, size: 20 });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly result = toSignal(
    toObservable(this.query).pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((q) =>
        this.service.list(q.page, q.size).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of<PageResponse<Organization> | null>(null);
          }),
          finalize(() => this.loading.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  create(): void {
    this.dialog
      .open<OrganizationDialog, void, Organization>(OrganizationDialog, { width: '720px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((org): org is Organization => !!org))
      .subscribe((org) => {
        this.notify.success(`Administradora "${org.name}" cadastrada.`);
        this.query.update((q) => ({ ...q }));
      });
  }

  open(o: Organization): void {
    void this.router.navigate(['/organizacoes', o.id]);
  }

  onPage(event: PageEvent): void {
    this.query.set({ page: event.pageIndex, size: event.pageSize });
  }
}

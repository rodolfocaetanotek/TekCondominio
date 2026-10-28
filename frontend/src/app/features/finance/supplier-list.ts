import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { catchError, combineLatest, debounceTime, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { formatCpfCnpj, formatPhone } from '../../shared/format/br-format';
import { DocPipe } from '../../shared/format/doc.pipe';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { PageHeader } from '../../shared/ui/page-header';
import { FinanceApi, Supplier } from './finance-api';

@Component({
  selector: 'tc-supplier-list',
  imports: [ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, PageHeader, EmptyState, DocPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Cadastros" title="Fornecedores" subtitle="Prestadores de serviço da administradora, usados em despesas e contratos.">
        @if (canEdit()) { <button mat-flat-button (click)="edit()"><mat-icon>add_business</mat-icon> Novo fornecedor</button> }
      </tc-page-header>
      <mat-form-field class="search"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon>
        <input matInput [formControl]="search" placeholder="Nome, categoria ou CNPJ" /></mat-form-field>
      <section class="tc-table-card">
        @if ((page()?.content ?? []).length === 0) {
          <tc-empty-state icon="storefront" title="Nenhum fornecedor" />
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="page()?.content ?? []">
              <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Fornecedor</th>
                <td mat-cell *matCellDef="let s"><strong>{{ s.name }}</strong><br /><span class="tc-muted">{{ s.document ? (s.document | doc: 'cpfCnpj') : '' }}</span></td></ng-container>
              <ng-container matColumnDef="category"><th mat-header-cell *matHeaderCellDef>Categoria</th><td mat-cell *matCellDef="let s">{{ s.category ?? '—' }}</td></ng-container>
              <ng-container matColumnDef="contact"><th mat-header-cell *matHeaderCellDef>Contato</th>
                <td mat-cell *matCellDef="let s">{{ s.contactName ?? '' }} {{ s.phone ? (s.phone | doc: 'phone') : '' }}<br /><span class="tc-muted">{{ s.email ?? '' }}</span></td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                <td mat-cell *matCellDef="let s"><span class="tc-status" [class.is-active]="s.active" [class.is-inactive]="!s.active">{{ s.active ? 'Ativo' : 'Inativo' }}</span></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns" [class.is-clickable]="canEdit()" (click)="canEdit() && edit(row)"></tr>
            </table>
          </div>
        }
      </section>
    </div>
  `,
  styles: `.search { width: min(420px, 100%); }`,
})
export class SupplierList {
  private readonly context = inject(CondominiumContext);
  private readonly api = inject(FinanceApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly columns = ['name', 'category', 'contact', 'status'];
  protected readonly search = new FormControl('', { nonNullable: true });
  private readonly term = signal('');
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.canAny('SUPPLIER_EDIT'));
  protected readonly page = toSignal(combineLatest([toObservable(this.term), toObservable(this.tick)]).pipe(
    switchMap(([t]) => this.api.suppliers(t, 0, 100).pipe(catchError(() => of(null))))));

  constructor() {
    this.search.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((v) => this.term.set(v.trim()));
  }

  edit(s?: Supplier): void {
    openForm(this.dialog, {
      title: s ? 'Editar fornecedor' : 'Novo fornecedor',
      fields: [
        { key: 'name', label: 'Nome / razão social', required: true, value: s?.name, span: 8, maxLength: 150 },
        { key: 'document', label: 'CPF/CNPJ', value: formatCpfCnpj(s?.document), span: 4, mask: 'cpfCnpj' },
        { key: 'category', label: 'Categoria', value: s?.category, span: 6, hint: 'Ex.: Elevadores, Limpeza, Jardinagem', maxLength: 80 },
        { key: 'contactName', label: 'Pessoa de contato', value: s?.contactName, span: 6, maxLength: 120 },
        { key: 'email', label: 'E-mail', type: 'email', value: s?.email, span: 6 },
        { key: 'phone', label: 'Telefone', value: formatPhone(s?.phone), span: 6, mask: 'phone' },
        { key: 'notes', label: 'Observações', type: 'textarea', value: s?.notes, maxLength: 500 },
        ...(s ? [{ key: 'active', label: 'Ativo', type: 'checkbox' as const, value: s.active }] : []),
      ],
      submit: (v) => {
        const { active, ...body } = v;
        return this.api.saveSupplier(body, s?.id, s ? Boolean(active) : undefined);
      },
    }, '640px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Fornecedor salvo.'); this.tick.update((t) => t + 1); });
  }
}

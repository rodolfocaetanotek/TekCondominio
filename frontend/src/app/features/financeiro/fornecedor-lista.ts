import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { catchError, combineLatest, debounceTime, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { formatCpfCnpj, formatPhone } from '../../comum/formato/formato-br';
import { DocPipe } from '../../comum/formato/documento.pipe';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { openForm } from '../../comum/ui/formulario-dialog';
import { PageHeader } from '../../comum/ui/cabecalho-pagina';
import { FinanceApi, Fornecedor } from './financeiro-api';

@Component({
  selector: 'tc-supplier-list',
  imports: [ReactiveFormsModule, MatTableModule, MatProgressBarModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, PageHeader, EmptyState, DocPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Cadastros" title="Fornecedores" subtitle="Prestadores de serviço da administradora, usados em despesas e contratos.">
        @if (canEdit()) { <button mat-flat-button (click)="edit()"><mat-icon>add_business</mat-icon> Novo fornecedor</button> }
      </tc-page-header>
      <div class="filters">
        <mat-form-field class="search"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="search" placeholder="Nome, categoria ou CNPJ" /></mat-form-field>
      </div>
      <section class="tc-table-card" aria-live="polite">
        @if (page() === undefined) {
          <mat-progress-bar mode="indeterminate" />
        } @else if ((page()?.conteudo ?? []).length === 0) {
          <tc-empty-state icon="storefront" title="Nenhum fornecedor" />
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="page()?.conteudo ?? []">
              <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Fornecedor</th>
                <td mat-cell *matCellDef="let s"><strong>{{ s.nome }}</strong><br /><span class="tc-muted">{{ s.documento ? (s.documento | doc: 'cpfCnpj') : '' }}</span></td></ng-container>
              <ng-container matColumnDef="category"><th mat-header-cell *matHeaderCellDef>Categoria</th><td mat-cell *matCellDef="let s">{{ s.categoria ?? '—' }}</td></ng-container>
              <ng-container matColumnDef="contact"><th mat-header-cell *matHeaderCellDef>Contato</th>
                <td mat-cell *matCellDef="let s">{{ s.nomeContato ?? '' }} {{ s.telefone ? (s.telefone | doc: 'phone') : '' }}<br /><span class="tc-muted">{{ s.email ?? '' }}</span></td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                <td mat-cell *matCellDef="let s"><span class="tc-status" [class.is-active]="s.ativo" [class.is-inactive]="!s.ativo">{{ s.ativo ? 'Ativo' : 'Inativo' }}</span></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns" [class.is-clickable]="canEdit()" (click)="canEdit() && edit(row)"
                [attr.tabindex]="canEdit() ? 0 : null" (keydown.enter)="canEdit() && edit(row)"></tr>
            </table>
          </div>
        }
      </section>
    </div>
  `,
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

  edit(s?: Fornecedor): void {
    openForm(this.dialog, {
      title: s ? 'Editar fornecedor' : 'Novo fornecedor',
      fields: [
        { key: 'nome', label: 'Nome / razão social', required: true, value: s?.nome, span: 8, maxLength: 150 },
        { key: 'documento', label: 'CPF/CNPJ', value: formatCpfCnpj(s?.documento), span: 4, mask: 'cpfCnpj' },
        { key: 'categoria', label: 'Categoria', value: s?.categoria, span: 6, hint: 'Ex.: Elevadores, Limpeza, Jardinagem', maxLength: 80 },
        { key: 'nomeContato', label: 'Pessoa de contato', value: s?.nomeContato, span: 6, maxLength: 120 },
        { key: 'email', label: 'E-mail', type: 'email', value: s?.email, span: 6 },
        { key: 'telefone', label: 'Telefone', value: formatPhone(s?.telefone), span: 6, mask: 'phone' },
        { key: 'observacoes', label: 'Observações', type: 'textarea', value: s?.observacoes, maxLength: 500 },
        ...(s ? [{ key: 'ativo', label: 'Ativo', type: 'checkbox' as const, value: s.ativo }] : []),
      ],
      submit: (v) => {
        const { ativo, ...body } = v;
        return this.api.saveSupplier(body, s?.id, s ? Boolean(ativo) : undefined);
      },
    }, '640px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Fornecedor salvo.'); this.tick.update((t) => t + 1); });
  }
}

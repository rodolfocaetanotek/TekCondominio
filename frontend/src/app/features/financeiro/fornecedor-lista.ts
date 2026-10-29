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
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { formatarCpfCnpj, formatarTelefone } from '../../comum/formato/formato-br';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { FinanceiroApi, Fornecedor } from './financeiro-api';

@Component({
  selector: 'tc-fornecedor-lista',
  imports: [ReactiveFormsModule, MatTableModule, MatProgressBarModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, CabecalhoPagina, EstadoVazio, DocumentoPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Cadastros" titulo="Fornecedores" subtitulo="Prestadores de serviço da administradora, usados em despesas e contratos.">
        @if (podeEditar()) { <button mat-flat-button (click)="editar()"><mat-icon>add_business</mat-icon> Novo fornecedor</button> }
      </tc-cabecalho-pagina>
      <div class="filters">
        <mat-form-field class="search"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="busca" placeholder="Nome, categoria ou CNPJ" /></mat-form-field>
      </div>
      <section class="tc-table-card" aria-live="polite">
        @if (pagina() === undefined) {
          <mat-progress-bar mode="indeterminate" />
        } @else if ((pagina()?.conteudo ?? []).length === 0) {
          <tc-estado-vazio icone="storefront" titulo="Nenhum fornecedor" />
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="pagina()?.conteudo ?? []">
              <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Fornecedor</th>
                <td mat-cell *matCellDef="let s"><strong>{{ s.nome }}</strong><br /><span class="tc-muted">{{ s.documento ? (s.documento | documento: 'cpfCnpj') : '' }}</span></td></ng-container>
              <ng-container matColumnDef="category"><th mat-header-cell *matHeaderCellDef>Categoria</th><td mat-cell *matCellDef="let s">{{ s.categoria ?? '—' }}</td></ng-container>
              <ng-container matColumnDef="contact"><th mat-header-cell *matHeaderCellDef>Contato</th>
                <td mat-cell *matCellDef="let s">{{ s.nomeContato ?? '' }} {{ s.telefone ? (s.telefone | documento: 'telefone') : '' }}<br /><span class="tc-muted">{{ s.email ?? '' }}</span></td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                <td mat-cell *matCellDef="let s"><span class="tc-status" [class.is-active]="s.ativo" [class.is-inactive]="!s.ativo">{{ s.ativo ? 'Ativo' : 'Inativo' }}</span></td></ng-container>
              <tr mat-header-row *matHeaderRowDef="colunas"></tr>
              <tr mat-row *matRowDef="let linha; colunas: colunas" [class.is-clickable]="podeEditar()" (click)="podeEditar() && editar(linha)"
                [attr.tabindex]="podeEditar() ? 0 : null" (keydown.enter)="podeEditar() && editar(linha)"></tr>
            </table>
          </div>
        }
      </section>
    </div>
  `,
})
export class FornecedorLista {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly colunas = ['name', 'category', 'contact', 'status'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  private readonly termo = signal('');
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.podeAlgum('SUPPLIER_EDIT'));
  protected readonly pagina = toSignal(combineLatest([toObservable(this.termo), toObservable(this.recarga)]).pipe(
    switchMap(([t]) => this.api.fornecedores(t, 0, 100).pipe(catchError(() => of(null))))));

  constructor() {
    this.busca.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((v) => this.termo.set(v.trim()));
  }

  editar(s?: Fornecedor): void {
    abrirFormulario(this.dialog, {
      titulo: s ? 'Editar fornecedor' : 'Novo fornecedor',
      campos: [
        { chave: 'nome', rotulo: 'Nome / razão social', obrigatorio: true, valor: s?.nome, colunas: 8, tamanhoMaximo: 150 },
        { chave: 'documento', rotulo: 'CPF/CNPJ', valor: formatarCpfCnpj(s?.documento), colunas: 4, mascara: 'cpfCnpj' },
        { chave: 'categoria', rotulo: 'Categoria', valor: s?.categoria, colunas: 6, dica: 'Ex.: Elevadores, Limpeza, Jardinagem', tamanhoMaximo: 80 },
        { chave: 'nomeContato', rotulo: 'Pessoa de contato', valor: s?.nomeContato, colunas: 6, tamanhoMaximo: 120 },
        { chave: 'email', rotulo: 'E-mail', tipo: 'email', valor: s?.email, colunas: 6 },
        { chave: 'telefone', rotulo: 'Telefone', valor: formatarTelefone(s?.telefone), colunas: 6, mascara: 'telefone' },
        { chave: 'observacoes', rotulo: 'Observações', tipo: 'textarea', valor: s?.observacoes, tamanhoMaximo: 500 },
        ...(s ? [{ chave: 'ativo', rotulo: 'Ativo', tipo: 'checkbox' as const, valor: s.ativo }] : []),
      ],
      enviar: (v) => {
        const { ativo, ...corpo } = v;
        return this.api.salvarFornecedor(corpo, s?.id, s ? Boolean(ativo) : undefined);
      },
    }, '640px').pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Fornecedor salvo.'); this.recarga.update((t) => t + 1); });
  }
}

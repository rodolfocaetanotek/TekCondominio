import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { FinanceiroApi, Transferencia } from './financeiro-api';

@Component({
  selector: 'tc-transferencia-tab',
  imports: [CurrencyPipe, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule, DataIsoPipe, EstadoVazio],
  template: `
    <div class="head">
      <p class="tc-muted">Movimentações entre as contas do próprio condomínio (ex.: suprimento de caixa, aplicação).</p>
      @if (podeEditar()) {
        <button mat-flat-button (click)="cadastrar()"><mat-icon>swap_horiz</mat-icon> Nova transferência</button>
      }
    </div>
    <section class="tc-table-card" aria-live="polite">
      @if (lista() === undefined) {
        <mat-progress-bar mode="indeterminate" />
      } @else if ((lista() ?? []).length === 0) {
        <tc-estado-vazio icone="swap_horiz" titulo="Nenhuma transferência" />
      } @else {
        <div class="table-scroll">
          <table mat-table [dataSource]="lista() ?? []">
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Data</th><td mat-cell *matCellDef="let t">{{ t.data | dataIso }}</td></ng-container>
            <ng-container matColumnDef="route"><th mat-header-cell *matHeaderCellDef>De → Para</th>
              <td mat-cell *matCellDef="let t">{{ t.nomeContaOrigem }} → {{ t.nomeContaDestino }}<br /><span class="tc-muted">{{ t.descricao }}</span></td></ng-container>
            <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef class="money">Valor</th><td mat-cell *matCellDef="let t" class="money">{{ t.valor | currency: 'BRL' }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
              <td mat-cell *matCellDef="let t">
                @if (t.estornada) { <span class="tc-status is-inactive" [title]="t.motivoEstorno ?? ''">Estornada</span> }
                @else { <span class="tc-status" [class.is-success]="t.conciliada" [class.is-active]="!t.conciliada">{{ t.conciliada ? 'Conciliada' : 'Efetivada' }}</span> }
              </td></ng-container>
            <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let t">
                @if (!t.estornada && podeCancelar()) { <button mat-button (click)="estornar(t)">Estornar</button> }
              </td></ng-container>
            <tr mat-header-row *matHeaderRowDef="colunas"></tr>
            <tr mat-row *matRowDef="let linha; colunas: colunas"></tr>
          </table>
        </div>
      }
    </section>
  `,
  styles: `.head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 0 16px; flex-wrap: wrap; } .head p { margin: 0; }`,
})
export class TransferenciaTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly colunas = ['date', 'route', 'amount', 'status', 'actions'];
  protected readonly podeEditar = computed(() => this.contexto.pode('FINANCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly podeCancelar = computed(() => this.contexto.pode('FINANCE_CANCEL'));
  private readonly recarga = signal(0);
  protected readonly lista = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c]) => this.api.transferencias(c!).pipe(catchError(() => of([] as Transferencia[])))),
    ),
  );

  cadastrar(): void {
    const c = this.contexto.id()!;
    this.api.contasBancarias(c).subscribe((contas) => {
      const opcoes = contas.filter((a) => a.ativo).map((a) => ({ valor: a.id, rotulo: a.nome }));
      abrirFormulario(this.dialog, {
        titulo: 'Nova transferência',
        campos: [
          { chave: 'idContaOrigem', rotulo: 'De', tipo: 'select', obrigatorio: true, opcoes, colunas: 6 },
          { chave: 'idContaDestino', rotulo: 'Para', tipo: 'select', obrigatorio: true, opcoes, colunas: 6 },
          { chave: 'valor', rotulo: 'Valor (R$)', tipo: 'number', obrigatorio: true, colunas: 6, min: 0.01 },
          { chave: 'data', rotulo: 'Data', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 6 },
          { chave: 'descricao', rotulo: 'Descrição', tamanhoMaximo: 200 },
        ],
        enviar: (v) => this.api.transferencia(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notificacao.sucesso('Transferenciaência registrada.');
        this.recarga.update((t) => t + 1);
      });
    });
  }

  estornar(t: Transferencia): void {
    pedirMotivo(this.dialog, 'Estornar transferência', `Estornar a transferência de ${t.nomeContaOrigem} para ${t.nomeContaDestino}?`, 'Estornar')
      .pipe(switchMap((motivo) => this.api.estornarTransferencia(this.contexto.id()!, t.id, motivo)))
      .subscribe({
        next: () => { this.notificacao.sucesso('Transferenciaência estornada.'); this.recarga.update((x) => x + 1); },
        error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
      });
  }
}

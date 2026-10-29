import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Anexos } from '../operacao/anexos';
import { EstruturaService } from '../estrutura/estrutura.service';
import { abrirFormLancamento } from './lancamento-form';
import { LancamentoDetalhe, FinanceiroApi, Baixa } from './financeiro-api';

@Component({
  selector: 'tc-lancamento-detalhe',
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio, DataIsoPipe,
    RotuloPipe, Anexos],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/financeiro" class="back"><mat-icon>arrow_back</mat-icon> Financeiro</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir o lançamento" [descricao]="mensagem" /></section>
      } @else if (lancamento(); as e) {
        <tc-cabecalho-pagina [sobretitulo]="(e.resumo.natureza | rotulo: 'entryKind') + (e.resumo.rotuloParcela ? ' · parcela ' + e.resumo.rotuloParcela : '')"
          [titulo]="e.resumo.descricao" [subtitulo]="e.resumo.codigoConta + ' ' + e.resumo.nomeConta">
          <span class="tc-status big" [class.is-active]="e.resumo.situacao === 'PAID'"
            [class.is-pending]="e.resumo.aprovacao === 'PENDING' || (!e.resumo.vencido && (e.resumo.situacao === 'OPEN' || e.resumo.situacao === 'PARTIAL'))"
            [class.is-danger]="e.resumo.aprovacao !== 'PENDING' && e.resumo.vencido"
            [class.is-inactive]="e.resumo.situacao === 'CANCELLED' || e.resumo.situacao === 'RENEGOTIATED'">
            {{ e.resumo.aprovacao === 'PENDING' ? 'Aguardando aprovação' : (e.resumo.situacao | rotulo: 'entryStatus') }}
          </span>
        </tc-cabecalho-pagina>

        <section class="tc-kpis" aria-label="Valores do lançamento">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">receipt_long</mat-icon></span>
            <span class="label">Valor</span><span class="value">{{ e.resumo.valor | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-success">
            <span class="icon"><mat-icon aria-hidden="true">task_alt</mat-icon></span>
            <span class="label">{{ e.resumo.natureza === 'INCOME' ? 'Recebido' : 'Pago' }}</span><span class="value">{{ e.resumo.valorPago | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card" [class.tone-danger]="e.resumo.vencido" [class.tone-warning]="!e.resumo.vencido && e.resumo.valorAberto > 0">
            <span class="icon"><mat-icon aria-hidden="true">{{ e.resumo.vencido ? 'warning' : 'schedule' }}</mat-icon></span>
            <span class="label">Em aberto</span><span class="value">{{ e.resumo.valorAberto | currency: 'BRL' }}</span>
          </div>
        </section>

        <section class="facts tc-dl cols tc-card">
          <div><span class="k">Vencimento</span><strong [class.late]="e.resumo.vencido">{{ e.resumo.dataVencimento | dataIso }}</strong></div>
          <div><span class="k">Competência</span><strong>{{ e.resumo.competencia.slice(5, 7) }}/{{ e.resumo.competencia.slice(0, 4) }}</strong></div>
          @if (e.resumo.nomeFornecedor) { <div><span class="k">Fornecedor</span><strong>{{ e.resumo.nomeFornecedor }}</strong></div> }
          @if (e.resumo.rotuloUnidade) { <div><span class="k">Unidade</span><strong>{{ e.resumo.rotuloUnidade }}</strong></div> }
          @if (e.resumo.nomePessoa) { <div><span class="k">Pagador</span><strong>{{ e.resumo.nomePessoa }}</strong></div> }
          @if (e.resumo.numeroDocumento) { <div><span class="k">Documento</span><strong>{{ e.resumo.numeroDocumento }}</strong></div> }
          @if (e.nomeCentroCusto) { <div><span class="k">Centro de custo</span><strong>{{ e.nomeCentroCusto }}</strong></div> }
          @if (e.percentualMulta !== null && e.percentualMulta !== undefined) { <div><span class="k">Encargos</span><strong>multa {{ e.percentualMulta }}% · juros {{ e.percentualJurosMensal }}% a.m.</strong></div> }
          @if (e.observacoes) { <div class="wide"><span class="k">Observações</span><span>{{ e.observacoes }}</span></div> }
          @if (e.observacaoAprovacao) { <div class="wide"><span class="k">Parecer da aprovação</span><span>{{ e.observacaoAprovacao }}</span></div> }
          @if (e.motivoCancelamento) { <div class="wide"><span class="k">Motivo</span><span>{{ e.motivoCancelamento }}</span></div> }
        </section>

        <div class="tc-actions actions">
          @if (e.resumo.aprovacao === 'PENDING' && contexto.pode('FINANCE_APPROVE')) {
            <button mat-stroked-button (click)="decidir(e, false)">Reprovar</button>
            <button mat-flat-button (click)="decidir(e, true)"><mat-icon>verified</mat-icon> Aprovar pagamento</button>
          }
          @if (abrir(e) && contexto.pode('FINANCE_EDIT') && e.resumo.aprovacao !== 'PENDING' && e.resumo.aprovacao !== 'REJECTED') {
            <button mat-flat-button (click)="baixar(e)"><mat-icon>payments</mat-icon> Registrar {{ e.resumo.natureza === 'INCOME' ? 'recebimento' : 'pagamento' }}</button>
          }
          @if (abrir(e) && e.resumo.valorPago === 0 && contexto.pode('FINANCE_EDIT')) {
            <button mat-stroked-button (click)="editar(e)"><mat-icon>edit</mat-icon> Editar</button>
          }
          @if (abrir(e) && e.resumo.valorPago === 0 && contexto.pode('FINANCE_CANCEL')) {
            <button mat-button class="danger-text" (click)="cancelar(e)">Cancelar lançamento</button>
          }
        </div>

        <div class="layout">
          <section class="tc-table-card">
            <h3 class="tc-section-title tc-card-head">Baixas</h3>
            @for (p of e.baixas; track p.id) {
              <div class="pay" [class.reversed]="p.estornada">
                <div class="who">
                  <strong>{{ p.total | currency: 'BRL' }}</strong>
                  <span class="tc-muted">{{ p.dataPagamento | dataIso }} · {{ p.formaPagamento | rotulo: 'paymentMethod' }} · {{ p.nomeContaBancaria }}
                    @if (p.multa + p.juros > 0) { · encargos {{ p.multa + p.juros | currency: 'BRL' }} }
                    @if (p.desconto > 0) { · desconto {{ p.desconto | currency: 'BRL' }} }
                  </span>
                  @if (p.estornada) { <span class="tc-muted">Estornada: {{ p.motivoEstorno }}</span> }
                </div>
                @if (p.conciliada) { <span class="tc-chip-soft">Conciliada</span> }
                @if (!p.estornada && contexto.pode('FINANCE_CANCEL')) {
                  <button mat-button (click)="estornar(p)">Estornar</button>
                }
              </div>
            } @empty {
              <tc-estado-vazio icone="payments" titulo="Nenhuma baixa registrada" />
            }
          </section>
          <tc-anexos [idCondominio]="contexto.id()!" tipoEntidade="FinancialEntry" [idEntidade]="e.resumo.id"
            [categoria]="e.resumo.natureza === 'EXPENSE' ? 'INVOICE' : 'RECEIPT'" [podeEditar]="contexto.pode('FINANCE_EDIT')" />
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .facts { gap: 16px; }
    .late { color: var(--tc-danger); }
    .actions { margin: 16px 0; justify-content: flex-start; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .pay { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; padding: 12px 20px; border-top: 1px solid var(--tc-border); }
    .pay.reversed { opacity: .6; text-decoration: line-through; }
    .who { flex: 1 1 220px; display: grid; gap: 2px; min-width: 0; }
    .who strong { font-variant-numeric: tabular-nums; }
    .danger-text { --mat-button-text-label-text-color: var(--tc-danger); }
  `,
})
export class LancamentoDetalhePage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  readonly id = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly erro = signal<string | null>(null);

  protected readonly lancamento = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.id), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, id]) => this.api.lancamento(c!, id).pipe(catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); }))),
    ),
  );
  protected readonly c = computed(() => this.contexto.id()!);

  abrir(e: LancamentoDetalhe): boolean {
    return e.resumo.situacao === 'OPEN' || e.resumo.situacao === 'PARTIAL';
  }

  decidir(e: LancamentoDetalhe, aprovar: boolean): void {
    abrirFormulario(this.dialog, {
      titulo: aprovar ? 'Aprovar pagamento' : 'Reprovar pagamento',
      introducao: `${e.resumo.descricao} — R$ ${e.resumo.valor.toFixed(2).replace('.', ',')}`,
      campos: [{ chave: 'observacao', rotulo: aprovar ? 'Parecer (opcional)' : 'Motivo da reprovação', tipo: 'textarea', obrigatorio: !aprovar, tamanhoMaximo: 255 }],
      rotuloEnviar: aprovar ? 'Aprovar' : 'Reprovar',
      enviar: (v) => this.api.aprovar(this.c(), e.resumo.id, aprovar, (v['observacao'] as string) ?? null),
    }, '460px').pipe(filter((r) => !!r)).subscribe(() => this.concluir(aprovar ? 'Pagamento aprovado.' : 'Pagamento reprovado.'));
  }

  baixar(e: LancamentoDetalhe): void {
    const hoje = hojeIso();
    this.api.contasBancarias(this.c()).pipe(
      switchMap((contas) => this.api.calcularEncargos(this.c(), e.resumo.id, hoje).pipe(switchMap((q) => {
        const receita = e.resumo.natureza === 'INCOME';
        return abrirFormulario(this.dialog, {
          titulo: receita ? 'Registrar recebimento' : 'Registrar pagamento',
          introducao: q.diasAtraso > 0
            ? `Em atraso há ${q.diasAtraso} dia(s). Encargos calculados para hoje: multa R$ ${q.multa.toFixed(2)} e juros R$ ${q.juros.toFixed(2)} (ajuste se a data for outra).`
            : 'Baixa parcial é permitida: informe o valor principal pago.',
          campos: [
            { chave: 'idContaBancaria', rotulo: 'Conta', tipo: 'select', obrigatorio: true, colunas: 6, valor: contas.find((a) => a.ativo)?.id,
              opcoes: contas.filter((a) => a.ativo).map((a) => ({ valor: a.id, rotulo: a.nome })) },
            { chave: 'dataPagamento', rotulo: 'Data', tipo: 'date', obrigatorio: true, valor: hoje, colunas: 6 },
            { chave: 'principal', rotulo: 'Valor principal (R$)', tipo: 'number', obrigatorio: true, valor: e.resumo.valorAberto, colunas: 6, min: 0.01 },
            { chave: 'formaPagamento', rotulo: 'Forma', tipo: 'select', obrigatorio: true, valor: 'PIX', colunas: 6, opcoes: opcoes('paymentMethod') },
            { chave: 'multa', rotulo: 'Multa (R$)', tipo: 'number', valor: receita ? q.multa : 0, colunas: 4, min: 0 },
            { chave: 'juros', rotulo: 'Juros (R$)', tipo: 'number', valor: receita ? q.juros : 0, colunas: 4, min: 0 },
            { chave: 'desconto', rotulo: 'Desconto (R$)', tipo: 'number', valor: 0, colunas: 4, min: 0 },
            { chave: 'observacoes', rotulo: 'Observação', tamanhoMaximo: 255 },
          ],
          rotuloEnviar: 'Registrar baixa',
          enviar: (v) => this.api.baixar(this.c(), e.resumo.id, v),
        }, '620px') as Observable<unknown>;
      }))),
      filter((r) => !!r),
    ).subscribe({ next: () => this.concluir('Baixa registrada.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  editar(e: LancamentoDetalhe): void {
    abrirFormLancamento(this.dialog, this.api, this.estrutura, this.c(), e.resumo.natureza, e).pipe(filter((r) => !!r))
      .subscribe(() => this.concluir('Lançamento atualizado.'));
  }

  cancelar(e: LancamentoDetalhe): void {
    pedirMotivo(this.dialog, 'Cancelar lançamento', `"${e.resumo.descricao}" será cancelado. O registro é mantido no histórico.`, 'Cancelar lançamento')
      .pipe(switchMap((motivo) => this.api.cancelarLancamento(this.c(), e.resumo.id, motivo)))
      .subscribe({ next: () => this.concluir('Lançamento cancelado.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  estornar(p: Baixa): void {
    pedirMotivo(this.dialog, 'Estornar baixa', `Estornar a baixa de R$ ${p.total.toFixed(2).replace('.', ',')} em ${p.dataPagamento}?`, 'Estornar')
      .pipe(switchMap((motivo) => this.api.estornarBaixa(this.c(), p.id, motivo)))
      .subscribe({ next: () => this.concluir('Baixa estornada.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  private concluir(mensagem: string): void {
    this.notificacao.sucesso(mensagem);
    this.recarga.update((t) => t + 1);
  }
}

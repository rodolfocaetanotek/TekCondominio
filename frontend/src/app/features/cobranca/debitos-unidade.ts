import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { DebitosUnidade, FinanceiroApi } from '../financeiro/financeiro-api';

@Component({
  selector: 'tc-debitos-unidade',
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule, MatCheckboxModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio,
    DataIsoPipe, RotuloPipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/cobranca" class="back"><mat-icon>arrow_back</mat-icon> Cobrança</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível carregar" [descricao]="mensagem" /></section>
      } @else if (debitos(); as d) {
        <tc-cabecalho-pagina sobretitulo="Demonstrativo de débitos" [titulo]="'Unidade ' + d.rotuloUnidade"
          [subtitulo]="(d.nomeResponsavel ?? 'sem responsável financeiro') + ' · posição em ' + (d.dataBase | dataIso)" />
        <section class="tc-kpis" aria-label="Resumo do débito">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">account_balance_wallet</mat-icon></span>
            <span class="label">Principal em aberto</span><span class="value">{{ d.principalAberto | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-warning">
            <span class="icon"><mat-icon aria-hidden="true">percent</mat-icon></span>
            <span class="label">Multa + juros</span><span class="value">{{ d.encargosAbertos | currency: 'BRL' }}</span>
          </div>
          <div class="tc-kpi tc-card tone-danger">
            <span class="icon"><mat-icon aria-hidden="true">priority_high</mat-icon></span>
            <span class="label">Total atualizado</span><span class="value">{{ d.totalAberto | currency: 'BRL' }}</span>
          </div>
        </section>

        <section class="tc-table-card">
          <div class="tc-card-head">
            <h3 class="tc-section-title">Cobranças</h3>
            @if (podeEditar() && selecionado().length) {
              <button mat-flat-button (click)="acordo(d)"><mat-icon>handshake</mat-icon> Negociar {{ selecionado().length }} débito(s)</button>
            }
          </div>
          <div class="table-scroll">
            <table class="lines tc-cards-mobile">
              <thead><tr><th></th><th>Vencimento</th><th>Descrição</th><th class="money">Valor</th><th class="money">Pago</th><th class="money">Atualizado</th><th>Situação</th></tr></thead>
              <tbody>
                @for (l of d.linhas; track l.idLancamento) {
                  <tr>
                    <td>@if (podeEditar() && (l.situacao === 'OPEN' || l.situacao === 'PARTIAL')) {
                      <mat-checkbox [checked]="selecionado().includes(l.idLancamento)" (change)="alternar(l.idLancamento)" [attr.aria-label]="'Selecionar ' + l.descricao" /> }</td>
                    <td [class.late]="l.diasAtraso > 0">{{ l.dataVencimento | dataIso }}</td>
                    <td><a [routerLink]="['/financeiro/lancamentos', l.idLancamento]">{{ l.descricao }}</a>
                      @if (l.diasAtraso > 0) { <small class="tc-muted"> · {{ l.diasAtraso }} dias</small> }</td>
                    <td class="money">{{ l.valor | currency: 'BRL' }}</td>
                    <td class="money">{{ l.valorPago | currency: 'BRL' }}</td>
                    <td class="money">{{ l.valorAberto > 0 ? (l.totalAtualizado | currency: 'BRL') : '—' }}</td>
                    <td><span class="tc-status" [class.is-active]="l.situacao === 'PAID'"
                      [class.is-pending]="(l.situacao === 'OPEN' || l.situacao === 'PARTIAL') && l.diasAtraso <= 0"
                      [class.is-danger]="(l.situacao === 'OPEN' || l.situacao === 'PARTIAL') && l.diasAtraso > 0"
                      [class.is-inactive]="l.situacao === 'CANCELLED' || l.situacao === 'RENEGOTIATED'">{{ l.situacao | rotulo: 'entryStatus' }}</span></td>
                  </tr>
                } @empty { <tr><td colspan="7" class="tc-muted">Nenhuma cobrança.</td></tr> }
              </tbody>
            </table>
          </div>
        </section>

        <div class="cols">
          <section class="tc-card">
            <div class="tc-card-head"><h3 class="tc-section-title">Histórico de cobrança</h3>
              @if (podeEditar()) { <button mat-button (click)="observacao()"><mat-icon>add_comment</mat-icon> Registrar contato</button> }</div>
            @for (n of d.notas; track n.id) {
              <div class="note"><strong>{{ n.dataContato | dataIso }} · {{ n.tipoContato | rotulo: 'contactType' }}</strong>
                <p>{{ n.observacao }}</p><small class="tc-muted">{{ n.nomeCriadoPor }}</small></div>
            } @empty { <p class="tc-muted">Nenhum contato registrado.</p> }
          </section>
          <section class="tc-card">
            <h3 class="tc-section-title">Acordos</h3>
            @for (a of d.acordos; track a.id) {
              <div class="note"><strong>{{ a.valorTotal | currency: 'BRL' }} em {{ a.totalParcelas }}x — {{ a.situacao | rotulo: 'agreementStatus' }}</strong>
                <p class="tc-muted">{{ a.parcelasPagas }}/{{ a.totalParcelas }} parcelas pagas · 1ª em {{ a.dataPrimeiroVencimento | dataIso }}</p></div>
            } @empty { <p class="tc-muted">Nenhum acordo.</p> }
          </section>
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .tc-section-title { margin-top: 0; }
    .lines { margin-top: 8px; }
    .late { color: var(--tc-danger); font-weight: 600; }
    .cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(300px, 100%), 1fr)); gap: 16px; margin-top: 16px; align-items: start; }
    .note { border-bottom: 1px solid var(--tc-border); padding: 10px 0; }
    .note:last-child { border-bottom: 0; }
    .note p { margin: 4px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
  `,
})
export class DebitosUnidadePage {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  readonly idUnidade = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly selecionado = signal<string[]>([]);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditar = computed(() => this.contexto.pode('BILLING_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');

  protected readonly debitos = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.idUnidade), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, u]) => this.api.debitosUnidade(c!, u).pipe(catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); }))),
    ),
  );

  alternar(id: string): void {
    this.selecionado.update((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }

  observacao(): void {
    abrirFormulario(this.dialog, {
      titulo: 'Registrar contato de cobrança',
      campos: [
        { chave: 'tipoContato', rotulo: 'Tipo', tipo: 'select', obrigatorio: true, valor: 'PHONE', colunas: 6, opcoes: opcoes('contactType') },
        { chave: 'dataContato', rotulo: 'Data', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 6 },
        { chave: 'observacao', rotulo: 'Descrição do contato', tipo: 'textarea', obrigatorio: true, tamanhoMaximo: 1000 },
      ],
      enviar: (v) => this.api.registrarNota(this.contexto.id()!, this.idUnidade(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Contato registrado.'); this.recarga.update((t) => t + 1); });
  }

  acordo(d: DebitosUnidade): void {
    const total = d.linhas.filter((l) => this.selecionado().includes(l.idLancamento)).reduce((s, l) => s + l.totalAtualizado, 0);
    abrirFormulario(this.dialog, {
      titulo: 'Acordo de parcelamento',
      introducao: `${this.selecionado().length} débito(s), total atualizado R$ ${total.toFixed(2).replace('.', ',')}. Os débitos serão substituídos pelas parcelas do acordo.`,
      campos: [
        { chave: 'totalParcelas', rotulo: 'Nº de parcelas', tipo: 'number', obrigatorio: true, valor: 3, colunas: 4, min: 1 },
        { chave: 'dataPrimeiroVencimento', rotulo: '1º vencimento', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 4 },
        { chave: 'desconto', rotulo: 'Desconto (R$)', tipo: 'number', valor: 0, colunas: 4, min: 0 },
        { chave: 'incluirEncargos', rotulo: 'Incluir multa e juros calculados até hoje', tipo: 'checkbox', valor: true },
        { chave: 'observacoes', rotulo: 'Observações', tipo: 'textarea', tamanhoMaximo: 500 },
      ],
      rotuloEnviar: 'Criar acordo',
      enviar: (v) => this.api.cadastrarAcordo(this.contexto.id()!, { ...v, idUnidade: this.idUnidade(), idsLancamentos: this.selecionado() }),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notificacao.sucesso('Acordo criado.');
      this.selecionado.set([]);
      this.recarga.update((t) => t + 1);
    });
  }
}

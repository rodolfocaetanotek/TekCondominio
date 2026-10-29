import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { FinanceiroApi } from '../financeiro/financeiro-api';
import { Anexos } from './anexos';
import { Contrato, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-contrato-lista',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, MatExpansionModule, CabecalhoPagina, EstadoVazio, CondominioObrigatorio,
    DataIsoPipe, RotuloPipe, Anexos],
  template: `
    <div class="tc-page narrow">
      <tc-cabecalho-pagina sobretitulo="Fornecedores" titulo="Contratos" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) { <button mat-flat-button (click)="editar()"><mat-icon>contract</mat-icon> Novo contrato</button> }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <mat-accordion class="tc-accordion-cards">
          @for (c of lista() ?? []; track c.id) {
            <mat-expansion-panel (opened)="abrirHistorico(c)">
              <mat-expansion-panel-header>
                <mat-panel-title>{{ c.titulo }}</mat-panel-title>
                <mat-panel-description>
                  <span class="meta">{{ c.nomeFornecedor }} · <span class="money">{{ c.valor | currency: 'BRL' }}</span> {{ c.periodicidade | rotulo: 'periodicity' }}</span>
                  <span class="tc-status" [class.is-active]="c.situacao === 'ACTIVE' && !c.aVencer && !c.vencido"
                    [class.is-pending]="c.situacao !== 'TERMINATED' && !c.vencido && c.aVencer" [class.is-danger]="c.situacao !== 'TERMINATED' && c.vencido"
                    [class.is-inactive]="c.situacao === 'TERMINATED'">{{ c.situacao === 'TERMINATED' ? 'Encerrado' : c.vencido ? 'Vencido' : c.aVencer ? 'A vencer' : 'Vigente' }}</span>
                </mat-panel-description>
              </mat-expansion-panel-header>
              <p class="terms">Vigência: {{ c.dataInicio | dataIso }} a {{ c.dataFim ? (c.dataFim | dataIso) : 'indeterminado' }} · aviso prévio {{ c.diasAvisoPrevio }} dias
                {{ c.indiceReajuste ? ' · reajuste ' + c.indiceReajuste + (c.mesReajuste ? ' em ' + c.mesReajuste + '/ano' : '') : '' }}</p>
              @if (c.descricao) { <p class="pre">{{ c.descricao }}</p> }
              @if (c.motivoEncerramento) { <p class="tc-muted">Encerrado em {{ c.dataEncerramento | dataIso }}: {{ c.motivoEncerramento }}</p> }
              <h4 class="tc-section-label">Histórico</h4>
              @for (h of historico()[c.id] ?? []; track h.criadoEm) {
                <p class="small hist"><strong>{{ h.criadoEm | date: 'dd/MM/yy HH:mm' }}</strong> {{ h.nomeCriadoPor }}: {{ h.alteracao }}</p>
              }
              <tc-anexos [idCondominio]="contexto.id()!" tipoEntidade="Contract" [idEntidade]="c.id" categoria="CONTRACT" [podeEditar]="podeEditar()" />
              @if (podeEditar() && c.situacao === 'ACTIVE') {
                <div class="tc-actions foot">
                  <button mat-button (click)="encerrar(c)">Encerrar contrato</button>
                  <button mat-stroked-button (click)="editar(c)">Editar</button>
                </div>
              }
            </mat-expansion-panel>
          } @empty {
            <section class="tc-card"><tc-estado-vazio icone="contract" titulo="Nenhum contrato cadastrado" /></section>
          }
        </mat-accordion>
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .meta { flex: 1 1 auto; min-width: 0; }
    .terms { margin-top: 4px; }
    .pre { white-space: pre-wrap; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); margin: 2px 0; }
    .hist { padding: 4px 0 4px 10px; border-left: 2px solid var(--tc-border); }
    h4.tc-section-label { margin: 16px 0 6px; }
    tc-attachments { display: block; margin-top: 16px; }
    .foot { margin-top: 12px; }
  `,
})
export class ContratoLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly financeiro = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly historico = signal<Record<string, Contrato['historico']>>({});
  protected readonly podeEditar = computed(() => this.contexto.pode('SUPPLIER_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly lista = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.contratos(c!).pipe(catchError(() => of([] as Contrato[]))))));

  abrirHistorico(c: Contrato): void {
    this.api.contrato(this.contexto.id()!, c.id).subscribe((completo) => this.historico.update((h) => ({ ...h, [c.id]: completo.historico })));
  }

  editar(c?: Contrato): void {
    this.financeiro.fornecedores('', 0, 100).pipe(map((p) => p.conteudo)).subscribe((fornecedores) => abrirFormulario(this.dialog, {
      titulo: c ? 'Editar contrato' : 'Novo contrato',
      campos: [
        { chave: 'idFornecedor', rotulo: 'Fornecedor', tipo: 'select', obrigatorio: true, valor: c?.idFornecedor, colunas: 6,
          opcoes: fornecedores.filter((s) => s.ativo || s.id === c?.idFornecedor).map((s) => ({ valor: s.id, rotulo: s.nome })),
          dica: 'Cadastre o fornecedor em Fornecedores, se necessário.' },
        { chave: 'titulo', rotulo: 'Objeto', obrigatorio: true, valor: c?.titulo, colunas: 6, tamanhoMaximo: 200 },
        { chave: 'valor', rotulo: 'Valor (R$)', tipo: 'number', obrigatorio: true, valor: c?.valor, colunas: 4, min: 0 },
        { chave: 'periodicidade', rotulo: 'Periodicidade', tipo: 'select', obrigatorio: true, valor: c?.periodicidade ?? 'MONTHLY', colunas: 4, opcoes: opcoes('periodicity') },
        { chave: 'diasAvisoPrevio', rotulo: 'Aviso prévio (dias)', tipo: 'number', valor: c?.diasAvisoPrevio ?? 30, colunas: 4, min: 0 },
        { chave: 'dataInicio', rotulo: 'Início', tipo: 'date', obrigatorio: true, valor: c?.dataInicio ?? hojeIso(), colunas: 6 },
        { chave: 'dataFim', rotulo: 'Término', tipo: 'date', valor: c?.dataFim, colunas: 6 },
        { chave: 'indiceReajuste', rotulo: 'Índice de reajuste', valor: c?.indiceReajuste, colunas: 6, dica: 'Ex.: IPCA, IGP-M', tamanhoMaximo: 20 },
        { chave: 'mesReajuste', rotulo: 'Mês do reajuste (1–12)', tipo: 'number', valor: c?.mesReajuste, colunas: 6, min: 1 },
        { chave: 'descricao', rotulo: 'Descrição / cláusulas', tipo: 'textarea', valor: c?.descricao, tamanhoMaximo: 2000 },
      ],
      enviar: (v) => this.api.salvarContrato(this.contexto.id()!, { ...v, versao: c?.versao }, c?.id),
    }, '680px').pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Contrato salvo.'); this.recarga.update((t) => t + 1); }));
  }

  encerrar(c: Contrato): void {
    abrirFormulario(this.dialog, {
      titulo: 'Encerrar contrato',
      campos: [
        { chave: 'dataEncerramento', rotulo: 'Data de encerramento', tipo: 'date', obrigatorio: true, valor: hojeIso() },
        { chave: 'motivo', rotulo: 'Motivo', obrigatorio: true, tamanhoMaximo: 255 },
      ],
      rotuloEnviar: 'Encerrar',
      enviar: (v) => this.api.encerrarContrato(this.contexto.id()!, c.id, v),
    }, '440px').pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Contrato encerrado.'); this.recarga.update((t) => t + 1); });
  }
}

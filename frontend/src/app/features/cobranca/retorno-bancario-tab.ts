import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { ContaBancaria, FinanceiroApi, ItemRetorno } from '../financeiro/financeiro-api';

/**
 * Retorno de cobrança (CNAB 240): o arquivo baixado do banco dá baixa automática nas cobranças liquidadas,
 * identificadas pelo número de cobrança informado no boleto. O que não puder ser baixado fica pendente.
 */
@Component({
  selector: 'tc-retorno-bancario-tab',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule,
    MatTooltipModule, DataIsoPipe],
  template: `
    <section class="tc-card">
      <div class="filters">
        <mat-form-field class="search"><mat-label>Conta de cobrança</mat-label>
          <mat-select [formControl]="conta">
            @for (a of contas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}{{ a.codigoBanco ? ' · banco ' + a.codigoBanco : '' }}</mat-option> }
          </mat-select></mat-form-field>
        @if (podeEditar() && conta.value) {
          <label class="tc-file-button"><input type="file" accept=".ret,.RET,.txt,.TXT,.cnab" (change)="enviarArquivo($event)" [disabled]="ocupado()" />
            <mat-icon>upload_file</mat-icon> {{ ocupado() ? 'Processando…' : 'Processar retorno CNAB 240' }}</label>
        }
      </div>
      <p class="tc-muted small hint">Layout FEBRABAN 240 posições (segmentos T e U). A cobrança é localizada pelo "seu número" ou nosso número
        igual ao <strong>número de cobrança</strong> do título. Cada banco exige homologação do layout antes do uso em produção.</p>
    </section>

    <section class="tc-card">
      <h3 class="tc-section-title">Pendências de baixa ({{ pendente()?.length ?? 0 }})</h3>
      <div class="scroll">
      <table class="lines tc-cards-mobile">
        <thead><tr><th>Pago em</th><th>Seu nº / nosso nº</th><th class="money">Valor pago</th><th>Motivo</th><th></th></tr></thead>
        <tbody>
          @for (i of pendente() ?? []; track i.id) {
            <tr>
              <td>{{ i.dataOcorrencia | dataIso }}</td>
              <td>{{ i.seuNumero || '—' }} / {{ i.nossoNumero || '—' }}</td>
              <td class="money">{{ i.valorPago | currency: 'BRL' }}</td>
              <td>{{ i.mensagem }}{{ i.descricaoLancamento ? ' · ' + i.descricaoLancamento : '' }}</td>
              <td class="acts">
                @if (podeEditar()) {
                  <button mat-icon-button (click)="baixar(i)" matTooltip="Baixar em uma cobrança" aria-label="Baixar"><mat-icon>task_alt</mat-icon></button>
                  <button mat-icon-button (click)="ignorar(i)" matTooltip="Descartar com motivo" aria-label="Descartar"><mat-icon>block</mat-icon></button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="tc-muted">Nenhuma pendência.</td></tr>
          }
        </tbody>
      </table>
      </div>
    </section>

    <section class="tc-card">
      <h3 class="tc-section-title">Arquivos processados</h3>
      <div class="scroll">
      <table class="lines tc-cards-mobile">
        <thead><tr><th>Processado em</th><th>Arquivo</th><th>Banco</th><th>Títulos</th><th>Baixados</th><th>Pendentes</th><th>Por</th></tr></thead>
        <tbody>
          @for (f of arquivos() ?? []; track f.id) {
            <tr><td>{{ f.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</td><td>{{ f.nomeArquivo }}</td><td>{{ f.codigoBanco }}</td>
              <td>{{ f.totalItens }}</td><td>{{ f.baixados }}</td><td [class.warn]="f.pendentes > 0">{{ f.pendentes }}</td><td>{{ f.nomeCriadoPor }}</td></tr>
          } @empty {
            <tr><td colspan="7" class="tc-muted">Nenhum retorno processado.</td></tr>
          }
        </tbody>
      </table>
      </div>
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .small { font: var(--mat-sys-body-small); }
    .hint { margin: 0; }
    .tc-section-title { margin-top: 0; }
    .scroll { overflow-x: auto; }
    .warn { color: var(--tc-danger); font-weight: 600; }
    .acts { white-space: nowrap; }
  `,
})
export class RetornoBancarioTab {
  private readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly ocupado = signal(false);
  protected readonly conta = new FormControl<string | null>(null);

  protected readonly podeEditar = computed(() => this.contexto.pode('BILLING_EDIT') && this.contexto.pode('FINANCE_EDIT')
    && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly origem = combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(filter(([c]) => !!c));
  protected readonly contas = toSignal(toObservable(this.contexto.id).pipe(filter((c) => !!c),
    switchMap((c) => this.api.contasBancarias(c!).pipe(map((listar) => listar.filter((a) => a.tipo !== 'CASH' && a.ativo)),
      catchError(() => of([] as ContaBancaria[]))))));
  protected readonly arquivos = toSignal(this.origem.pipe(switchMap(([c]) => this.api.arquivosRetorno(c!).pipe(catchError(() => of([]))))));
  protected readonly pendente = toSignal(this.origem.pipe(switchMap(([c]) => this.api.itensRetornoPendentes(c!).pipe(catchError(() => of([] as ItemRetorno[]))))));

  enviarArquivo(evento: Event): void {
    const campoArquivo = evento.target as HTMLInputElement;
    const arquivo = campoArquivo.files?.[0];
    campoArquivo.value = '';
    if (!arquivo) return;
    this.ocupado.set(true);
    this.api.processarRetorno(this.contexto.id()!, this.conta.value!, arquivo).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.notificacao.sucesso(`Retorno processado: ${r.baixados} cobrança(s) baixada(s), ${r.pendentes} pendente(s).`);
        this.recarga.update((t) => t + 1);
      },
      error: (err: unknown) => {
        this.ocupado.set(false);
        this.notificacao.erro(paraProblema(err).detail);
      },
    });
  }

  baixar(item: ItemRetorno): void {
    const c = this.contexto.id()!;
    this.api.lancamentos(c, { natureza: 'INCOME', situacao: ['OPEN', 'PARTIAL'], tamanho: 200, ordem: 'dataVencimento,asc' }).subscribe((pagina) =>
      abrirFormulario(this.dialog, {
        titulo: 'Baixar pagamento do retorno',
        introducao: `Valor pago: R$ ${item.valorPago.toFixed(2).replace('.', ',')}. Escolha a cobrança correspondente.`,
        campos: [{ chave: 'idLancamento', rotulo: 'Cobrança', tipo: 'select', obrigatorio: true,
          opcoes: pagina.conteudo.map((e) => ({ valor: e.id, rotulo: `${e.rotuloUnidade ?? '—'} · ${e.descricao} · venc. ${e.dataVencimento.split('-').reverse().join('/')} · R$ ${e.valorAberto.toFixed(2).replace('.', ',')}` })) }],
        enviar: (v) => this.api.baixarItemRetorno(c, item.id, v['idLancamento'] as string),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notificacao.sucesso('Pagamento baixado.');
        this.recarga.update((t) => t + 1);
      }));
  }

  ignorar(item: ItemRetorno): void {
    abrirFormulario(this.dialog, {
      titulo: 'Descartar pendência do retorno',
      campos: [{ chave: 'observacao', rotulo: 'Motivo', obrigatorio: true, tamanhoMaximo: 255 }],
      enviar: (v) => this.api.ignorarItemRetorno(this.contexto.id()!, item.id, v['observacao'] as string),
    }).pipe(filter((r) => !!r)).subscribe(() => this.recarga.update((t) => t + 1));
  }
}

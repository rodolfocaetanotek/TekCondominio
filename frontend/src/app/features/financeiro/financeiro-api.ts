import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';

export interface ContaPlano { id: string; codigo: string; nome: string; natureza: 'INCOME' | 'EXPENSE'; analitica: boolean; sistema: boolean; ativo: boolean; }
export interface CentroCusto { id: string; nome: string; ativo: boolean; }
export interface ContaBancaria { id: string; nome: string; tipo: string; codigoBanco?: string; agencia?: string; numeroConta?: string; saldoInicial: number; dataAbertura: string; ativo: boolean; saldo: number; }
export interface ParametrosFinanceiros { percentualMulta: number; percentualJurosMensal: number; diaVencimento: number; exigeAprovacaoContasPagar: boolean; }
export interface Fornecedor { id: string; nome: string; documento?: string; categoria?: string; email?: string; telefone?: string; nomeContato?: string; observacoes?: string; ativo: boolean; }

export interface LancamentoResumo {
  id: string; natureza: 'INCOME' | 'EXPENSE'; descricao: string; codigoConta: string; nomeConta: string; nomeFornecedor?: string;
  rotuloUnidade?: string; nomePessoa?: string; valor: number; valorPago: number; valorAberto: number; dataVencimento: string; competencia: string;
  situacao: string; aprovacao: string; vencido: boolean; rotuloParcela?: string; numeroDocumento?: string; numeroCobranca?: number;
}
export interface Baixa {
  id: string; idContaBancaria: string; nomeContaBancaria: string; dataPagamento: string; principal: number; multa: number; juros: number;
  desconto: number; total: number; formaPagamento: string; observacoes?: string; conciliada: boolean; estornada: boolean; estornadaEm?: string; motivoEstorno?: string;
}
export interface LancamentoDetalhe {
  resumo: LancamentoResumo; idContaPlano: string; idCentroCusto?: string; nomeCentroCusto?: string; idFornecedor?: string; idUnidade?: string;
  idPessoa?: string; observacoes?: string; origem: string; observacaoAprovacao?: string; aprovadoEm?: string; motivoCancelamento?: string;
  canceladoEm?: string; percentualMulta?: number; percentualJurosMensal?: number; versao: number; baixas: Baixa[];
}
export interface LancamentoRequest {
  natureza: 'INCOME' | 'EXPENSE'; descricao: string; idContaPlano: string; idCentroCusto: string | null; idFornecedor: string | null;
  idUnidade: string | null; idPessoa: string | null; valor: number; dataVencimento: string; competencia: string | null;
  numeroDocumento: string | null; observacoes: string | null; repeticoes: number | null; versao?: number;
}
export interface CalculoEncargos { principal: number; diasAtraso: number; multa: number; juros: number; total: number; }
export interface Transferencia { id: string; idContaOrigem: string; nomeContaOrigem: string; idContaDestino: string; nomeContaDestino: string; valor: number; data: string; descricao?: string; conciliada: boolean; estornada: boolean; motivoEstorno?: string; }
export interface Movimentacao { tipo: string; id: string; data: string; descricao: string; valor: number; saldo: number; conciliada: boolean; }
export interface Extrato { idContaBancaria: string; nomeContaBancaria: string; de: string; ate: string; saldoInicial: number; saldoFinal: number; saldoConciliado: number; movimentacoes: Movimentacao[]; }
export interface ContaOrcamento { idContaPlano: string; codigo: string; nome: string; natureza: string; previsto: number[]; realizado: number[]; totalPrevisto: number; totalRealizado: number; }
export interface Orcamento { ano: number; contas: ContaOrcamento[]; }

export interface Rateio { id: string; competencia: string; descricao: string; codigoConta?: string; nomeConta?: string; criterio: string; valorBase: number; dataVencimento: string; quantidadeUnidades: number; valorGerado: number; situacao: string; quantidadePagas: number; valorRecebido: number; motivoCancelamento?: string; }
export interface RateioRequest { competencia: string; descricao: string; idContaPlano: string; criterio: string; valor: number | null; dataVencimento: string | null; consumo?: Record<string, number>; }
export interface SimulacaoRateio { competencia: string; dataVencimento: string; total: number; unidades: number; unidadesSemResponsavel: number; linhas: { idUnidade: string; rotuloUnidade: string; fracaoIdeal: number; nomeResponsavel?: string; valor: number }[]; }
export interface LinhaInadimplencia { idUnidade: string; rotuloUnidade: string; nomeResponsavel?: string; emailResponsavel?: string; telefoneResponsavel?: string; quantidadeVencidas: number; principal: number; encargos: number; total: number; vencimentoMaisAntigo: string; maximoDiasAtraso: number; dataUltimoContato?: string; }
export interface Inadimplencia { dataBase: string; totalPrincipal: number; totalEncargos: number; total: number; emitidoUltimos12Meses: number; indiceInadimplencia: number; unidadesEmDebito: number; unidadesAtivas: number; linhas: LinhaInadimplencia[]; }
export interface LinhaDebito { idLancamento: string; descricao: string; competencia: string; dataVencimento: string; valor: number; valorPago: number; valorAberto: number; situacao: string; diasAtraso: number; multa: number; juros: number; totalAtualizado: number; }
export interface NotaCobranca { id: string; tipoContato: string; dataContato: string; observacao: string; idLancamento?: string; nomeCriadoPor?: string; criadoEm: string; }
export interface Acordo { id: string; idUnidade: string; rotuloUnidade?: string; valorOriginal: number; valorEncargos: number; desconto: number; valorTotal: number; totalParcelas: number; dataPrimeiroVencimento: string; situacao: string; parcelasPagas: number; observacoes?: string; motivoCancelamento?: string; criadoEm: string; parcelas: LancamentoResumo[]; }
export interface DebitosUnidade { idUnidade: string; rotuloUnidade: string; nomeResponsavel?: string; dataBase: string; principalAberto: number; encargosAbertos: number; totalAberto: number; linhas: LinhaDebito[]; notas: NotaCobranca[]; acordos: Acordo[]; }

/** Fotografia gravada no fechamento: chaves em inglês por serem dado histórico persistido. */
export interface FotografiaFechamento { income: string; expense: string; unreconciledMovements: number; balances: { account: string; balance: string; reconciled: string }[]; }
export interface ItemHistorico { id: string; acao: 'CLOSE' | 'REOPEN'; fechadoAte?: string; fechadoAteAnterior?: string; observacoes?: string; fotografia?: FotografiaFechamento; criadoEm: string; nomeCriadoPor?: string; }
export interface SituacaoFechamento { fechadoAte?: string; sugestaoAte?: string; historico: ItemHistorico[]; }
export interface SaldoConta { id: string; nome: string; saldo: number; saldoConciliado: number; }
export interface Verificacao { de: string; ate: string; movimentacoesNaoConciliadas: number; aprovacoesPendentes: number; receitas: number; despesas: number; saldos: SaldoConta[]; }
export interface ResultadoImportacao { idImportacao: string; nomeArquivo: string; inicioPeriodo?: string; fimPeriodo?: string; linhasArquivo: number; linhasNovas: number; duplicadas: number; conciliadasAutomaticamente: number; pendentes: number; }
export interface Sugestao { tipo: string; id: string; data: string; valor: number; descricao: string; }
export interface LinhaExtrato { id: string; fitId: string; dataLancamento: string; valor: number; tipoTransacao?: string; historico?: string; situacao: 'UNMATCHED' | 'MATCHED' | 'IGNORED'; tipoConciliado?: string; idConciliado?: string; observacao?: string; sugestoes: Sugestao[]; }
export interface ArquivoRetorno { id: string; idContaBancaria: string; nomeArquivo: string; codigoBanco: string; dataGeracao?: string; totalItens: number; baixados: number; pendentes: number; criadoEm: string; nomeCriadoPor?: string; }
export interface ItemRetorno { id: string; idArquivo: string; sequencia: number; codigoMovimento: string; descricaoMovimento: string; nossoNumero?: string; seuNumero?: string; dataVencimento?: string; valorTitulo: number; encargos: number; desconto: number; valorPago: number; tarifa: number; dataOcorrencia?: string; dataCredito?: string; situacao: 'SETTLED' | 'PENDING' | 'INFO' | 'IGNORED'; idLancamento?: string; descricaoLancamento?: string; rotuloUnidade?: string; mensagem?: string; }

@Injectable({ providedIn: 'root' })
export class FinanceiroApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}/financeiro`;
  }
  private cobranca(c: string): string {
    return `/api/v1/condominios/${c}/cobranca`;
  }

  contasPlano(): Observable<ContaPlano[]> { return this.http.get<ContaPlano[]>('/api/v1/financeiro/plano-contas'); }
  cadastrarContaPlano(corpo: { codigo: string; nome: string; natureza: string }): Observable<ContaPlano> { return this.http.post<ContaPlano>('/api/v1/financeiro/plano-contas', corpo); }
  parametros(c: string): Observable<ParametrosFinanceiros> { return this.http.get<ParametrosFinanceiros>(`${this.b(c)}/parametros`); }
  salvarParametros(c: string, s: ParametrosFinanceiros): Observable<ParametrosFinanceiros> { return this.http.put<ParametrosFinanceiros>(`${this.b(c)}/parametros`, s); }
  centrosCusto(c: string): Observable<CentroCusto[]> { return this.http.get<CentroCusto[]>(`${this.b(c)}/centros-custo`); }
  salvarCentroCusto(c: string, nome: string, id?: string, ativo?: boolean): Observable<CentroCusto> {
    return id
      ? this.http.put<CentroCusto>(`${this.b(c)}/centros-custo/${id}`, { nome }, { params: ativo === undefined ? {} : { ativo } })
      : this.http.post<CentroCusto>(`${this.b(c)}/centros-custo`, { nome });
  }
  contasBancarias(c: string): Observable<ContaBancaria[]> { return this.http.get<ContaBancaria[]>(`${this.b(c)}/contas-bancarias`); }
  cadastrarContaBancaria(c: string, corpo: Record<string, unknown>): Observable<ContaBancaria> { return this.http.post<ContaBancaria>(`${this.b(c)}/contas-bancarias`, corpo); }
  atualizarContaBancaria(c: string, id: string, corpo: Record<string, unknown>, ativo?: boolean): Observable<ContaBancaria> {
    return this.http.put<ContaBancaria>(`${this.b(c)}/contas-bancarias/${id}`, corpo, { params: ativo === undefined ? {} : { ativo } });
  }
  extrato(c: string, id: string, de: string, ate: string): Observable<Extrato> {
    return this.http.get<Extrato>(`${this.b(c)}/contas-bancarias/${id}/extrato`, { params: { de, ate } });
  }
  conciliar(c: string, itens: { tipo: string; id: string }[], conciliada: boolean): Observable<unknown> {
    return this.http.post(`${this.b(c)}/conciliar`, { itens, conciliada });
  }

  lancamentos(c: string, q: Record<string, string | number | string[] | undefined>): Observable<PaginaResponse<LancamentoResumo>> {
    let parametros = new HttpParams();
    for (const [k, v] of Object.entries(q)) {
      if (v === undefined || v === '' || v === null) continue;
      parametros = Array.isArray(v) ? v.reduce((p, x) => p.append(k, x), parametros) : parametros.set(k, String(v));
    }
    return this.http.get<PaginaResponse<LancamentoResumo>>(`${this.b(c)}/lancamentos`, { params: parametros });
  }
  lancamento(c: string, id: string): Observable<LancamentoDetalhe> { return this.http.get<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}`); }
  cadastrarLancamento(c: string, corpo: LancamentoRequest): Observable<LancamentoDetalhe[]> { return this.http.post<LancamentoDetalhe[]>(`${this.b(c)}/lancamentos`, corpo); }
  atualizarLancamento(c: string, id: string, corpo: LancamentoRequest): Observable<LancamentoDetalhe> { return this.http.put<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}`, corpo); }
  aprovar(c: string, id: string, aprovar: boolean, observacao: string | null): Observable<LancamentoDetalhe> {
    return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/${aprovar ? 'aprovar' : 'reprovar'}`, { observacao });
  }
  cancelarLancamento(c: string, id: string, motivo: string): Observable<LancamentoDetalhe> { return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/cancelar`, { motivo }); }
  calcularEncargos(c: string, id: string, data: string): Observable<CalculoEncargos> { return this.http.get<CalculoEncargos>(`${this.b(c)}/lancamentos/${id}/encargos`, { params: { data } }); }
  baixar(c: string, id: string, corpo: Record<string, unknown>): Observable<LancamentoDetalhe> { return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/baixas`, corpo); }
  estornarBaixa(c: string, idBaixa: string, motivo: string): Observable<LancamentoDetalhe> {
    return this.http.post<LancamentoDetalhe>(`${this.b(c)}/baixas/${idBaixa}/estornar`, { motivo });
  }
  // fechamento mensal
  fechamento(c: string): Observable<SituacaoFechamento> { return this.http.get<SituacaoFechamento>(`${this.b(c)}/fechamento`); }
  verificarFechamento(c: string, ate: string): Observable<Verificacao> { return this.http.get<Verificacao>(`${this.b(c)}/fechamento/verificar`, { params: { ate } }); }
  fechar(c: string, ate: string, observacoes: string | null): Observable<SituacaoFechamento> { return this.http.post<SituacaoFechamento>(`${this.b(c)}/fechamento`, { ate, observacoes }); }
  reabrir(c: string, ate: string | null, motivo: string): Observable<SituacaoFechamento> { return this.http.post<SituacaoFechamento>(`${this.b(c)}/fechamento/reabrir`, { ate, motivo }); }

  // extrato OFX e conciliação
  importarExtrato(c: string, idConta: string, arquivo: File, ignorarConferenciaConta = false): Observable<ResultadoImportacao> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<ResultadoImportacao>(`${this.b(c)}/contas-bancarias/${idConta}/extratos`, form, { params: { ignorarConferenciaConta } });
  }
  linhasExtrato(c: string, idConta: string, situacao?: string): Observable<LinhaExtrato[]> {
    return this.http.get<LinhaExtrato[]>(`${this.b(c)}/contas-bancarias/${idConta}/linhas-extrato`, { params: situacao ? { situacao } : {} });
  }
  conciliarLinha(c: string, id: string, tipo: string, idMovimentacao: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/conciliar`, { tipo, idMovimentacao }); }
  desfazerConciliacaoLinha(c: string, id: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/desfazer-conciliacao`, null); }
  ignorarLinha(c: string, id: string, observacao: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/ignorar`, { observacao }); }
  lancarLinha(c: string, id: string, corpo: Record<string, unknown>): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/lancar`, corpo); }

  // retorno bancário (CNAB 240)
  processarRetorno(c: string, idContaBancaria: string, arquivo: File): Observable<ArquivoRetorno> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<ArquivoRetorno>(`${this.cobranca(c)}/retornos-bancarios`, form, { params: { idContaBancaria } });
  }
  arquivosRetorno(c: string): Observable<ArquivoRetorno[]> { return this.http.get<ArquivoRetorno[]>(`${this.cobranca(c)}/retornos-bancarios`); }
  itensRetorno(c: string, idArquivo: string): Observable<ItemRetorno[]> { return this.http.get<ItemRetorno[]>(`${this.cobranca(c)}/retornos-bancarios/${idArquivo}/itens`); }
  itensRetornoPendentes(c: string): Observable<ItemRetorno[]> { return this.http.get<ItemRetorno[]>(`${this.cobranca(c)}/itens-retorno/pendentes`); }
  baixarItemRetorno(c: string, id: string, idLancamento: string): Observable<ItemRetorno> { return this.http.post<ItemRetorno>(`${this.cobranca(c)}/itens-retorno/${id}/baixar`, { idLancamento }); }
  ignorarItemRetorno(c: string, id: string, observacao: string): Observable<ItemRetorno> { return this.http.post<ItemRetorno>(`${this.cobranca(c)}/itens-retorno/${id}/ignorar`, { observacao }); }

  transferencias(c: string): Observable<Transferencia[]> { return this.http.get<Transferencia[]>(`${this.b(c)}/transferencias`); }
  transferencia(c: string, corpo: Record<string, unknown>): Observable<Transferencia> { return this.http.post<Transferencia>(`${this.b(c)}/transferencias`, corpo); }
  estornarTransferencia(c: string, id: string, motivo: string): Observable<Transferencia> { return this.http.post<Transferencia>(`${this.b(c)}/transferencias/${id}/estornar`, { motivo }); }
  orcamento(c: string, ano: number): Observable<Orcamento> { return this.http.get<Orcamento>(`${this.b(c)}/orcamento/${ano}`); }
  salvarOrcamento(c: string, ano: number, celulas: { idContaPlano: string; mes: number; valor: number }[]): Observable<Orcamento> {
    return this.http.put<Orcamento>(`${this.b(c)}/orcamento/${ano}`, { celulas });
  }

  fornecedores(busca: string, pagina = 0, tamanho = 20): Observable<PaginaResponse<Fornecedor>> {
    return this.http.get<PaginaResponse<Fornecedor>>('/api/v1/fornecedores', { params: { busca, pagina, tamanho } });
  }
  salvarFornecedor(corpo: Record<string, unknown>, id?: string, ativo?: boolean): Observable<Fornecedor> {
    return id
      ? this.http.put<Fornecedor>(`/api/v1/fornecedores/${id}`, corpo, { params: ativo === undefined ? {} : { ativo } })
      : this.http.post<Fornecedor>('/api/v1/fornecedores', corpo);
  }

  rateios(c: string): Observable<Rateio[]> { return this.http.get<Rateio[]>(`${this.cobranca(c)}/rateios`); }
  simularRateio(c: string, corpo: RateioRequest): Observable<SimulacaoRateio> { return this.http.post<SimulacaoRateio>(`${this.cobranca(c)}/rateios/simular`, corpo); }
  gerarRateio(c: string, corpo: RateioRequest): Observable<Rateio> { return this.http.post<Rateio>(`${this.cobranca(c)}/rateios`, corpo); }
  cancelarRateio(c: string, id: string, motivo: string): Observable<Rateio> { return this.http.post<Rateio>(`${this.cobranca(c)}/rateios/${id}/cancelar`, { motivo }); }
  inadimplencia(c: string, dataBase?: string): Observable<Inadimplencia> { return this.http.get<Inadimplencia>(`${this.cobranca(c)}/inadimplencia`, { params: dataBase ? { dataBase } : {} }); }
  debitosUnidade(c: string, idUnidade: string): Observable<DebitosUnidade> { return this.http.get<DebitosUnidade>(`${this.cobranca(c)}/unidades/${idUnidade}/debitos`); }
  registrarNota(c: string, idUnidade: string, corpo: Record<string, unknown>): Observable<NotaCobranca> { return this.http.post<NotaCobranca>(`${this.cobranca(c)}/unidades/${idUnidade}/notas`, corpo); }
  acordos(c: string): Observable<Acordo[]> { return this.http.get<Acordo[]>(`${this.cobranca(c)}/acordos`); }
  cadastrarAcordo(c: string, corpo: Record<string, unknown>): Observable<Acordo> { return this.http.post<Acordo>(`${this.cobranca(c)}/acordos`, corpo); }
  cancelarAcordo(c: string, id: string, motivo: string): Observable<Acordo> { return this.http.post<Acordo>(`${this.cobranca(c)}/acordos/${id}/cancelar`, { motivo }); }
}

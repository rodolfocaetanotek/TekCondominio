import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';

export interface ContaPlano { id: string; codigo: string; nome: string; natureza: 'INCOME' | 'EXPENSE'; analitica: boolean; sistema: boolean; ativo: boolean; }
export interface CentroCusto { id: string; nome: string; ativo: boolean; }
export interface ContaBancaria { id: string; nome: string; tipo: string; codigoBanco?: string; agencia?: string; numeroConta?: string; saldoInicial: number; dataAbertura: string; ativo: boolean; saldo: number; }
export interface ParametrosFinanceiros { percentualMulta: number; percentualJurosMensal: number; diaVencimento: number; exigeAprovacaoContasPagar: boolean; }
export interface Supplier { id: string; name: string; document?: string; category?: string; email?: string; phone?: string; contactName?: string; notes?: string; active: boolean; }

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

export interface Allocation { id: string; competence: string; description: string; accountCode?: string; accountName?: string; method: string; baseAmount: number; dueDate: string; unitsCount: number; generatedAmount: number; status: string; paidCount: number; receivedAmount: number; cancelReason?: string; }
export interface AllocationRequest { competence: string; description: string; chartAccountId: string; method: string; amount: number | null; dueDate: string | null; consumption?: Record<string, number>; }
export interface AllocationPreview { competence: string; dueDate: string; total: number; units: number; unitsWithoutResponsible: number; lines: { unitId: string; unitLabel: string; idealFraction: number; responsibleName?: string; amount: number }[]; }
export interface DelinquencyRow { unitId: string; unitLabel: string; responsibleName?: string; responsibleEmail?: string; responsiblePhone?: string; overdueCount: number; principal: number; charges: number; total: number; oldestDueDate: string; maxDaysLate: number; lastContactDate?: string; }
export interface Delinquency { asOf: string; totalPrincipal: number; totalCharges: number; total: number; issuedLast12Months: number; delinquencyRate: number; unitsInDebt: number; activeUnits: number; rows: DelinquencyRow[]; }
export interface DebtLine { entryId: string; description: string; competence: string; dueDate: string; amount: number; paid: number; remaining: number; status: string; daysLate: number; fine: number; interest: number; updatedTotal: number; }
export interface Note { id: string; contactType: string; contactDate: string; note: string; createdByName?: string; createdAt: string; }
export interface Agreement { id: string; unitId: string; unitLabel?: string; originalAmount: number; chargesAmount: number; discount: number; totalAmount: number; installments: number; firstDueDate: string; status: string; paidInstallments: number; notes?: string; cancelReason?: string; createdAt: string; installmentEntries: LancamentoResumo[]; }
export interface UnitDebt { unitId: string; unitLabel: string; responsibleName?: string; asOf: string; openPrincipal: number; openCharges: number; openTotal: number; lines: DebtLine[]; notes: Note[]; agreements: Agreement[]; }

/** Fotografia gravada no fechamento: chaves em inglês por serem dado histórico persistido. */
export interface FotografiaFechamento { income: string; expense: string; unreconciledMovements: number; balances: { account: string; balance: string; reconciled: string }[]; }
export interface ItemHistorico { id: string; acao: 'CLOSE' | 'REOPEN'; fechadoAte?: string; fechadoAteAnterior?: string; observacoes?: string; fotografia?: FotografiaFechamento; criadoEm: string; nomeCriadoPor?: string; }
export interface SituacaoFechamento { fechadoAte?: string; sugestaoAte?: string; historico: ItemHistorico[]; }
export interface SaldoConta { id: string; nome: string; saldo: number; saldoConciliado: number; }
export interface Verificacao { de: string; ate: string; movimentacoesNaoConciliadas: number; aprovacoesPendentes: number; receitas: number; despesas: number; saldos: SaldoConta[]; }
export interface ResultadoImportacao { idImportacao: string; nomeArquivo: string; inicioPeriodo?: string; fimPeriodo?: string; linhasArquivo: number; linhasNovas: number; duplicadas: number; conciliadasAutomaticamente: number; pendentes: number; }
export interface Sugestao { tipo: string; id: string; data: string; valor: number; descricao: string; }
export interface LinhaExtrato { id: string; fitId: string; dataLancamento: string; valor: number; tipoTransacao?: string; historico?: string; situacao: 'UNMATCHED' | 'MATCHED' | 'IGNORED'; tipoConciliado?: string; idConciliado?: string; observacao?: string; sugestoes: Sugestao[]; }
export interface ReturnFile { id: string; bankAccountId: string; fileName: string; bankCode: string; generatedOn?: string; itemsTotal: number; settled: number; pending: number; createdAt: string; createdByName?: string; }
export interface ReturnItem { id: string; fileId: string; sequence: number; movementCode: string; movementLabel: string; ourNumber?: string; yourNumber?: string; dueDate?: string; faceValue: number; fees: number; discount: number; paidAmount: number; bankFee: number; occurrenceDate?: string; creditDate?: string; status: 'SETTLED' | 'PENDING' | 'INFO' | 'IGNORED'; entryId?: string; entryDescription?: string; unitLabel?: string; message?: string; }

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}/financeiro`;
  }
  private billing(c: string): string {
    return `/api/v1/condominios/${c}/billing`;
  }

  chartAccounts(): Observable<ContaPlano[]> { return this.http.get<ContaPlano[]>('/api/v1/financeiro/plano-contas'); }
  createChartAccount(body: { codigo: string; nome: string; natureza: string }): Observable<ContaPlano> { return this.http.post<ContaPlano>('/api/v1/financeiro/plano-contas', body); }
  settings(c: string): Observable<ParametrosFinanceiros> { return this.http.get<ParametrosFinanceiros>(`${this.b(c)}/parametros`); }
  saveSettings(c: string, s: ParametrosFinanceiros): Observable<ParametrosFinanceiros> { return this.http.put<ParametrosFinanceiros>(`${this.b(c)}/parametros`, s); }
  costCenters(c: string): Observable<CentroCusto[]> { return this.http.get<CentroCusto[]>(`${this.b(c)}/centros-custo`); }
  saveCostCenter(c: string, nome: string, id?: string, ativo?: boolean): Observable<CentroCusto> {
    return id
      ? this.http.put<CentroCusto>(`${this.b(c)}/centros-custo/${id}`, { nome }, { params: ativo === undefined ? {} : { ativo } })
      : this.http.post<CentroCusto>(`${this.b(c)}/centros-custo`, { nome });
  }
  bankAccounts(c: string): Observable<ContaBancaria[]> { return this.http.get<ContaBancaria[]>(`${this.b(c)}/contas-bancarias`); }
  createBankAccount(c: string, body: Record<string, unknown>): Observable<ContaBancaria> { return this.http.post<ContaBancaria>(`${this.b(c)}/contas-bancarias`, body); }
  updateBankAccount(c: string, id: string, body: Record<string, unknown>, ativo?: boolean): Observable<ContaBancaria> {
    return this.http.put<ContaBancaria>(`${this.b(c)}/contas-bancarias/${id}`, body, { params: ativo === undefined ? {} : { ativo } });
  }
  statement(c: string, id: string, de: string, ate: string): Observable<Extrato> {
    return this.http.get<Extrato>(`${this.b(c)}/contas-bancarias/${id}/extrato`, { params: { de, ate } });
  }
  reconcile(c: string, itens: { tipo: string; id: string }[], conciliada: boolean): Observable<unknown> {
    return this.http.post(`${this.b(c)}/conciliar`, { itens, conciliada });
  }

  entries(c: string, q: Record<string, string | number | string[] | undefined>): Observable<PaginaResponse<LancamentoResumo>> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(q)) {
      if (v === undefined || v === '' || v === null) continue;
      params = Array.isArray(v) ? v.reduce((p, x) => p.append(k, x), params) : params.set(k, String(v));
    }
    return this.http.get<PaginaResponse<LancamentoResumo>>(`${this.b(c)}/lancamentos`, { params });
  }
  entry(c: string, id: string): Observable<LancamentoDetalhe> { return this.http.get<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}`); }
  createEntry(c: string, body: LancamentoRequest): Observable<LancamentoDetalhe[]> { return this.http.post<LancamentoDetalhe[]>(`${this.b(c)}/lancamentos`, body); }
  updateEntry(c: string, id: string, body: LancamentoRequest): Observable<LancamentoDetalhe> { return this.http.put<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}`, body); }
  approve(c: string, id: string, approve: boolean, observacao: string | null): Observable<LancamentoDetalhe> {
    return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/${approve ? 'aprovar' : 'reprovar'}`, { observacao });
  }
  cancelEntry(c: string, id: string, motivo: string): Observable<LancamentoDetalhe> { return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/cancelar`, { motivo }); }
  quote(c: string, id: string, data: string): Observable<CalculoEncargos> { return this.http.get<CalculoEncargos>(`${this.b(c)}/lancamentos/${id}/encargos`, { params: { data } }); }
  pay(c: string, id: string, body: Record<string, unknown>): Observable<LancamentoDetalhe> { return this.http.post<LancamentoDetalhe>(`${this.b(c)}/lancamentos/${id}/baixas`, body); }
  reversePayment(c: string, idBaixa: string, motivo: string): Observable<LancamentoDetalhe> {
    return this.http.post<LancamentoDetalhe>(`${this.b(c)}/baixas/${idBaixa}/estornar`, { motivo });
  }
  // fechamento mensal
  closing(c: string): Observable<SituacaoFechamento> { return this.http.get<SituacaoFechamento>(`${this.b(c)}/fechamento`); }
  closingCheck(c: string, ate: string): Observable<Verificacao> { return this.http.get<Verificacao>(`${this.b(c)}/fechamento/verificar`, { params: { ate } }); }
  close(c: string, ate: string, observacoes: string | null): Observable<SituacaoFechamento> { return this.http.post<SituacaoFechamento>(`${this.b(c)}/fechamento`, { ate, observacoes }); }
  reopen(c: string, ate: string | null, motivo: string): Observable<SituacaoFechamento> { return this.http.post<SituacaoFechamento>(`${this.b(c)}/fechamento/reabrir`, { ate, motivo }); }

  // extrato OFX e conciliação
  importStatement(c: string, idConta: string, arquivo: File, ignorarConferenciaConta = false): Observable<ResultadoImportacao> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<ResultadoImportacao>(`${this.b(c)}/contas-bancarias/${idConta}/extratos`, form, { params: { ignorarConferenciaConta } });
  }
  statementLines(c: string, idConta: string, situacao?: string): Observable<LinhaExtrato[]> {
    return this.http.get<LinhaExtrato[]>(`${this.b(c)}/contas-bancarias/${idConta}/linhas-extrato`, { params: situacao ? { situacao } : {} });
  }
  matchLine(c: string, id: string, tipo: string, idMovimentacao: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/conciliar`, { tipo, idMovimentacao }); }
  unmatchLine(c: string, id: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/desfazer-conciliacao`, null); }
  ignoreLine(c: string, id: string, observacao: string): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/ignorar`, { observacao }); }
  registerLine(c: string, id: string, body: Record<string, unknown>): Observable<LinhaExtrato> { return this.http.post<LinhaExtrato>(`${this.b(c)}/linhas-extrato/${id}/lancar`, body); }

  // retorno bancário (CNAB 240)
  processReturn(c: string, bankAccountId: string, file: File): Observable<ReturnFile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ReturnFile>(`${this.billing(c)}/bank-returns`, form, { params: { bankAccountId } });
  }
  returnFiles(c: string): Observable<ReturnFile[]> { return this.http.get<ReturnFile[]>(`${this.billing(c)}/bank-returns`); }
  returnItems(c: string, fileId: string): Observable<ReturnItem[]> { return this.http.get<ReturnItem[]>(`${this.billing(c)}/bank-returns/${fileId}/items`); }
  pendingReturnItems(c: string): Observable<ReturnItem[]> { return this.http.get<ReturnItem[]>(`${this.billing(c)}/bank-return-items/pending`); }
  settleReturnItem(c: string, id: string, entryId: string): Observable<ReturnItem> { return this.http.post<ReturnItem>(`${this.billing(c)}/bank-return-items/${id}/settle`, { entryId }); }
  ignoreReturnItem(c: string, id: string, note: string): Observable<ReturnItem> { return this.http.post<ReturnItem>(`${this.billing(c)}/bank-return-items/${id}/ignore`, { note }); }

  transfers(c: string): Observable<Transferencia[]> { return this.http.get<Transferencia[]>(`${this.b(c)}/transferencias`); }
  transfer(c: string, body: Record<string, unknown>): Observable<Transferencia> { return this.http.post<Transferencia>(`${this.b(c)}/transferencias`, body); }
  reverseTransfer(c: string, id: string, motivo: string): Observable<Transferencia> { return this.http.post<Transferencia>(`${this.b(c)}/transferencias/${id}/estornar`, { motivo }); }
  budget(c: string, ano: number): Observable<Orcamento> { return this.http.get<Orcamento>(`${this.b(c)}/orcamento/${ano}`); }
  saveBudget(c: string, ano: number, celulas: { idContaPlano: string; mes: number; valor: number }[]): Observable<Orcamento> {
    return this.http.put<Orcamento>(`${this.b(c)}/orcamento/${ano}`, { celulas });
  }

  suppliers(search: string, page = 0, size = 20): Observable<PaginaResponse<Supplier>> {
    return this.http.get<PaginaResponse<Supplier>>('/api/v1/suppliers', { params: { search, pagina: page, tamanho: size } });
  }
  saveSupplier(body: Record<string, unknown>, id?: string, active?: boolean): Observable<Supplier> {
    return id
      ? this.http.put<Supplier>(`/api/v1/suppliers/${id}`, body, { params: active === undefined ? {} : { active } })
      : this.http.post<Supplier>('/api/v1/suppliers', body);
  }

  allocations(c: string): Observable<Allocation[]> { return this.http.get<Allocation[]>(`${this.billing(c)}/allocations`); }
  previewAllocation(c: string, body: AllocationRequest): Observable<AllocationPreview> { return this.http.post<AllocationPreview>(`${this.billing(c)}/allocations/preview`, body); }
  generateAllocation(c: string, body: AllocationRequest): Observable<Allocation> { return this.http.post<Allocation>(`${this.billing(c)}/allocations`, body); }
  cancelAllocation(c: string, id: string, reason: string): Observable<Allocation> { return this.http.post<Allocation>(`${this.billing(c)}/allocations/${id}/cancel`, { motivo: reason }); }
  delinquency(c: string, asOf?: string): Observable<Delinquency> { return this.http.get<Delinquency>(`${this.billing(c)}/delinquency`, { params: asOf ? { asOf } : {} }); }
  unitDebt(c: string, unitId: string): Observable<UnitDebt> { return this.http.get<UnitDebt>(`${this.billing(c)}/units/${unitId}/debt`); }
  addNote(c: string, unitId: string, body: Record<string, unknown>): Observable<Note> { return this.http.post<Note>(`${this.billing(c)}/units/${unitId}/notes`, body); }
  agreements(c: string): Observable<Agreement[]> { return this.http.get<Agreement[]>(`${this.billing(c)}/agreements`); }
  createAgreement(c: string, body: Record<string, unknown>): Observable<Agreement> { return this.http.post<Agreement>(`${this.billing(c)}/agreements`, body); }
  cancelAgreement(c: string, id: string, reason: string): Observable<Agreement> { return this.http.post<Agreement>(`${this.billing(c)}/agreements/${id}/cancel`, { motivo: reason }); }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { downloadFile } from '../../comum/baixar-arquivo';

export interface Documento { id: string; categoria: string; titulo: string; descricao?: string; nomeArquivo: string; tipoConteudo: string; tamanhoBytes: number; visibilidade: string; tipoEntidade?: string; idEntidade?: string; idUnidade?: string; arquivado: boolean; criadoEm: string; nomeCriadoPor?: string; }
export interface ResumoOcorrencia { id: string; numero: number; titulo: string; categoria: string; prioridade: string; situacao: string; idUnidade?: string; rotuloUnidade?: string; nomeSolicitante: string; nomeResponsavel?: string; prazo?: string; atrasada: boolean; criadoEm: string; resolvidaEm?: string; }
export interface InteracaoOcorrencia { id: string; nomeAutor: string; mensagem: string; situacaoAlterada?: string; interna: boolean; criadoEm: string; }
export interface DetalheOcorrencia { resumo: ResumoOcorrencia; descricao: string; solucao?: string; versao: number; interacoes: InteracaoOcorrencia[]; }
export interface EstatisticasOcorrencia { porSituacao: Record<string, number>; abertas: number; atrasadas: number; mediaHorasSolucao?: number; }
export interface Comunicado { id: string; titulo: string; texto: string; publicoAlvo: string; nomeBloco?: string; rotuloUnidade?: string; fixado: boolean; enviarEmail: boolean; dataExpiracao?: string; publicadoEm: string; arquivado: boolean; nomeCriadoPor?: string; quantidadeLeituras?: number; lido?: boolean; }
export interface ResumoAssembleia { id: string; titulo: string; tipo: string; primeiraConvocacaoEm: string; local: string; situacao: string; }
export interface ItemPauta { id: string; posicao: number; titulo: string; descricao?: string; deliberativo: boolean; deliberacao?: string; responsavel?: string; prazo?: string; apuracao: { sim: number; nao: number; abstencao: number; votos: number }; }
export interface Presenca { id: string; idUnidade: string; rotuloUnidade: string; nomePresente: string; porProcuracao: boolean; nomeProcurador?: string; registradoEm: string; }
export interface DetalheAssembleia { id: string; titulo: string; tipo: string; primeiraConvocacaoEm: string; segundaConvocacaoEm?: string; local: string; criterioVoto: string; situacao: string; convocadaEm?: string; encerradaEm?: string; motivoCancelamento?: string; ata?: string; versao: number; pauta: ItemPauta[]; presencas: Presenca[]; quorum: { unidadesPresentes: number; totalUnidades: number; fracaoPresente: number }; }
export interface Contrato { id: string; idFornecedor: string; nomeFornecedor: string; titulo: string; descricao?: string; valor: number; periodicidade: string; dataInicio: string; dataFim?: string; indiceReajuste?: string; mesReajuste?: number; diasAvisoPrevio: number; situacao: string; aVencer: boolean; vencido: boolean; dataEncerramento?: string; motivoEncerramento?: string; versao: number; historico: { alteracao: string; criadoEm: string; nomeCriadoPor?: string }[]; }
export interface CaixaEntrada { naoLidas: number; itens: { id: string; titulo: string; texto?: string; link?: string; lida: boolean; criadoEm: string }[]; }
export interface RelatorioTabela { titulo: string; subtitulo?: string; colunas: { chave: string; rotulo: string; tipo: string }[]; linhas: unknown[][]; totais?: unknown[]; observacoes?: string[]; }
export interface Painel {
  de: string; ate: string; condominios: number; unidades: number; moradores: number; financeiro: boolean; receitas: number; despesas: number;
  saldo: number; aReceberAberto: number; aPagarAberto: number; valorVencido: number; cobrancasVencidas: number; cobrancasEmitidas: number;
  indiceInadimplencia: number; serie: { mes: string; receitas: number; despesas: number }[];
  contasAPagarProximas: { id: string; idCondominio: string; nomeCondominio: string; descricao: string; dataVencimento: string; valor: number; aprovacao: string }[];
  ocorrenciasRecentes: { id: string; idCondominio: string; nomeCondominio: string; titulo: string; situacao: string; quando: string }[];
  comunicadosRecentes: { id: string; idCondominio: string; nomeCondominio: string; titulo: string; quando: string }[];
}

@Injectable({ providedIn: 'root' })
export class OpsApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  // documentos e anexos (anexo = documento com tipoEntidade/idEntidade)
  documents(c: string, q: Record<string, string> = {}): Observable<PaginaResponse<Documento>> { return this.http.get<PaginaResponse<Documento>>(`${this.b(c)}/documentos`, { params: { tamanho: '100', ...q } }); }
  upload(c: string, file: File, meta: Record<string, string>): Observable<Documento> {
    const form = new FormData();
    form.append('arquivo', file);
    return this.http.post<Documento>(`${this.b(c)}/documentos`, form, { params: meta });
  }
  download(c: string, id: string): Observable<void> { return downloadFile(this.http, `${this.b(c)}/documentos/${id}/baixar`); }
  archiveDocument(c: string, id: string): Observable<Documento> { return this.http.post<Documento>(`${this.b(c)}/documentos/${id}/arquivar`, null); }

  // ocorrências
  occurrences(c: string, q: Record<string, string | string[]>): Observable<PaginaResponse<ResumoOcorrencia>> { return this.http.get<PaginaResponse<ResumoOcorrencia>>(`${this.b(c)}/ocorrencias`, { params: q }); }
  occurrenceStats(c: string): Observable<EstatisticasOcorrencia> { return this.http.get<EstatisticasOcorrencia>(`${this.b(c)}/ocorrencias/estatisticas`); }
  occurrence(c: string, id: string): Observable<DetalheOcorrencia> { return this.http.get<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}`); }
  createOccurrence(c: string, body: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias`, body); }
  updateOccurrence(c: string, id: string, body: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.put<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}`, body); }
  occurrenceStatus(c: string, id: string, body: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}/situacao`, body); }
  comment(c: string, id: string, mensagem: string, interna: boolean): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}/interacoes`, { mensagem, interna }); }

  // comunicados
  announcements(c: string, arquivados = false): Observable<Comunicado[]> { return this.http.get<Comunicado[]>(`${this.b(c)}/comunicados`, { params: { arquivados } }); }
  publish(c: string, body: Record<string, unknown>): Observable<Comunicado> { return this.http.post<Comunicado>(`${this.b(c)}/comunicados`, body); }
  archiveAnnouncement(c: string, id: string): Observable<Comunicado> { return this.http.post<Comunicado>(`${this.b(c)}/comunicados/${id}/arquivar`, null); }
  readAnnouncement(c: string, id: string): Observable<void> { return this.http.post<void>(`${this.b(c)}/comunicados/${id}/marcar-lido`, null); }

  // assembleias
  assemblies(c: string): Observable<ResumoAssembleia[]> { return this.http.get<ResumoAssembleia[]>(`${this.b(c)}/assembleias`); }
  assembly(c: string, id: string): Observable<DetalheAssembleia> { return this.http.get<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}`); }
  saveAssembly(c: string, body: Record<string, unknown>, id?: string): Observable<DetalheAssembleia> {
    return id ? this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}`, body) : this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias`, body);
  }
  assemblyAction(c: string, id: string, acao: 'convocar' | 'iniciar' | 'encerrar'): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/${acao}`, null); }
  cancelAssembly(c: string, id: string, motivo: string): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/cancelar`, { motivo }); }
  attend(c: string, id: string, body: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/presencas`, body); }
  removeAttendance(c: string, id: string, idPresenca: string): Observable<DetalheAssembleia> { return this.http.delete<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/presencas/${idPresenca}`); }
  vote(c: string, id: string, body: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/votos`, body); }
  deliberate(c: string, id: string, idItemPauta: string, body: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/pauta/${idItemPauta}/deliberacao`, body); }
  saveMinutes(c: string, id: string, ata: string): Observable<DetalheAssembleia> { return this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/ata`, { ata }); }
  minutesDraft(c: string, id: string): Observable<{ ata: string }> { return this.http.get<{ ata: string }>(`${this.b(c)}/assembleias/${id}/rascunho-ata`); }

  // contratos
  contracts(c: string): Observable<Contrato[]> { return this.http.get<Contrato[]>(`${this.b(c)}/contratos`); }
  contract(c: string, id: string): Observable<Contrato> { return this.http.get<Contrato>(`${this.b(c)}/contratos/${id}`); }
  saveContract(c: string, body: Record<string, unknown>, id?: string): Observable<Contrato> {
    return id ? this.http.put<Contrato>(`${this.b(c)}/contratos/${id}`, body) : this.http.post<Contrato>(`${this.b(c)}/contratos`, body);
  }
  terminateContract(c: string, id: string, body: Record<string, unknown>): Observable<Contrato> { return this.http.post<Contrato>(`${this.b(c)}/contratos/${id}/encerrar`, body); }

  // notificações, painel, relatórios
  inbox(): Observable<CaixaEntrada> { return this.http.get<CaixaEntrada>('/api/v1/notificacoes'); }
  readNotification(id: string): Observable<void> { return this.http.post<void>(`/api/v1/notificacoes/${id}/marcar-lida`, null); }
  readAllNotifications(): Observable<void> { return this.http.post<void>('/api/v1/notificacoes/marcar-todas-lidas', null); }
  dashboard(q: Record<string, string>): Observable<Painel> { return this.http.get<Painel>('/api/v1/painel', { params: q }); }
  report(c: string, tipo: string, q: Record<string, string>): Observable<RelatorioTabela> { return this.http.get<RelatorioTabela>(`${this.b(c)}/relatorios/${tipo}`, { params: q }); }
  exportReport(c: string, tipo: string, q: Record<string, string>): Observable<void> { return downloadFile(this.http, `${this.b(c)}/relatorios/${tipo}/exportar`, q); }
}

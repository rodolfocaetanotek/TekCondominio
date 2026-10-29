import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaginaResponse } from '../../core/api/api.models';
import { baixarArquivo } from '../../comum/baixar-arquivo';

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
export class OperacaoApi {
  private readonly http = inject(HttpClient);
  private b(c: string): string {
    return `/api/v1/condominios/${c}`;
  }

  // documentos e anexos (anexo = documento com tipoEntidade/idEntidade)
  documentos(c: string, q: Record<string, string> = {}): Observable<PaginaResponse<Documento>> { return this.http.get<PaginaResponse<Documento>>(`${this.b(c)}/documentos`, { params: { tamanho: '100', ...q } }); }
  enviarArquivo(c: string, arquivo: File, meta: Record<string, string>): Observable<Documento> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<Documento>(`${this.b(c)}/documentos`, form, { params: meta });
  }
  baixar(c: string, id: string): Observable<void> { return baixarArquivo(this.http, `${this.b(c)}/documentos/${id}/baixar`); }
  arquivarDocumento(c: string, id: string): Observable<Documento> { return this.http.post<Documento>(`${this.b(c)}/documentos/${id}/arquivar`, null); }

  // ocorrências
  ocorrencias(c: string, q: Record<string, string | string[]>): Observable<PaginaResponse<ResumoOcorrencia>> { return this.http.get<PaginaResponse<ResumoOcorrencia>>(`${this.b(c)}/ocorrencias`, { params: q }); }
  estatisticasOcorrencias(c: string): Observable<EstatisticasOcorrencia> { return this.http.get<EstatisticasOcorrencia>(`${this.b(c)}/ocorrencias/estatisticas`); }
  ocorrencia(c: string, id: string): Observable<DetalheOcorrencia> { return this.http.get<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}`); }
  cadastrarOcorrencia(c: string, corpo: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias`, corpo); }
  atualizarOcorrencia(c: string, id: string, corpo: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.put<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}`, corpo); }
  situacaoOcorrencia(c: string, id: string, corpo: Record<string, unknown>): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}/situacao`, corpo); }
  comentar(c: string, id: string, mensagem: string, interna: boolean): Observable<DetalheOcorrencia> { return this.http.post<DetalheOcorrencia>(`${this.b(c)}/ocorrencias/${id}/interacoes`, { mensagem, interna }); }

  // comunicados
  comunicados(c: string, arquivados = false): Observable<Comunicado[]> { return this.http.get<Comunicado[]>(`${this.b(c)}/comunicados`, { params: { arquivados } }); }
  publicar(c: string, corpo: Record<string, unknown>): Observable<Comunicado> { return this.http.post<Comunicado>(`${this.b(c)}/comunicados`, corpo); }
  arquivarComunicado(c: string, id: string): Observable<Comunicado> { return this.http.post<Comunicado>(`${this.b(c)}/comunicados/${id}/arquivar`, null); }
  marcarComunicadoLido(c: string, id: string): Observable<void> { return this.http.post<void>(`${this.b(c)}/comunicados/${id}/marcar-lido`, null); }

  // assembleias
  assembleias(c: string): Observable<ResumoAssembleia[]> { return this.http.get<ResumoAssembleia[]>(`${this.b(c)}/assembleias`); }
  assembleia(c: string, id: string): Observable<DetalheAssembleia> { return this.http.get<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}`); }
  salvarAssembleia(c: string, corpo: Record<string, unknown>, id?: string): Observable<DetalheAssembleia> {
    return id ? this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}`, corpo) : this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias`, corpo);
  }
  acaoAssembleia(c: string, id: string, acao: 'convocar' | 'iniciar' | 'encerrar'): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/${acao}`, null); }
  cancelarAssembleia(c: string, id: string, motivo: string): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/cancelar`, { motivo }); }
  registrarPresenca(c: string, id: string, corpo: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/presencas`, corpo); }
  removerPresenca(c: string, id: string, idPresenca: string): Observable<DetalheAssembleia> { return this.http.delete<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/presencas/${idPresenca}`); }
  votar(c: string, id: string, corpo: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.post<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/votos`, corpo); }
  deliberar(c: string, id: string, idItemPauta: string, corpo: Record<string, unknown>): Observable<DetalheAssembleia> { return this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/pauta/${idItemPauta}/deliberacao`, corpo); }
  salvarAta(c: string, id: string, ata: string): Observable<DetalheAssembleia> { return this.http.put<DetalheAssembleia>(`${this.b(c)}/assembleias/${id}/ata`, { ata }); }
  rascunhoAta(c: string, id: string): Observable<{ ata: string }> { return this.http.get<{ ata: string }>(`${this.b(c)}/assembleias/${id}/rascunho-ata`); }

  // contratos
  contratos(c: string): Observable<Contrato[]> { return this.http.get<Contrato[]>(`${this.b(c)}/contratos`); }
  contrato(c: string, id: string): Observable<Contrato> { return this.http.get<Contrato>(`${this.b(c)}/contratos/${id}`); }
  salvarContrato(c: string, corpo: Record<string, unknown>, id?: string): Observable<Contrato> {
    return id ? this.http.put<Contrato>(`${this.b(c)}/contratos/${id}`, corpo) : this.http.post<Contrato>(`${this.b(c)}/contratos`, corpo);
  }
  encerrarContrato(c: string, id: string, corpo: Record<string, unknown>): Observable<Contrato> { return this.http.post<Contrato>(`${this.b(c)}/contratos/${id}/encerrar`, corpo); }

  // notificações, painel, relatórios
  caixaEntrada(): Observable<CaixaEntrada> { return this.http.get<CaixaEntrada>('/api/v1/notificacoes'); }
  marcarNotificacaoLida(id: string): Observable<void> { return this.http.post<void>(`/api/v1/notificacoes/${id}/marcar-lida`, null); }
  marcarTodasNotificacoesLidas(): Observable<void> { return this.http.post<void>('/api/v1/notificacoes/marcar-todas-lidas', null); }
  painel(q: Record<string, string>): Observable<Painel> { return this.http.get<Painel>('/api/v1/painel', { params: q }); }
  relatorio(c: string, tipo: string, q: Record<string, string>): Observable<RelatorioTabela> { return this.http.get<RelatorioTabela>(`${this.b(c)}/relatorios/${tipo}`, { params: q }); }
  exportarRelatorio(c: string, tipo: string, q: Record<string, string>): Observable<void> { return baixarArquivo(this.http, `${this.b(c)}/relatorios/${tipo}/exportar`, q); }
}

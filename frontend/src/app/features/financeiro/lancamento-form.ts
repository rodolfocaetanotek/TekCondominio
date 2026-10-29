import { MatDialog } from '@angular/material/dialog';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { hojeIso } from '../../comum/ui/datas';
import { DefinicaoCampo, abrirFormulario } from '../../comum/ui/formulario-dialog';
import { EstruturaService } from '../estrutura/estrutura.service';
import { LancamentoDetalhe, LancamentoRequest, FinanceiroApi } from './financeiro-api';

/**
 * Abre o formulário de receita/despesa (novo ou edição) com as opções carregadas da API.
 * Para novos lançamentos permite repetir mensalmente (parcelas/recorrência).
 */
export function abrirFormLancamento(dialog: MatDialog, api: FinanceiroApi, estrutura: EstruturaService, idCondominio: string,
                              tipo: 'INCOME' | 'EXPENSE', existente?: LancamentoDetalhe): Observable<unknown> {
  const natureza = tipo;
  return forkJoin({
    contas: api.contasPlano(),
    centros: api.centrosCusto(idCondominio),
    fornecedores: tipo === 'EXPENSE' ? api.fornecedores('', 0, 100).pipe(map((p) => p.conteudo)) : of([]),
    unidades: tipo === 'INCOME' ? estrutura.unidades(idCondominio, { pagina: 0, tamanho: 100, ativo: 'true' }).pipe(map((p) => p.conteudo)) : of([]),
  }).pipe(
    switchMap(({ contas, centros, fornecedores, unidades }) => {
      const s = existente?.resumo;
      const campos: DefinicaoCampo[] = [
        { chave: 'descricao', rotulo: 'Descrição', obrigatorio: true, valor: s?.descricao, tamanhoMaximo: 200 },
        {
          chave: 'idContaPlano', rotulo: 'Conta do plano de contas', tipo: 'select', obrigatorio: true, valor: existente?.idContaPlano,
          opcoes: contas.filter((a) => a.analitica && a.ativo && a.natureza === natureza).map((a) => ({ valor: a.id, rotulo: `${a.codigo} — ${a.nome}` })),
        },
        { chave: 'valor', rotulo: 'Valor (R$)', tipo: 'number', obrigatorio: true, valor: s?.valor, colunas: 6, min: 0.01 },
        { chave: 'dataVencimento', rotulo: 'Vencimento', tipo: 'date', obrigatorio: true, valor: s?.dataVencimento ?? hojeIso(), colunas: 6 },
        tipo === 'EXPENSE'
          ? { chave: 'idFornecedor', rotulo: 'Fornecedor', tipo: 'select', valor: existente?.idFornecedor ?? null, colunas: 6,
              opcoes: [{ valor: null, rotulo: '—' }, ...fornecedores.filter((x) => x.ativo).map((x) => ({ valor: x.id, rotulo: x.nome }))] }
          : { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', valor: existente?.idUnidade ?? null, colunas: 6,
              opcoes: [{ valor: null, rotulo: '— (receita geral)' }, ...unidades.map((u) => ({ valor: u.id, rotulo: u.rotulo }))] },
        { chave: 'numeroDocumento', rotulo: tipo === 'EXPENSE' ? 'Nº da nota/boleto' : 'Documento', valor: s?.numeroDocumento, colunas: 6, tamanhoMaximo: 40 },
        { chave: 'idCentroCusto', rotulo: 'Centro de custo', tipo: 'select', valor: existente?.idCentroCusto ?? null, colunas: 6,
          opcoes: [{ valor: null, rotulo: '—' }, ...centros.filter((x) => x.ativo).map((x) => ({ valor: x.id, rotulo: x.nome }))] },
        { chave: 'competencia', rotulo: 'Competência', tipo: 'date', valor: s?.competencia ?? null, colunas: 6, dica: 'Mês de referência (padrão: vencimento)' },
        { chave: 'observacoes', rotulo: 'Observações', tipo: 'textarea', valor: existente?.observacoes, tamanhoMaximo: 500 },
      ];
      if (!existente) {
        campos.push({ chave: 'repeticoes', rotulo: 'Repetir mensalmente (nº de parcelas)', tipo: 'number', valor: 1, colunas: 6, min: 1,
          dica: 'Ex.: 12 para uma despesa fixa do ano' });
      }
      return abrirFormulario<unknown>(dialog, {
        titulo: (existente ? 'Editar ' : 'Nova ') + (tipo === 'EXPENSE' ? 'despesa (conta a pagar)' : 'receita (conta a receber)'),
        campos,
        enviar: (v) => {
          const corpo: LancamentoRequest = {
            natureza: tipo, descricao: String(v['descricao']), idContaPlano: String(v['idContaPlano']),
            idCentroCusto: (v['idCentroCusto'] as string) ?? null, idFornecedor: (v['idFornecedor'] as string) ?? null,
            idUnidade: (v['idUnidade'] as string) ?? null, idPessoa: existente?.idPessoa ?? null, valor: Number(v['valor']),
            dataVencimento: String(v['dataVencimento']), competencia: (v['competencia'] as string) ?? null,
            numeroDocumento: (v['numeroDocumento'] as string) ?? null, observacoes: (v['observacoes'] as string) ?? null,
            repeticoes: v['repeticoes'] ? Number(v['repeticoes']) : null, versao: existente?.versao,
          };
          return existente ? api.atualizarLancamento(idCondominio, existente.resumo.id, corpo) : api.cadastrarLancamento(idCondominio, corpo);
        },
      }, '680px');
    }),
  );
}

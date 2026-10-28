import { MatDialog } from '@angular/material/dialog';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { todayIso } from '../../shared/ui/dates';
import { FieldDef, openForm } from '../../shared/ui/form-dialog';
import { StructureService } from '../structure/structure.service';
import { LancamentoDetalhe, LancamentoRequest, FinanceApi } from './finance-api';

/**
 * Abre o formulário de receita/despesa (novo ou edição) com as opções carregadas da API.
 * Para novos lançamentos permite repetir mensalmente (parcelas/recorrência).
 */
export function openEntryForm(dialog: MatDialog, api: FinanceApi, structure: StructureService, condominiumId: string,
                              kind: 'INCOME' | 'EXPENSE', existing?: LancamentoDetalhe): Observable<unknown> {
  const nature = kind;
  return forkJoin({
    accounts: api.chartAccounts(),
    centers: api.costCenters(condominiumId),
    suppliers: kind === 'EXPENSE' ? api.suppliers('', 0, 100).pipe(map((p) => p.conteudo)) : of([]),
    units: kind === 'INCOME' ? structure.units(condominiumId, { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.conteudo)) : of([]),
  }).pipe(
    switchMap(({ accounts, centers, suppliers, units }) => {
      const s = existing?.resumo;
      const fields: FieldDef[] = [
        { key: 'descricao', label: 'Descrição', required: true, value: s?.descricao, maxLength: 200 },
        {
          key: 'idContaPlano', label: 'Conta do plano de contas', type: 'select', required: true, value: existing?.idContaPlano,
          options: accounts.filter((a) => a.analitica && a.ativo && a.natureza === nature).map((a) => ({ value: a.id, label: `${a.codigo} — ${a.nome}` })),
        },
        { key: 'valor', label: 'Valor (R$)', type: 'number', required: true, value: s?.valor, span: 6, min: 0.01 },
        { key: 'dataVencimento', label: 'Vencimento', type: 'date', required: true, value: s?.dataVencimento ?? todayIso(), span: 6 },
        kind === 'EXPENSE'
          ? { key: 'idFornecedor', label: 'Fornecedor', type: 'select', value: existing?.idFornecedor ?? null, span: 6,
              options: [{ value: null, label: '—' }, ...suppliers.filter((x) => x.ativo).map((x) => ({ value: x.id, label: x.nome }))] }
          : { key: 'idUnidade', label: 'Unidade', type: 'select', value: existing?.idUnidade ?? null, span: 6,
              options: [{ value: null, label: '— (receita geral)' }, ...units.map((u) => ({ value: u.id, label: u.rotulo }))] },
        { key: 'numeroDocumento', label: kind === 'EXPENSE' ? 'Nº da nota/boleto' : 'Documento', value: s?.numeroDocumento, span: 6, maxLength: 40 },
        { key: 'idCentroCusto', label: 'Centro de custo', type: 'select', value: existing?.idCentroCusto ?? null, span: 6,
          options: [{ value: null, label: '—' }, ...centers.filter((x) => x.ativo).map((x) => ({ value: x.id, label: x.nome }))] },
        { key: 'competencia', label: 'Competência', type: 'date', value: s?.competencia ?? null, span: 6, hint: 'Mês de referência (padrão: vencimento)' },
        { key: 'observacoes', label: 'Observações', type: 'textarea', value: existing?.observacoes, maxLength: 500 },
      ];
      if (!existing) {
        fields.push({ key: 'repeticoes', label: 'Repetir mensalmente (nº de parcelas)', type: 'number', value: 1, span: 6, min: 1,
          hint: 'Ex.: 12 para uma despesa fixa do ano' });
      }
      return openForm<unknown>(dialog, {
        title: (existing ? 'Editar ' : 'Nova ') + (kind === 'EXPENSE' ? 'despesa (conta a pagar)' : 'receita (conta a receber)'),
        fields,
        submit: (v) => {
          const body: LancamentoRequest = {
            natureza: kind, descricao: String(v['descricao']), idContaPlano: String(v['idContaPlano']),
            idCentroCusto: (v['idCentroCusto'] as string) ?? null, idFornecedor: (v['idFornecedor'] as string) ?? null,
            idUnidade: (v['idUnidade'] as string) ?? null, idPessoa: existing?.idPessoa ?? null, valor: Number(v['valor']),
            dataVencimento: String(v['dataVencimento']), competencia: (v['competencia'] as string) ?? null,
            numeroDocumento: (v['numeroDocumento'] as string) ?? null, observacoes: (v['observacoes'] as string) ?? null,
            repeticoes: v['repeticoes'] ? Number(v['repeticoes']) : null, versao: existing?.versao,
          };
          return existing ? api.updateEntry(condominiumId, existing.resumo.id, body) : api.createEntry(condominiumId, body);
        },
      }, '680px');
    }),
  );
}

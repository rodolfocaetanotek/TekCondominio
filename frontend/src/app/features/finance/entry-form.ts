import { MatDialog } from '@angular/material/dialog';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { todayIso } from '../../shared/ui/dates';
import { FieldDef, openForm } from '../../shared/ui/form-dialog';
import { StructureService } from '../structure/structure.service';
import { EntryDetail, EntryRequest, FinanceApi } from './finance-api';

/**
 * Abre o formulário de receita/despesa (novo ou edição) com as opções carregadas da API.
 * Para novos lançamentos permite repetir mensalmente (parcelas/recorrência).
 */
export function openEntryForm(dialog: MatDialog, api: FinanceApi, structure: StructureService, condominiumId: string,
                              kind: 'INCOME' | 'EXPENSE', existing?: EntryDetail): Observable<unknown> {
  const nature = kind;
  return forkJoin({
    accounts: api.chartAccounts(),
    centers: api.costCenters(condominiumId),
    suppliers: kind === 'EXPENSE' ? api.suppliers('', 0, 100).pipe(map((p) => p.conteudo)) : of([]),
    units: kind === 'INCOME' ? structure.units(condominiumId, { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.conteudo)) : of([]),
  }).pipe(
    switchMap(({ accounts, centers, suppliers, units }) => {
      const s = existing?.summary;
      const fields: FieldDef[] = [
        { key: 'description', label: 'Descrição', required: true, value: s?.description, maxLength: 200 },
        {
          key: 'chartAccountId', label: 'Conta do plano de contas', type: 'select', required: true, value: existing?.chartAccountId,
          options: accounts.filter((a) => a.analytic && a.active && a.nature === nature).map((a) => ({ value: a.id, label: `${a.code} — ${a.name}` })),
        },
        { key: 'amount', label: 'Valor (R$)', type: 'number', required: true, value: s?.amount, span: 6, min: 0.01 },
        { key: 'dueDate', label: 'Vencimento', type: 'date', required: true, value: s?.dueDate ?? todayIso(), span: 6 },
        kind === 'EXPENSE'
          ? { key: 'supplierId', label: 'Fornecedor', type: 'select', value: existing?.supplierId ?? null, span: 6,
              options: [{ value: null, label: '—' }, ...suppliers.filter((x) => x.active).map((x) => ({ value: x.id, label: x.name }))] }
          : { key: 'unitId', label: 'Unidade', type: 'select', value: existing?.unitId ?? null, span: 6,
              options: [{ value: null, label: '— (receita geral)' }, ...units.map((u) => ({ value: u.id, label: u.label }))] },
        { key: 'documentNumber', label: kind === 'EXPENSE' ? 'Nº da nota/boleto' : 'Documento', value: s?.documentNumber, span: 6, maxLength: 40 },
        { key: 'costCenterId', label: 'Centro de custo', type: 'select', value: existing?.costCenterId ?? null, span: 6,
          options: [{ value: null, label: '—' }, ...centers.filter((x) => x.active).map((x) => ({ value: x.id, label: x.name }))] },
        { key: 'competence', label: 'Competência', type: 'date', value: s?.competence ?? null, span: 6, hint: 'Mês de referência (padrão: vencimento)' },
        { key: 'notes', label: 'Observações', type: 'textarea', value: existing?.notes, maxLength: 500 },
      ];
      if (!existing) {
        fields.push({ key: 'repeat', label: 'Repetir mensalmente (nº de parcelas)', type: 'number', value: 1, span: 6, min: 1,
          hint: 'Ex.: 12 para uma despesa fixa do ano' });
      }
      return openForm<unknown>(dialog, {
        title: (existing ? 'Editar ' : 'Nova ') + (kind === 'EXPENSE' ? 'despesa (conta a pagar)' : 'receita (conta a receber)'),
        fields,
        submit: (v) => {
          const body: EntryRequest = {
            kind, description: String(v['description']), chartAccountId: String(v['chartAccountId']),
            costCenterId: (v['costCenterId'] as string) ?? null, supplierId: (v['supplierId'] as string) ?? null,
            unitId: (v['unitId'] as string) ?? null, personId: existing?.personId ?? null, amount: Number(v['amount']),
            dueDate: String(v['dueDate']), competence: (v['competence'] as string) ?? null,
            documentNumber: (v['documentNumber'] as string) ?? null, notes: (v['notes'] as string) ?? null,
            repeat: v['repeat'] ? Number(v['repeat']) : null, version: existing?.version,
          };
          return existing ? api.updateEntry(condominiumId, existing.summary.id, body) : api.createEntry(condominiumId, body);
        },
      }, '680px');
    }),
  );
}

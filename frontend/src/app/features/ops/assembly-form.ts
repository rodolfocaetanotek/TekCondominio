import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { options } from '../../shared/labels';
import { openForm } from '../../shared/ui/form-dialog';
import { AssemblyDetail, OpsApi } from './ops-api';

/** Formulário de assembleia. Pauta: um item por linha; linhas iniciadas com "#" são informativas (sem votação). */
export function openAssemblyForm(dialog: MatDialog, api: OpsApi, c: string, existing?: AssemblyDetail): Observable<AssemblyDetail | undefined> {
  const toLocal = (iso?: string) => (iso ? new Date(new Date(iso).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : null);
  return openForm<AssemblyDetail>(dialog, {
    title: existing ? 'Editar assembleia' : 'Nova assembleia',
    fields: [
      { key: 'title', label: 'Título', required: true, value: existing?.title, span: 8, maxLength: 200 },
      { key: 'kind', label: 'Tipo', type: 'select', required: true, value: existing?.kind ?? 'ORDINARY', span: 4, options: options('assemblyKind') },
      { key: 'scheduledAt', label: '1ª convocação', type: 'datetime', required: true, value: toLocal(existing?.scheduledAt), span: 6 },
      { key: 'secondCallAt', label: '2ª convocação', type: 'datetime', value: toLocal(existing?.secondCallAt), span: 6 },
      { key: 'location', label: 'Local', required: true, value: existing?.location, span: 8, maxLength: 255 },
      { key: 'voteWeight', label: 'Critério de voto', type: 'select', required: true, value: existing?.voteWeight ?? 'UNIT', span: 4, options: options('voteWeight') },
      { key: 'agenda', label: 'Pauta (um item por linha)', type: 'textarea', required: true,
        value: existing?.agenda.map((i) => (i.voting ? '' : '# ') + i.title).join('\n'),
        hint: 'Inicie a linha com "#" para itens apenas informativos (sem votação).' },
    ],
    submit: (v) => {
      const agenda = String(v['agenda']).split('\n').map((l) => l.trim()).filter(Boolean)
        .map((l) => ({ title: l.replace(/^#\s*/, ''), voting: !l.startsWith('#') }));
      return api.saveAssembly(c, { ...v, agenda }, existing?.id);
    },
  }, '680px');
}

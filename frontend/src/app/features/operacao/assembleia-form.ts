import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { options } from '../../comum/rotulos';
import { openForm } from '../../comum/ui/formulario-dialog';
import { DetalheAssembleia, OpsApi } from './operacao-api';

/** Formulário de assembleia. Pauta: um item por linha; linhas iniciadas com "#" são informativas (sem votação). */
export function openAssemblyForm(dialog: MatDialog, api: OpsApi, c: string, existing?: DetalheAssembleia): Observable<DetalheAssembleia | undefined> {
  const toLocal = (iso?: string) => (iso ? new Date(new Date(iso).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : null);
  return openForm<DetalheAssembleia>(dialog, {
    title: existing ? 'Editar assembleia' : 'Nova assembleia',
    fields: [
      { key: 'titulo', label: 'Título', required: true, value: existing?.titulo, span: 8, maxLength: 200 },
      { key: 'tipo', label: 'Tipo', type: 'select', required: true, value: existing?.tipo ?? 'ORDINARY', span: 4, options: options('assemblyKind') },
      { key: 'primeiraConvocacaoEm', label: '1ª convocação', type: 'datetime', required: true, value: toLocal(existing?.primeiraConvocacaoEm), span: 6 },
      { key: 'segundaConvocacaoEm', label: '2ª convocação', type: 'datetime', value: toLocal(existing?.segundaConvocacaoEm), span: 6 },
      { key: 'local', label: 'Local', required: true, value: existing?.local, span: 8, maxLength: 255 },
      { key: 'criterioVoto', label: 'Critério de voto', type: 'select', required: true, value: existing?.criterioVoto ?? 'UNIT', span: 4, options: options('voteWeight') },
      { key: 'pauta', label: 'Pauta (um item por linha)', type: 'textarea', required: true,
        value: existing?.pauta.map((i) => (i.deliberativo ? '' : '# ') + i.titulo).join('\n'),
        hint: 'Inicie a linha com "#" para itens apenas informativos (sem votação).' },
    ],
    submit: (v) => {
      const pauta = String(v['pauta']).split('\n').map((l) => l.trim()).filter(Boolean)
        .map((l) => ({ titulo: l.replace(/^#\s*/, ''), deliberativo: !l.startsWith('#') }));
      return api.saveAssembly(c, { ...v, pauta }, existing?.id);
    },
  }, '680px');
}

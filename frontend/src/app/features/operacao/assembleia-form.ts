import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { opcoes } from '../../comum/rotulos';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DetalheAssembleia, OperacaoApi } from './operacao-api';

/** Formulário de assembleia. Pauta: um item por linha; linhas iniciadas com "#" são informativas (sem votação). */
export function abrirFormAssembleia(dialog: MatDialog, api: OperacaoApi, c: string, existente?: DetalheAssembleia): Observable<DetalheAssembleia | undefined> {
  const paraLocal = (iso?: string) => (iso ? new Date(new Date(iso).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : null);
  return abrirFormulario<DetalheAssembleia>(dialog, {
    titulo: existente ? 'Editar assembleia' : 'Nova assembleia',
    campos: [
      { chave: 'titulo', rotulo: 'Título', obrigatorio: true, valor: existente?.titulo, colunas: 8, tamanhoMaximo: 200 },
      { chave: 'tipo', rotulo: 'Tipo', tipo: 'select', obrigatorio: true, valor: existente?.tipo ?? 'ORDINARY', colunas: 4, opcoes: opcoes('assemblyKind') },
      { chave: 'primeiraConvocacaoEm', rotulo: '1ª convocação', tipo: 'datetime', obrigatorio: true, valor: paraLocal(existente?.primeiraConvocacaoEm), colunas: 6 },
      { chave: 'segundaConvocacaoEm', rotulo: '2ª convocação', tipo: 'datetime', valor: paraLocal(existente?.segundaConvocacaoEm), colunas: 6 },
      { chave: 'local', rotulo: 'Local', obrigatorio: true, valor: existente?.local, colunas: 8, tamanhoMaximo: 255 },
      { chave: 'criterioVoto', rotulo: 'Critério de voto', tipo: 'select', obrigatorio: true, valor: existente?.criterioVoto ?? 'UNIT', colunas: 4, opcoes: opcoes('voteWeight') },
      { chave: 'pauta', rotulo: 'Pauta (um item por linha)', tipo: 'textarea', obrigatorio: true,
        valor: existente?.pauta.map((i) => (i.deliberativo ? '' : '# ') + i.titulo).join('\n'),
        dica: 'Inicie a linha com "#" para itens apenas informativos (sem votação).' },
    ],
    enviar: (v) => {
      const pauta = String(v['pauta']).split('\n').map((l) => l.trim()).filter(Boolean)
        .map((l) => ({ titulo: l.replace(/^#\s*/, ''), deliberativo: !l.startsWith('#') }));
      return api.salvarAssembleia(c, { ...v, pauta }, existente?.id);
    },
  }, '680px');
}

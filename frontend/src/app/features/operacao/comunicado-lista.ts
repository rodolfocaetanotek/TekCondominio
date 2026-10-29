import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, forkJoin, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { pedirConfirmacao } from '../../comum/ui/confirmacao-dialog';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EstruturaService } from '../estrutura/estrutura.service';
import { Comunicado, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-comunicado-lista',
  imports: [DatePipe, MatButtonModule, MatIconModule, CabecalhoPagina, EstadoVazio, CondominioObrigatorio],
  template: `
    <div class="tc-page narrow">
      <tc-cabecalho-pagina sobretitulo="Comunicação" titulo="Comunicados" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) { <button mat-flat-button (click)="publicar()"><mat-icon>campaign</mat-icon> Novo comunicado</button> }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        @for (a of lista() ?? []; track a.id) {
          <article class="tc-card item" [class.unread]="!a.lido" (click)="marcarLido(a)">
            <header class="item-head">
              @if (a.fixado) { <mat-icon class="pin" aria-label="Fixado">push_pin</mat-icon> }
              <h3>{{ a.titulo }}</h3>
              <span class="tc-chip-soft">{{ a.publicoAlvo === 'ALL' ? 'Todos' : a.publicoAlvo === 'BUILDING' ? 'Bloco ' + a.nomeBloco : 'Unidade ' + a.rotuloUnidade }}</span>
            </header>
            <p class="body">{{ a.texto }}</p>
            <footer class="item-foot">
              <span class="meta tc-muted">
                {{ a.publicadoEm | date: 'dd/MM/yyyy HH:mm' }} · {{ a.nomeCriadoPor }}{{ a.enviarEmail ? ' · enviado por e-mail' : '' }}
                · {{ a.quantidadeLeituras }} leitura(s){{ a.dataExpiracao ? ' · até ' + a.dataExpiracao.slice(8, 10) + '/' + a.dataExpiracao.slice(5, 7) : '' }}
              </span>
              @if (podeEditar()) { <button mat-button (click)="arquivar(a, $event)"><mat-icon>archive</mat-icon> Arquivar</button> }
            </footer>
          </article>
        } @empty {
          <section class="tc-card"><tc-estado-vazio icone="campaign" titulo="Nenhum comunicado publicado" /></section>
        }
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 900px; }
    .item { display: grid; gap: 10px; margin-bottom: 12px; cursor: default; }
    .item.unread { border-left: 4px solid var(--mat-sys-primary); }
    .item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .item-head h3 { margin: 0; flex: 1 1 200px; min-width: 0; font: var(--mat-sys-title-medium); font-weight: 600; color: var(--tc-text); overflow-wrap: anywhere; }
    .pin { color: var(--tc-accent); }
    .body { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .item-foot { display: flex; align-items: center; gap: 8px 12px; flex-wrap: wrap; padding-top: 10px; border-top: 1px solid var(--tc-border); }
    .meta { flex: 1 1 240px; min-width: 0; font: var(--mat-sys-body-small); }
    .item-foot button { margin-left: auto; }
  `,
})
export class ComunicadoLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('ANNOUNCEMENT_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly lista = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.comunicados(c!).pipe(catchError(() => of([] as Comunicado[]))))));

  publicar(): void {
    const c = this.contexto.id()!;
    forkJoin({ blocos: this.estrutura.blocos(c), unidades: this.estrutura.unidades(c, { pagina: 0, tamanho: 100, ativo: 'true' }).pipe(map((p) => p.conteudo)) })
      .subscribe(({ blocos, unidades }) => abrirFormulario(this.dialog, {
        titulo: 'Novo comunicado',
        introducao: 'Os moradores com acesso ao portal e a equipe recebem notificação. Com "enviar por e-mail", os moradores cadastrados também recebem e-mail.',
        campos: [
          { chave: 'titulo', rotulo: 'Título', obrigatorio: true, tamanhoMaximo: 150 },
          { chave: 'texto', rotulo: 'Mensagem', tipo: 'textarea', obrigatorio: true, tamanhoMaximo: 8000 },
          { chave: 'publicoAlvo', rotulo: 'Público', tipo: 'select', valor: 'ALL', colunas: 4,
            opcoes: [{ valor: 'ALL', rotulo: 'Todos' }, { valor: 'BUILDING', rotulo: 'Um bloco' }, { valor: 'UNIT', rotulo: 'Uma unidade' }] },
          { chave: 'idBloco', rotulo: 'Bloco (se público = bloco)', tipo: 'select', valor: null, colunas: 4, opcoes: [{ valor: null, rotulo: '—' }, ...blocos.map((b) => ({ valor: b.id, rotulo: b.nome }))] },
          { chave: 'idUnidade', rotulo: 'Unidade (se público = unidade)', tipo: 'select', valor: null, colunas: 4, opcoes: [{ valor: null, rotulo: '—' }, ...unidades.map((u) => ({ valor: u.id, rotulo: u.rotulo }))] },
          { chave: 'dataExpiracao', rotulo: 'Exibir até', tipo: 'date', colunas: 6 },
          { chave: 'fixado', rotulo: 'Fixar no topo', tipo: 'checkbox' },
          { chave: 'enviarEmail', rotulo: 'Enviar também por e-mail', tipo: 'checkbox' },
        ],
        rotuloEnviar: 'Publicar',
        enviar: (v) => this.api.publicar(c, v),
      }, '680px').pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Comunicado publicado.'); this.recarga.update((t) => t + 1); }));
  }

  marcarLido(a: Comunicado): void {
    if (!a.lido) this.api.marcarComunicadoLido(this.contexto.id()!, a.id).subscribe(() => this.recarga.update((t) => t + 1));
  }

  arquivar(a: Comunicado, evento: Event): void {
    evento.stopPropagation();
    pedirConfirmacao(this.dialog, 'Arquivar comunicado', `Arquivar "${a.titulo}"? Ele deixa de ser exibido aos moradores.`, 'Arquivar')
      .pipe(switchMap(() => this.api.arquivarComunicado(this.contexto.id()!, a.id)))
      .subscribe({ next: () => this.recarga.update((t) => t + 1), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }
}

import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { pedirConfirmacao, pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EstruturaService } from '../estrutura/estrutura.service';
import { abrirFormAssembleia } from './assembleia-form';
import { Anexos } from './anexos';
import { ItemPauta, DetalheAssembleia, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-assembleia-detalhe',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio, RotuloPipe, DataIsoPipe, Anexos],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/assembleias" class="back"><mat-icon>arrow_back</mat-icon> Assembleias</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir" [descricao]="mensagem" /></section>
      } @else if (a(); as d) {
        <tc-cabecalho-pagina [sobretitulo]="'Assembleia ' + (d.tipo | rotulo: 'assemblyKind')" [titulo]="d.titulo"
          [subtitulo]="(d.primeiraConvocacaoEm | date: 'dd/MM/yyyy HH:mm') + ' · ' + d.local + ' · ' + (d.criterioVoto | rotulo: 'voteWeight')">
          <span class="tc-status big" [class.is-success]="d.situacao === 'CLOSED'" [class.is-pending]="d.situacao === 'CALLED'" [class.is-info]="d.situacao === 'IN_PROGRESS'"
            [class.is-inactive]="d.situacao === 'CANCELLED' || d.situacao === 'DRAFT'">{{ d.situacao | rotulo: 'assemblyStatus' }}</span>
        </tc-cabecalho-pagina>

        @if (podeEditar()) {
          <div class="tc-actions bar">
            @switch (d.situacao) {
              @case ('DRAFT') {
                <button mat-stroked-button (click)="editar(d)"><mat-icon>edit</mat-icon> Editar</button>
                <button mat-flat-button (click)="executarAcao('convocar', 'Convocar assembleia', 'Será publicado um comunicado de convocação com a pauta para todos os condôminos (notificação e e-mail).')"><mat-icon>campaign</mat-icon> Convocar</button>
              }
              @case ('CALLED') {
                <button mat-stroked-button (click)="editar(d)"><mat-icon>edit</mat-icon> Editar</button>
                <button mat-flat-button (click)="executarAcao('iniciar', 'Iniciar assembleia', 'Abre o registro de presença e votação.')"><mat-icon>play_arrow</mat-icon> Iniciar</button>
              }
              @case ('IN_PROGRESS') {
                <button mat-flat-button (click)="executarAcao('encerrar', 'Encerrar assembleia', 'Após encerrada, presença e votos não podem ser alterados.')"><mat-icon>stop</mat-icon> Encerrar</button>
              }
            }
            @if (d.situacao !== 'CLOSED' && d.situacao !== 'CANCELLED') { <button mat-button (click)="cancelar()">Cancelar assembleia</button> }
          </div>
        }

        <section class="tc-kpis" aria-label="Quórum">
          <div class="tc-kpi tc-card">
            <span class="icon"><mat-icon aria-hidden="true">door_front</mat-icon></span>
            <span class="label">Unidades presentes</span><span class="value">{{ d.quorum.unidadesPresentes }} / {{ d.quorum.totalUnidades }}</span>
          </div>
          <div class="tc-kpi tc-card tone-info">
            <span class="icon"><mat-icon aria-hidden="true">pie_chart</mat-icon></span>
            <span class="label">Frações presentes</span><span class="value">{{ d.quorum.fracaoPresente | number: '1.2-4' }}%</span>
          </div>
          @if (d.motivoCancelamento) {
            <div class="tc-kpi tc-card tone-danger reason">
              <span class="icon"><mat-icon aria-hidden="true">event_busy</mat-icon></span>
              <span class="label">Cancelamento</span><span class="value">{{ d.motivoCancelamento }}</span>
            </div>
          }
        </section>

        <div class="layout">
          <section class="tc-card">
            <h3 class="tc-section-title first">Pauta e votação</h3>
            @for (i of d.pauta; track i.id) {
              <div class="item">
                <strong class="item-title">{{ i.posicao }}. {{ i.titulo }}</strong>
                @if (i.deliberativo) {
                  <div class="tally">
                    <span class="vote yes">Sim <b>{{ i.apuracao.sim | number: '1.0-4' }}</b></span><span class="vote no">Não <b>{{ i.apuracao.nao | number: '1.0-4' }}</b></span>
                    <span class="vote">Abst. <b>{{ i.apuracao.abstencao | number: '1.0-4' }}</b></span><span class="tc-muted">{{ i.apuracao.votos }} voto(s)</span>
                    @if (podeEditar() && d.situacao === 'IN_PROGRESS') { <button mat-button (click)="votar(d, i)"><mat-icon>how_to_vote</mat-icon> Registrar voto</button> }
                  </div>
                } @else { <small class="tc-chip-soft info"> informativo</small> }
                @if (i.deliberacao) { <p class="delib">{{ i.deliberacao }}{{ i.responsavel ? ' — resp.: ' + i.responsavel : '' }}{{ i.prazo ? ' (prazo ' + (i.prazo | dataIso) + ')' : '' }}</p> }
                @if (podeEditar() && (d.situacao === 'IN_PROGRESS' || d.situacao === 'CLOSED')) { <button mat-button (click)="deliberar(i)">Deliberação</button> }
              </div>
            }
          </section>
          <section class="tc-card">
            <div class="head"><h3 class="tc-section-title">Lista de presença</h3>
              @if (podeEditar() && d.situacao === 'IN_PROGRESS') { <button mat-button (click)="registrarPresenca()"><mat-icon>person_add</mat-icon> Registrar</button> }</div>
            @for (p of d.presencas; track p.id) {
              <div class="att"><span><strong>{{ p.rotuloUnidade }}</strong> · {{ p.nomePresente }}{{ p.porProcuracao ? ' (procurador: ' + p.nomeProcurador + ')' : '' }}</span>
                @if (podeEditar() && d.situacao === 'IN_PROGRESS') { <button mat-icon-button (click)="removerPresenca(p.id)" aria-label="Remover presença"><mat-icon>close</mat-icon></button> }</div>
            } @empty { <p class="tc-muted">Nenhuma presença registrada.</p> }
          </section>
        </div>

        @if (d.situacao === 'IN_PROGRESS' || d.situacao === 'CLOSED') {
          <section class="tc-card minutes">
            <div class="head"><h3 class="tc-section-title">Ata</h3>
              @if (podeEditar()) { <button mat-button (click)="rascunho()"><mat-icon>auto_awesome</mat-icon> Gerar rascunho a partir dos dados</button> }</div>
            <mat-form-field class="full"><mat-label>Texto da ata</mat-label><textarea matInput rows="14" [formControl]="ata" [readonly]="!podeEditar()"></textarea></mat-form-field>
            @if (podeEditar()) { <div class="tc-actions"><button mat-flat-button (click)="salvarAta()" [disabled]="ata.pristine">Salvar ata</button></div> }
          </section>
        }
        <tc-anexos [idCondominio]="contexto.id()!" tipoEntidade="Assembly" [idEntidade]="d.id" categoria="MINUTES" [podeEditar]="podeEditar()" />
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; } .back { margin: -8px 0 8px -12px; }
    .bar { justify-content: flex-start; margin-bottom: 16px; }
    .reason .value { font-size: 15px; font-weight: 600; letter-spacing: 0; overflow-wrap: anywhere; }
    .layout { display: grid; grid-template-columns: minmax(0, 3fr) minmax(260px, 2fr); gap: 16px; align-items: start; margin-bottom: 16px; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .tc-section-title.first { margin-top: 0; }
    .item { display: grid; gap: 4px; justify-items: start; padding: 12px 0; border-bottom: 1px solid var(--tc-border); }
    .item:last-child { border-bottom: 0; padding-bottom: 0; }
    .item-title { overflow-wrap: anywhere; }
    .info { margin-left: 4px; }
    .tally { display: flex; gap: 8px 14px; align-items: center; flex-wrap: wrap; font-variant-numeric: tabular-nums; }
    .vote b { font-weight: 650; }
    .vote.yes b { color: var(--tc-success); }
    .vote.no b { color: var(--tc-danger); }
    .delib { background: var(--tc-surface-muted); border: 1px solid var(--tc-border); padding: 8px 12px; border-radius: var(--tc-radius-sm); margin: 4px 0; justify-self: stretch; white-space: pre-wrap; }
    .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
    .head h3 { margin: 0; }
    .att { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--tc-border); }
    .att:last-child { border-bottom: 0; }
    .att span { min-width: 0; overflow-wrap: anywhere; }
    .minutes { margin-bottom: 16px; } .full { width: 100%; margin-top: 8px; }
  `,
})
export class AssembleiaDetalhePage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  readonly id = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly erro = signal<string | null>(null);
  protected readonly ata = new FormControl('', { nonNullable: true });
  protected readonly podeEditar = computed(() => this.contexto.pode('ASSEMBLY_EDIT'));
  protected readonly a = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, id]) => this.api.assembleia(c!, id).pipe(
      map((d) => { if (this.ata.pristine) this.ata.reset(d.ata ?? ''); return d; }),
      catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); })))));

  private c(): string {
    return this.contexto.id()!;
  }

  executarAcao(acao: 'convocar' | 'iniciar' | 'encerrar', titulo: string, mensagem: string): void {
    pedirConfirmacao(this.dialog, titulo, mensagem, titulo.split(' ')[0]).pipe(switchMap(() => this.api.acaoAssembleia(this.c(), this.id(), acao)))
      .subscribe({ next: () => this.concluir('Assembleia atualizada.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  cancelar(): void {
    pedirMotivo(this.dialog, 'Cancelar assembleia', 'Informe o motivo do cancelamento.', 'Cancelar assembleia')
      .pipe(switchMap((r) => this.api.cancelarAssembleia(this.c(), this.id(), r)))
      .subscribe({ next: () => this.concluir('Assembleia cancelada.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  editar(d: DetalheAssembleia): void {
    abrirFormAssembleia(this.dialog, this.api, this.c(), d).pipe(filter((r) => !!r)).subscribe(() => this.concluir('Assembleia atualizada.'));
  }

  registrarPresenca(): void {
    this.unidades().subscribe((unidades) => abrirFormulario(this.dialog, {
      titulo: 'Registrar presença',
      campos: [
        { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', obrigatorio: true, opcoes: unidades },
        { chave: 'nomePresente', rotulo: 'Nome de quem está presente', obrigatorio: true, tamanhoMaximo: 150 },
        { chave: 'porProcuracao', rotulo: 'Representado por procuração', tipo: 'checkbox' },
        { chave: 'nomeProcurador', rotulo: 'Nome do procurador', tamanhoMaximo: 150 },
      ],
      enviar: (v) => this.api.registrarPresenca(this.c(), this.id(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.concluir('Presença registrada.')));
  }

  removerPresenca(idPresenca: string): void {
    this.api.removerPresenca(this.c(), this.id(), idPresenca)
      .subscribe({ next: () => this.concluir('Presença removida.'), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  votar(d: DetalheAssembleia, item: ItemPauta): void {
    abrirFormulario(this.dialog, {
      titulo: `Voto — ${item.posicao}. ${item.titulo}`,
      introducao: 'Somente unidades presentes votam; cada unidade vota uma vez por item.',
      campos: [
        { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', obrigatorio: true, opcoes: d.presencas.map((p) => ({ valor: p.idUnidade, rotulo: p.rotuloUnidade + ' · ' + p.nomePresente })) },
        { chave: 'escolha', rotulo: 'Voto', tipo: 'select', obrigatorio: true, valor: 'YES', opcoes: [{ valor: 'YES', rotulo: 'Sim' }, { valor: 'NO', rotulo: 'Não' }, { valor: 'ABSTAIN', rotulo: 'Abstenção' }] },
      ],
      rotuloEnviar: 'Registrar voto',
      enviar: (v) => this.api.votar(this.c(), this.id(), { ...v, idItemPauta: item.id }),
    }, '460px').pipe(filter((r) => !!r)).subscribe(() => this.concluir('Voto registrado.'));
  }

  deliberar(item: ItemPauta): void {
    abrirFormulario(this.dialog, {
      titulo: `Deliberação — ${item.posicao}. ${item.titulo}`,
      campos: [
        { chave: 'deliberacao', rotulo: 'Deliberação', tipo: 'textarea', valor: item.deliberacao, tamanhoMaximo: 2000 },
        { chave: 'responsavel', rotulo: 'Responsável pela execução', valor: item.responsavel, colunas: 6, tamanhoMaximo: 150 },
        { chave: 'prazo', rotulo: 'Prazo', tipo: 'date', valor: item.prazo, colunas: 6 },
      ],
      enviar: (v) => this.api.deliberar(this.c(), this.id(), item.id, v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.concluir('Deliberação registrada.'));
  }

  rascunho(): void {
    this.api.rascunhoAta(this.c(), this.id()).subscribe((r) => {
      this.ata.setValue(r.ata);
      this.ata.markAsDirty();
    });
  }

  salvarAta(): void {
    this.api.salvarAta(this.c(), this.id(), this.ata.value).subscribe({
      next: () => { this.ata.markAsPristine(); this.concluir('Ata salva.'); },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  private unidades(): Observable<{ valor: string; rotulo: string }[]> {
    return this.estrutura.unidades(this.c(), { pagina: 0, tamanho: 100, ativo: 'true' }).pipe(map((p) => p.conteudo.map((u) => ({ valor: u.id, rotulo: u.rotulo }))));
  }

  private concluir(m: string): void {
    this.notificacao.sucesso(m);
    this.recarga.update((t) => t + 1);
  }
}

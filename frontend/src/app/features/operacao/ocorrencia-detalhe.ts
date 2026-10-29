import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Anexos } from './anexos';
import { DetalheOcorrencia, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-ocorrencia-detalhe',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatCheckboxModule,
    MatProgressBarModule, CabecalhoPagina, EstadoVazio, RotuloPipe, DataIsoPipe, Anexos],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/ocorrencias" class="back"><mat-icon>arrow_back</mat-icon> Ocorrências</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir" [descricao]="mensagem" /></section>
      } @else if (o(); as d) {
        <tc-cabecalho-pagina [sobretitulo]="'Ocorrência nº ' + d.resumo.numero" [titulo]="d.resumo.titulo"
          [subtitulo]="(d.resumo.categoria | rotulo: 'occurrenceCategory') + ' · prioridade ' + (d.resumo.prioridade | rotulo: 'priority') + (d.resumo.rotuloUnidade ? ' · Unid. ' + d.resumo.rotuloUnidade : '')">
          <span class="tc-status big" [class.is-success]="d.resumo.situacao === 'RESOLVED'"
            [class.is-pending]="d.resumo.situacao === 'OPEN' || d.resumo.situacao === 'WAITING'" [class.is-info]="d.resumo.situacao === 'IN_PROGRESS'"
            [class.is-inactive]="d.resumo.situacao === 'CANCELLED'">{{ d.resumo.situacao | rotulo: 'occurrenceStatus' }}</span>
        </tc-cabecalho-pagina>
        <div class="layout">
          <div class="main">
            <section class="tc-card">
              <p class="desc">{{ d.descricao }}</p>
              <p class="tc-muted small">Aberta por {{ d.resumo.nomeSolicitante }} em {{ d.resumo.criadoEm | date: 'dd/MM/yyyy HH:mm' }}
                · responsável: {{ d.resumo.nomeResponsavel ?? 'não definido' }} · prazo: {{ d.resumo.prazo ? (d.resumo.prazo | dataIso) : '—' }}</p>
              @if (d.solucao) { <p class="resolution"><strong>Solução:</strong> {{ d.solucao }}</p> }
              @if (podeEditar()) {
                <div class="tc-actions status-acts">
                  <button mat-button (click)="triar(d)">Triagem</button>
                  @for (s of proximasSituacoes(d); track s.valor) {
                    <button mat-stroked-button (click)="alterarSituacao(s.valor)">{{ s.rotulo }}</button>
                  }
                </div>
              }
            </section>
            <section class="tc-card">
              <h3 class="tc-section-title first">Andamento</h3>
              @for (i of d.interacoes; track i.id) {
                <div class="msg" [class.internal]="i.interna">
                  <strong>{{ i.nomeAutor }}</strong> <small class="tc-muted">{{ i.criadoEm | date: 'dd/MM/yy HH:mm' }}
                    {{ i.situacaoAlterada ? ' · ' + (i.situacaoAlterada | rotulo: 'occurrenceStatus') : '' }}{{ i.interna ? ' · interna' : '' }}</small>
                  <p>{{ i.mensagem }}</p>
                </div>
              } @empty { <p class="tc-muted">Sem interações.</p> }
              @if (podeEditar()) {
                <mat-form-field class="full"><mat-label>Nova mensagem</mat-label><textarea matInput rows="3" [formControl]="mensagem" maxlength="2000"></textarea></mat-form-field>
                <div class="tc-actions send">
                  <mat-checkbox [formControl]="interna">Interna (não aparece para o morador)</mat-checkbox>
                  <button mat-flat-button (click)="enviar()" [disabled]="mensagem.invalid">Enviar</button>
                </div>
              }
            </section>
          </div>
          <tc-anexos [idCondominio]="contexto.id()!" tipoEntidade="Occurrence" [idEntidade]="d.resumo.id" categoria="OCCURRENCE" [podeEditar]="podeEditar()" />
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; } .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .main { display: grid; gap: 16px; min-width: 0; }
    .desc { white-space: pre-wrap; margin-top: 0; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); }
    .resolution { background: var(--tc-success-container); border-left: 3px solid var(--tc-success); padding: 10px 12px; border-radius: var(--tc-radius-sm); white-space: pre-wrap; }
    .status-acts { justify-content: flex-start; gap: 8px; padding-top: 12px; border-top: 1px solid var(--tc-border); }
    .tc-section-title.first { margin-top: 0; }
    .msg { border-left: 3px solid var(--mat-sys-primary); padding: 6px 12px; margin-bottom: 12px; border-radius: 0 var(--tc-radius-sm) var(--tc-radius-sm) 0; }
    .msg.internal { border-color: var(--tc-accent); background: var(--tc-accent-soft); }
    .msg p { margin: 4px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .full { width: 100%; margin-top: 4px; }
    .send { justify-content: space-between; align-items: center; }
    @media (max-width: 600px) {
      .status-acts > button { flex: 1 1 auto; }
      .send > button { flex: 1 1 100%; }
    }
  `,
})
export class OcorrenciaDetalhePage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  readonly id = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly erro = signal<string | null>(null);
  protected readonly mensagem = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly interna = new FormControl(false, { nonNullable: true });
  protected readonly podeEditar = computed(() => this.contexto.pode('OCCURRENCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly o = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, id]) => this.api.ocorrencia(c!, id).pipe(catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); })))));

  proximasSituacoes(d: DetalheOcorrencia): { valor: string; rotulo: string }[] {
    const s = d.resumo.situacao;
    if (s === 'RESOLVED' || s === 'CANCELLED') return [{ valor: 'OPEN', rotulo: 'Reabrir' }];
    return [
      ...(s !== 'IN_PROGRESS' ? [{ valor: 'IN_PROGRESS', rotulo: 'Em atendimento' }] : []),
      ...(s !== 'WAITING' ? [{ valor: 'WAITING', rotulo: 'Aguardando' }] : []),
      { valor: 'RESOLVED', rotulo: 'Resolver' },
      { valor: 'CANCELLED', rotulo: 'Cancelar' },
    ];
  }

  alterarSituacao(situacao: string): void {
    const exigeTexto = situacao === 'RESOLVED' || situacao === 'CANCELLED';
    abrirFormulario(this.dialog, {
      titulo: situacao === 'RESOLVED' ? 'Resolver ocorrência' : 'Alterar situação',
      campos: [{ chave: 'mensagem', rotulo: situacao === 'RESOLVED' ? 'Solução aplicada' : 'Mensagem', tipo: 'textarea', obrigatorio: exigeTexto, tamanhoMaximo: 2000 }],
      enviar: (v) => this.api.situacaoOcorrencia(this.contexto.id()!, this.id(), { situacao, mensagem: v['mensagem'] }),
    }, '480px').pipe(filter((r) => !!r)).subscribe(() => this.concluir('Situação atualizada.'));
  }

  triar(d: DetalheOcorrencia): void {
    abrirFormulario(this.dialog, {
      titulo: 'Triagem',
      campos: [
        { chave: 'categoria', rotulo: 'Categoria', tipo: 'select', obrigatorio: true, valor: d.resumo.categoria, colunas: 6, opcoes: opcoes('occurrenceCategory') },
        { chave: 'prioridade', rotulo: 'Prioridade', tipo: 'select', obrigatorio: true, valor: d.resumo.prioridade, colunas: 6, opcoes: opcoes('priority') },
        { chave: 'nomeResponsavel', rotulo: 'Responsável', valor: d.resumo.nomeResponsavel, colunas: 6, tamanhoMaximo: 150 },
        { chave: 'prazo', rotulo: 'Prazo', tipo: 'date', valor: d.resumo.prazo, colunas: 6 },
      ],
      enviar: (v) => this.api.atualizarOcorrencia(this.contexto.id()!, this.id(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => this.concluir('Triagem salva.'));
  }

  enviar(): void {
    this.api.comentar(this.contexto.id()!, this.id(), this.mensagem.value.trim(), this.interna.value).subscribe({
      next: () => { this.mensagem.reset(); this.concluir('Mensagem registrada.'); },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  private concluir(m: string): void {
    this.notificacao.sucesso(m);
    this.recarga.update((t) => t + 1);
  }
}

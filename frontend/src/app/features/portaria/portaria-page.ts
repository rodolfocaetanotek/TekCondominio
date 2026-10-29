import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EstruturaService } from '../estrutura/estrutura.service';
import { AutorizacaoVisita, PortariaApi } from './portaria-api';

/** Painel da portaria: entrada por código, chegadas sem aviso, quem está no condomínio e saídas. */
@Component({
  selector: 'tc-portaria-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, CabecalhoPagina,
    CondominioObrigatorio, EstadoVazio, DataIsoPipe, RotuloPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Administração" titulo="Portaria" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) { <button mat-flat-button (click)="chegada()"><mat-icon>person_add</mat-icon> Registrar chegada</button> }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        @if (podeEditar()) {
          <section class="tc-card code">
            <span class="code-icon" aria-hidden="true"><mat-icon>pin</mat-icon></span>
            <mat-form-field class="code-field" subscriptSizing="dynamic"><mat-label>Código de acesso (6 dígitos)</mat-label>
              <input matInput [formControl]="codigo" inputmode="numeric" maxlength="6" autocomplete="off" (keydown.enter)="registrarEntradaPorCodigo()" /></mat-form-field>
            <button mat-flat-button class="code-btn" (click)="registrarEntradaPorCodigo()" [disabled]="codigo.invalid"><mat-icon>login</mat-icon> Liberar entrada</button>
          </section>
        }
        @for (grupo of grupos(); track grupo.titulo; let gi = $index) {
          <section class="tc-card group" [class.g-inside]="gi === 0" [class.g-expected]="gi === 1" [class.g-left]="gi === 2">
            <h3 class="tc-section-title">{{ grupo.titulo }} ({{ grupo.itens.length }})</h3>
            @for (v of grupo.itens; track v.id) {
              <div class="row">
                <div class="who"><strong>{{ v.nome }}</strong> · {{ v.tipo | rotulo: 'visitorKind' }} · unid. {{ v.rotuloUnidade }}<br />
                  <span class="tc-muted small">{{ v.empresa ?? '' }}{{ v.documento ? ' · doc. ' + v.documento : '' }}{{ v.placaVeiculo ? ' · placa ' + v.placaVeiculo : '' }}
                    · autorizado por {{ v.nomeAutorizadoPor }}{{ v.validaDe !== v.validaAte ? ' · válido até ' + (v.validaAte | dataIso) : '' }}
                    {{ v.entradaEm ? ' · entrada ' + (v.entradaEm | date: 'HH:mm') : '' }}{{ v.saidaEm ? ' · saída ' + (v.saidaEm | date: 'HH:mm') : '' }}</span></div>
                @if (podeEditar()) {
                  @if (v.situacao === 'EXPECTED') { <button mat-flat-button class="act" (click)="executarAcao(v, 'in')"><mat-icon>login</mat-icon> Entrada</button> }
                  @if (v.situacao === 'INSIDE') { <button mat-stroked-button class="act" (click)="executarAcao(v, 'out')"><mat-icon>logout</mat-icon> Saída</button> }
                }
              </div>
            } @empty { <p class="tc-muted none">Nenhum registro.</p> }
          </section>
        }
        @if ((lista() ?? []).length === 0) { <section class="tc-card"><tc-estado-vazio icone="badge" titulo="Sem movimentação hoje" /></section> }
      }
    </div>
  `,
  styles: `
    :host .tc-page > section { margin-bottom: 16px; }
    .code { display: flex; gap: 12px 16px; align-items: center; flex-wrap: wrap; padding: 20px 24px;
      border-color: color-mix(in srgb, var(--mat-sys-primary) 35%, var(--tc-border));
      background: color-mix(in srgb, var(--mat-sys-primary) 4%, var(--tc-surface)); }
    .code-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; flex: 0 0 auto;
      background: color-mix(in srgb, var(--mat-sys-primary) 14%, transparent); color: var(--mat-sys-primary); }
    .code-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .code-field { flex: 1 1 260px; max-width: 420px; min-width: 0; }
    .code-field input { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 26px; font-weight: 650; letter-spacing: .3em; }
    .code-btn { min-height: 52px; padding-inline: 24px; font-size: 15px; }
    .group .tc-section-title { margin-top: 0; padding-left: 10px; border-left: 3px solid var(--tc-border-strong); line-height: 1.2; }
    .g-inside .tc-section-title { border-left-color: var(--tc-success); }
    .g-expected .tc-section-title { border-left-color: var(--tc-warning); }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; padding-bottom: 0; }
    .who { flex: 1 1 260px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .who strong { font-size: 15px; }
    .act { min-width: 120px; }
    .none { margin: 0; }
    .small { font: var(--mat-sys-body-small); }
    @media (max-width: 600px) {
      .code { padding: 16px; }
      .code-icon { display: none; }
      .code-field { flex: 1 1 100%; max-width: none; }
      .code-btn, .act { flex: 1 1 100%; }
    }
  `,
})
export class PortariaPage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(PortariaApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly codigo = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] });

  protected readonly podeEditar = computed(() => this.contexto.pode('ACCESS_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly lista = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.hoje(c!).pipe(catchError(() => of([] as AutorizacaoVisita[]))))));
  protected readonly grupos = computed(() => {
    const todos = this.lista() ?? [];
    return [
      { titulo: 'No condomínio agora', itens: todos.filter((v) => v.situacao === 'INSIDE') },
      { titulo: 'Esperados hoje', itens: todos.filter((v) => v.situacao === 'EXPECTED') },
      { titulo: 'Saíram hoje', itens: todos.filter((v) => v.situacao === 'LEFT') },
    ];
  });

  registrarEntradaPorCodigo(): void {
    if (this.codigo.invalid) return;
    this.api.registrarEntradaPorCodigo(this.contexto.id()!, this.codigo.value).subscribe({
      next: (v) => {
        this.notificacao.sucesso(`Entrada liberada: ${v.nome} (unid. ${v.rotuloUnidade}).`);
        this.codigo.reset();
        this.recarga.update((t) => t + 1);
      },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  executarAcao(v: AutorizacaoVisita, acao: 'in' | 'out'): void {
    const c = this.contexto.id()!;
    (acao === 'in' ? this.api.registrarEntrada(c, v.id) : this.api.registrarSaida(c, v.id)).subscribe({
      next: () => this.recarga.update((t) => t + 1),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  chegada(): void {
    const c = this.contexto.id()!;
    this.estrutura.unidades(c, { pagina: 0, tamanho: 500, ativo: 'true' }).pipe(map((p) => p.conteudo)).subscribe((unidades) =>
      abrirFormulario(this.dialog, {
        titulo: 'Registrar chegada',
        introducao: 'Colete apenas os dados necessários. O documento é exibido mascarado e os registros são anonimizados após o prazo de retenção.',
        campos: [
          { chave: 'idUnidade', rotulo: 'Unidade de destino', tipo: 'select', obrigatorio: true, colunas: 6, opcoes: unidades.map((u) => ({ valor: u.id, rotulo: u.rotulo })) },
          { chave: 'tipo', rotulo: 'Tipo', tipo: 'select', obrigatorio: true, valor: 'VISITOR', colunas: 6, opcoes: opcoes('visitorKind') },
          { chave: 'nome', rotulo: 'Nome', obrigatorio: true, tamanhoMaximo: 150 },
          { chave: 'documento', rotulo: 'Documento (opcional)', tamanhoMaximo: 20, colunas: 6 },
          { chave: 'placaVeiculo', rotulo: 'Placa (opcional)', tamanhoMaximo: 8, colunas: 6 },
          { chave: 'empresa', rotulo: 'Empresa (prestador/entrega)', tamanhoMaximo: 120 },
        ],
        rotuloEnviar: 'Registrar entrada',
        enviar: (v) => this.api.chegada(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => this.recarga.update((t) => t + 1)));
  }
}

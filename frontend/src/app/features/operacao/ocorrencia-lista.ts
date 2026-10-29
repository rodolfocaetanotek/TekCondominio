import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, filter, map, of, switchMap } from 'rxjs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EstruturaService } from '../estrutura/estrutura.service';
import { DetalheOcorrencia, OperacaoApi, ResumoOcorrencia } from './operacao-api';

@Component({
  selector: 'tc-ocorrencia-lista',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio, CondominioObrigatorio, RotuloPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Manutenção" titulo="Ocorrências" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) { <button mat-flat-button (click)="cadastrar()"><mat-icon>add_alert</mat-icon> Registrar ocorrência</button> }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        @if (estatisticas(); as s) {
          <section class="tc-kpis" aria-label="Indicadores de ocorrências">
            <div class="tc-kpi tc-card tone-warning">
              <span class="icon"><mat-icon aria-hidden="true">pending_actions</mat-icon></span>
              <span class="label">Pendentes</span><span class="value">{{ s.abertas }}</span>
            </div>
            <div class="tc-kpi tc-card" [class.tone-danger]="s.atrasadas > 0" [class.tone-success]="s.atrasadas === 0">
              <span class="icon"><mat-icon aria-hidden="true">{{ s.atrasadas > 0 ? 'alarm' : 'verified' }}</mat-icon></span>
              <span class="label">Fora do prazo</span><span class="value">{{ s.atrasadas }}</span>
            </div>
            <div class="tc-kpi tc-card tone-success">
              <span class="icon"><mat-icon aria-hidden="true">task_alt</mat-icon></span>
              <span class="label">Resolvidas</span><span class="value">{{ s.porSituacao['RESOLVED'] ?? 0 }}</span>
            </div>
            <div class="tc-kpi tc-card tone-info">
              <span class="icon"><mat-icon aria-hidden="true">timer</mat-icon></span>
              <span class="label">Tempo médio de solução</span>
              <span class="value">{{ s.mediaHorasSolucao !== null && s.mediaHorasSolucao !== undefined ? (s.mediaHorasSolucao | number: '1.0-1') + ' h' : '—' }}</span>
            </div>
          </section>
        }
        <div class="filters">
          <mat-form-field class="search"><mat-label>Buscar</mat-label><mat-icon matPrefix>search</mat-icon><input matInput [formControl]="busca" /></mat-form-field>
          <mat-form-field><mat-label>Situação</mat-label>
            <mat-select [formControl]="situacao">
              <mat-option value="pending">Pendentes</mat-option>
              <mat-option value="RESOLVED">Resolvidas</mat-option>
              <mat-option value="CANCELLED">Canceladas</mat-option>
              <mat-option value="">Todas</mat-option>
            </mat-select></mat-form-field>
        </div>
        <section class="tc-table-card" aria-live="polite" [attr.aria-busy]="pagina() === undefined">
          <mat-progress-bar [mode]="pagina() === undefined ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="pagina() !== undefined" />
          @if (pagina() === undefined) {
            <span class="sr-only">Carregando ocorrências…</span>
          } @else if ((pagina()?.conteudo ?? []).length === 0) {
            <tc-estado-vazio icone="build" titulo="Nenhuma ocorrência" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="pagina()?.conteudo ?? []">
                <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Nº</th><td mat-cell *matCellDef="let o" class="mono">{{ o.numero }}</td></ng-container>
                <ng-container matColumnDef="title"><th mat-header-cell *matHeaderCellDef>Ocorrência</th>
                  <td mat-cell *matCellDef="let o"><span class="title-cell"><strong>{{ o.titulo }}</strong>
                    <span class="tc-muted sub">{{ o.categoria | rotulo: 'occurrenceCategory' }}{{ o.rotuloUnidade ? ' · Unid. ' + o.rotuloUnidade : '' }} · {{ o.nomeSolicitante }} · {{ o.criadoEm | date: 'dd/MM/yy HH:mm' }}</span></span></td></ng-container>
                <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Prioridade</th>
                  <td mat-cell *matCellDef="let o"><span [class.urgent]="o.prioridade === 'URGENT' || o.prioridade === 'HIGH'">{{ o.prioridade | rotulo: 'priority' }}</span></td></ng-container>
                <ng-container matColumnDef="assignee"><th mat-header-cell *matHeaderCellDef>Responsável / prazo</th>
                  <td mat-cell *matCellDef="let o"><span class="two-line">{{ o.nomeResponsavel ?? '—' }}<span class="sub" [class.negative]="o.atrasada">{{ o.prazo ? 'até ' + o.prazo.slice(8, 10) + '/' + o.prazo.slice(5, 7) : '' }}</span></span></td></ng-container>
                <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let o"><span class="tc-status" [class.is-success]="o.situacao === 'RESOLVED'"
                    [class.is-danger]="o.atrasada && o.situacao !== 'RESOLVED' && o.situacao !== 'CANCELLED'"
                    [class.is-pending]="!o.atrasada && (o.situacao === 'OPEN' || o.situacao === 'WAITING')" [class.is-info]="!o.atrasada && o.situacao === 'IN_PROGRESS'"
                    [class.is-inactive]="o.situacao === 'CANCELLED'">{{ o.situacao | rotulo: 'occurrenceStatus' }}</span></td></ng-container>
                <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                <tr mat-row *matRowDef="let linha; colunas: colunas" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ocorrência nº ' + linha.numero" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .title-cell, .two-line { display: grid; gap: 2px; padding-block: 6px; }
    .sub { font: var(--mat-sys-body-small); }
    .negative { color: var(--tc-danger); font-weight: 600; }
    .urgent { color: var(--tc-danger); font-weight: 600; }
    @media (max-width: 720px) {
      .title-cell, .two-line { justify-items: end; padding-block: 0; }
    }
  `,
})
export class OcorrenciaLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly colunas = ['number', 'title', 'priority', 'assignee', 'status'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly situacao = new FormControl('pending', { nonNullable: true });
  private readonly parametros = signal({ busca: '', situacao: 'pending', recarga: 0 });
  protected readonly podeEditar = computed(() => this.contexto.pode('OCCURRENCE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly origem = combineLatest([toObservable(this.contexto.id), toObservable(this.parametros)]).pipe(filter(([c]) => !!c));

  protected readonly pagina = toSignal(this.origem.pipe(switchMap(([c, p]) => this.api.ocorrencias(c!, {
    busca: p.busca, tamanho: '100', ...(p.situacao === 'pending' ? { situacao: ['OPEN', 'IN_PROGRESS', 'WAITING'] } : p.situacao ? { situacao: p.situacao } : {}),
  }).pipe(catchError(() => of(null))))));
  protected readonly estatisticas = toSignal(this.origem.pipe(switchMap(([c]) => this.api.estatisticasOcorrencias(c!).pipe(catchError(() => of(null))))));

  constructor() {
    this.busca.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((busca) => this.parametros.update((p) => ({ ...p, busca })));
    this.situacao.valueChanges.pipe(takeUntilDestroyed()).subscribe((situacao) => this.parametros.update((p) => ({ ...p, situacao })));
  }

  abrir(o: ResumoOcorrencia): void {
    void this.router.navigate(['/ocorrencias', o.id]);
  }

  cadastrar(): void {
    const c = this.contexto.id()!;
    this.estrutura.unidades(c, { pagina: 0, tamanho: 100, ativo: 'true' }).pipe(map((p) => p.conteudo)).subscribe((unidades) =>
      abrirFormulario<DetalheOcorrencia>(this.dialog, {
        titulo: 'Registrar ocorrência',
        campos: [
          { chave: 'titulo', rotulo: 'Título', obrigatorio: true, tamanhoMaximo: 150 },
          { chave: 'descricao', rotulo: 'Descrição', tipo: 'textarea', obrigatorio: true, tamanhoMaximo: 4000 },
          { chave: 'categoria', rotulo: 'Categoria', tipo: 'select', obrigatorio: true, valor: 'MAINTENANCE', colunas: 6, opcoes: opcoes('occurrenceCategory') },
          { chave: 'prioridade', rotulo: 'Prioridade', tipo: 'select', valor: 'MEDIUM', colunas: 6, opcoes: opcoes('priority') },
          { chave: 'idUnidade', rotulo: 'Unidade relacionada', tipo: 'select', valor: null,
            opcoes: [{ valor: null, rotulo: '— (área comum)' }, ...unidades.map((u) => ({ valor: u.id, rotulo: u.rotulo }))] },
        ],
        enviar: (v) => this.api.cadastrarOcorrencia(c, v),
      }).pipe(filter((r): r is DetalheOcorrencia => !!r)).subscribe((r) => {
        this.notificacao.sucesso(`Ocorrência nº ${r.resumo.numero} registrada.`);
        void this.router.navigate(['/ocorrencias', r.resumo.id]);
      }));
  }
}

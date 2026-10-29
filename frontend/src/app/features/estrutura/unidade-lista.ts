import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { BlocoDialog } from './bloco-dialog';
import { ImportacaoDialog } from './importacao-dialog';
import { Bloco, Unidade, ROTULOS_TIPO_UNIDADE } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { UnidadeDialog, DadosUnidadeDialog } from './unidade-dialog';

@Component({
  selector: 'tc-unidade-lista',
  imports: [DecimalPipe, ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule, MatTooltipModule,
    CabecalhoPagina, EstadoVazio, CondominioObrigatorio],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Estrutura" titulo="Unidades" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) {
          <button mat-stroked-button (click)="abrirImportacao()"><mat-icon>upload_file</mat-icon> Importar planilha</button>
          <button mat-flat-button (click)="abrirUnidade()"><mat-icon>add</mat-icon> Nova unidade</button>
        }
      </tc-cabecalho-pagina>

      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        @if (data(); as d) {
          <section class="summary">
            <div class="tc-kpi tc-card fraction" [class.tone-success]="d.resumo.fechada" [class.tone-warning]="!d.resumo.fechada" role="status">
              <span class="icon"><mat-icon aria-hidden="true">{{ d.resumo.fechada ? 'check_circle' : 'warning' }}</mat-icon></span>
              <span class="label">Frações ideais</span>
              <span class="value">{{ d.resumo.total | number: '1.2-6' }}%</span>
              <span class="sub">{{ d.resumo.unidadesAtivas }} unidade(s) ativa(s) ·
                {{ d.resumo.fechada ? 'soma correta, rateio por fração liberado' : 'faltam ' + (d.resumo.diferenca | number: '1.2-6') + ' p.p. para 100%' }}</span>
            </div>
            <div class="tc-card buildings">
              <p class="tc-section-label">Blocos</p>
              <div class="chips">
              @for (b of d.blocos; track b.id) {
                <button mat-stroked-button class="chip" [class.inactive]="!b.ativo" [matMenuTriggerFor]="menuBloco" [disabled]="!podeEditar()"
                  [matTooltip]="b.unidadesAtivas + ' unidade(s) ativa(s)'">{{ b.nome }} ({{ b.unidadesAtivas }})</button>
                <mat-menu #menuBloco="matMenu">
                  <button mat-menu-item (click)="abrirBloco(b)"><mat-icon>edit</mat-icon>Editar</button>
                  <button mat-menu-item (click)="alternarBloco(b)"><mat-icon>{{ b.ativo ? 'block' : 'check_circle' }}</mat-icon>{{ b.ativo ? 'Inativar' : 'Reativar' }}</button>
                </mat-menu>
              } @empty {
                <span class="tc-muted">nenhum</span>
              }
              @if (podeEditar()) {
                <button mat-button (click)="abrirBloco()"><mat-icon>add</mat-icon> Bloco</button>
              }
              </div>
            </div>
          </section>
        }

        <div class="filters">
          <mat-form-field class="search">
            <mat-label>Buscar unidade</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput [formControl]="busca" autocomplete="off" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Bloco</mat-label>
            <mat-select [formControl]="bloco">
              <mat-option value="">Todos</mat-option>
              @for (b of data()?.blocos ?? []; track b.id) {
                <mat-option [value]="b.id">{{ b.nome }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Situação</mat-label>
            <mat-select [formControl]="ativo">
              <mat-option value="">Todas</mat-option>
              <mat-option value="true">Ativas</mat-option>
              <mat-option value="false">Inativas</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <section class="tc-table-card" [attr.aria-busy]="carregando()">
          <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
          @if (erro(); as mensagem) {
            <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar" [descricao]="mensagem" />
          } @else if (data(); as d) {
            @if (d.pagina.totalElementos === 0) {
              <tc-estado-vazio icone="door_front" titulo="Nenhuma unidade"
                descricao="Cadastre as unidades uma a uma ou importe uma planilha com unidades e proprietários." />
            } @else {
              <div class="table-scroll">
                <table mat-table [dataSource]="d.pagina.conteudo">
                  <caption class="sr-only">Unidades</caption>
                  <ng-container matColumnDef="label">
                    <th mat-header-cell *matHeaderCellDef>Unidade</th>
                    <td mat-cell *matCellDef="let u"><strong>{{ u.rotulo }}</strong></td>
                  </ng-container>
                  <ng-container matColumnDef="type">
                    <th mat-header-cell *matHeaderCellDef>Tipo</th>
                    <td mat-cell *matCellDef="let u">{{ rotuloTipo(u) }}</td>
                  </ng-container>
                  <ng-container matColumnDef="fraction">
                    <th mat-header-cell *matHeaderCellDef class="num">Fração (%)</th>
                    <td mat-cell *matCellDef="let u" class="num mono">{{ u.fracaoIdeal | number: '1.2-6' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="owner">
                    <th mat-header-cell *matHeaderCellDef>Proprietário</th>
                    <td mat-cell *matCellDef="let u">{{ u.nomeProprietario ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="financial">
                    <th mat-header-cell *matHeaderCellDef>Resp. financeiro</th>
                    <td mat-cell *matCellDef="let u">{{ u.nomeResponsavelFinanceiro ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Situação</th>
                    <td mat-cell *matCellDef="let u"><span class="tc-status" [class.is-active]="u.ativo" [class.is-inactive]="!u.ativo">{{ u.ativo ? 'Ativa' : 'Inativa' }}</span></td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                  <tr mat-row *matRowDef="let linha; colunas: colunas" class="is-clickable" tabindex="0" role="link"
                    [attr.aria-label]="'Abrir unidade ' + linha.rotulo" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
                </table>
              </div>
              <mat-paginator [length]="d.pagina.totalElementos" [pageIndex]="d.pagina.pagina" [pageSize]="d.pagina.tamanho"
                [pageSizeOptions]="[25, 50, 100]" (page)="aoPaginar($event)" aria-label="Paginação de unidades" />
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    .summary { display: grid; grid-template-columns: minmax(0, 340px) minmax(0, 1fr); gap: 12px; margin-bottom: 16px; }
    .buildings { padding: 16px; }
    .chips { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .chip.inactive { opacity: .6; text-decoration: line-through; }
    .num { text-align: right; }
    @media (max-width: 720px) { .summary { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class UnidadeLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly service = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);

  protected readonly colunas = ['label', 'type', 'fraction', 'owner', 'financial', 'status'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly bloco = new FormControl('', { nonNullable: true });
  protected readonly ativo = new FormControl('true', { nonNullable: true });
  private readonly paginacao = signal({ pagina: 0, tamanho: 50, busca: '', idBloco: '', ativo: 'true', recarga: 0 });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditar = computed(() => this.contexto.pode('STRUCTURE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');

  private readonly consulta = computed(() => ({ condominio: this.contexto.id(), ...this.paginacao() }));

  protected readonly data = toSignal(
    toObservable(this.consulta).pipe(
      filter((q) => !!q.condominio),
      tap(() => {
        this.carregando.set(true);
        this.erro.set(null);
      }),
      switchMap((q) =>
        forkJoin({
          pagina: this.service.unidades(q.condominio!, q),
          blocos: this.service.blocos(q.condominio!),
          resumo: this.service.resumoFracoes(q.condominio!),
        }).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of(null);
          }),
          finalize(() => this.carregando.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  constructor() {
    this.busca.valueChanges
      .pipe(debounceTime(300), map((v) => v.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((busca) => this.paginacao.update((p) => ({ ...p, busca, pagina: 0 })));
    this.bloco.valueChanges.pipe(takeUntilDestroyed()).subscribe((idBloco) => this.paginacao.update((p) => ({ ...p, idBloco, pagina: 0 })));
    this.ativo.valueChanges.pipe(takeUntilDestroyed()).subscribe((ativo) => this.paginacao.update((p) => ({ ...p, ativo, pagina: 0 })));
  }

  rotuloTipo(u: Unidade): string {
    return ROTULOS_TIPO_UNIDADE[u.tipo];
  }

  aoPaginar(e: PageEvent): void {
    this.paginacao.update((p) => ({ ...p, pagina: e.pageIndex, tamanho: e.pageSize }));
  }

  abrir(u: Unidade): void {
    void this.router.navigate(['/unidades', u.id]);
  }

  abrirUnidade(): void {
    const idCondominio = this.contexto.id()!;
    this.dialog
      .open<UnidadeDialog, DadosUnidadeDialog, Unidade>(UnidadeDialog, { data: { idCondominio, blocos: this.data()?.blocos ?? [] }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((u): u is Unidade => !!u))
      .subscribe((u) => {
        this.notificacao.sucesso(`Unidade ${u.rotulo} cadastrada.`);
        this.renovar();
      });
  }

  abrirBloco(bloco?: Bloco): void {
    this.dialog
      .open(BlocoDialog, { data: { idCondominio: this.contexto.id()!, bloco }, width: '440px' })
      .afterClosed()
      .pipe(filter((b) => !!b))
      .subscribe(() => {
        this.notificacao.sucesso('Bloco salvo.');
        this.renovar();
      });
  }

  alternarBloco(b: Bloco): void {
    this.service.alterarBlocoAtivo(this.contexto.id()!, b.id, !b.ativo).subscribe({
      next: () => this.renovar(),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  abrirImportacao(): void {
    this.dialog
      .open(ImportacaoDialog, { data: { idCondominio: this.contexto.id()! }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe(() => this.renovar());
  }

  private renovar(): void {
    this.paginacao.update((p) => ({ ...p, recarga: p.recarga + 1 }));
  }
}

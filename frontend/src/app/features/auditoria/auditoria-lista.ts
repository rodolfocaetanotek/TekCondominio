import { DatePipe, KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { catchError, finalize, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, paraProblema } from '../../core/api/api.models';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { ROTULOS_ACAO_AUDITORIA, ROTULOS_ENTIDADE_AUDITORIA, RegistroAuditoria } from './auditoria.models';
import { ConsultaAuditoria, AuditoriaService } from './auditoria.service';

@Component({
  selector: 'tc-auditoria-lista',
  imports: [
    DatePipe,
    KeyValuePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    CabecalhoPagina,
    EstadoVazio,
  ],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Segurança" titulo="Trilha de auditoria"
        subtitulo="Registro das ações críticas realizadas na sua organização." />

      <div class="filters">
        <mat-form-field class="action">
          <mat-label>Ação</mat-label>
          <mat-select [formControl]="acao">
            <mat-option value="">Todas</mat-option>
            @for (a of acoes; track a[0]) {
              <mat-option [value]="a[0]">{{ a[1] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      <section class="tc-table-card" [attr.aria-busy]="carregando()">
        <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
        @if (erro(); as mensagem) {
          <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar a auditoria" [descricao]="mensagem" />
        } @else if (resultado(); as pagina) {
          @if (pagina.totalElementos === 0) {
            <tc-estado-vazio icone="policy" titulo="Nenhum registro encontrado" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="pagina.conteudo">
                <caption class="sr-only">Registros de auditoria</caption>
                <ng-container matColumnDef="when">
                  <th mat-header-cell *matHeaderCellDef>Data/hora</th>
                  <td mat-cell *matCellDef="let e" class="nowrap">{{ e.occurredAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                </ng-container>
                <ng-container matColumnDef="user">
                  <th mat-header-cell *matHeaderCellDef>Usuário</th>
                  <td mat-cell *matCellDef="let e">{{ e.userEmail ?? '—' }}</td>
                </ng-container>
                <ng-container matColumnDef="action">
                  <th mat-header-cell *matHeaderCellDef>Ação</th>
                  <td mat-cell *matCellDef="let e">
                    <strong>{{ rotuloAcao(e.acao) }}</strong>
                    <span class="tc-muted sub">{{ rotuloEntidade(e.tipoEntidade) }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="details">
                  <th mat-header-cell *matHeaderCellDef>Detalhes</th>
                  <td mat-cell *matCellDef="let e" class="details">
                    @for (d of e.details | keyvalue; track d.key) {
                      <div><span class="tc-muted">{{ d.key }}:</span> <span class="mono">{{ formatar(d.value) }}</span></div>
                    } @empty { — }
                  </td>
                </ng-container>
                <ng-container matColumnDef="ip">
                  <th mat-header-cell *matHeaderCellDef>IP</th>
                  <td mat-cell *matCellDef="let e" class="mono">{{ e.ipAddress ?? '—' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                <tr mat-row *matRowDef="let linha; columns: colunas"></tr>
              </table>
            </div>
            <mat-paginator [length]="pagina.totalElementos" [pageIndex]="pagina.pagina" [pageSize]="pagina.tamanho"
              [pageSizeOptions]="[25, 50, 100]" (page)="aoPaginar($event)" aria-label="Paginação da auditoria" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .filters > .action { flex-basis: 320px; }
    .sub { display: block; font: var(--mat-sys-body-small); }
    .nowrap { white-space: nowrap; }
    .details { max-width: 420px; font: var(--mat-sys-body-small); word-break: break-word; }
  `,
})
export class AuditoriaLista {
  private readonly service = inject(AuditoriaService);

  protected readonly colunas = ['when', 'user', 'action', 'details', 'ip'];
  protected readonly acoes = Object.entries(ROTULOS_ACAO_AUDITORIA);
  protected readonly acao = new FormControl('', { nonNullable: true });
  protected readonly consulta = signal<ConsultaAuditoria>({ pagina: 0, tamanho: 50 });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly resultado = toSignal(
    toObservable(this.consulta).pipe(
      tap(() => {
        this.carregando.set(true);
        this.erro.set(null);
      }),
      switchMap((q) =>
        this.service.listar(q).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of<PaginaResponse<RegistroAuditoria> | null>(null);
          }),
          finalize(() => this.carregando.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  constructor() {
    this.acao.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((acao) => this.consulta.update((q) => ({ ...q, acao, pagina: 0 })));
  }

  aoPaginar(evento: PageEvent): void {
    this.consulta.update((q) => ({ ...q, pagina: evento.pageIndex, tamanho: evento.pageSize }));
  }

  rotuloAcao(acao: string): string {
    return ROTULOS_ACAO_AUDITORIA[acao] ?? acao;
  }

  rotuloEntidade(entidade: string | undefined): string {
    return entidade ? (ROTULOS_ENTIDADE_AUDITORIA[entidade] ?? entidade) : '';
  }

  formatar(valor: unknown): string {
    return typeof valor === 'object' && valor !== null ? JSON.stringify(valor) : String(valor);
  }
}

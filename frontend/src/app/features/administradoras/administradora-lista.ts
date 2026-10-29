import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, filter, finalize, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { AdministradoraDialog } from './administradora-dialog';
import { Administradora, AdministradoraService } from './administradora.service';

@Component({
  selector: 'tc-administradora-lista',
  imports: [DatePipe, DocumentoPipe, MatTableModule, MatPaginatorModule, MatButtonModule, MatIconModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Plataforma" titulo="Administradoras"
        subtitulo="Organizações clientes. Cada uma tem dados totalmente isolados das demais.">
        <button mat-flat-button (click)="cadastrar()"><mat-icon>add</mat-icon> Nova administradora</button>
      </tc-cabecalho-pagina>

      <section class="tc-table-card" [attr.aria-busy]="carregando()">
        <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
        @if (erro(); as mensagem) {
          <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar" [descricao]="mensagem" />
        } @else if (resultado(); as pagina) {
          @if (pagina.totalElementos === 0) {
            <tc-estado-vazio icone="corporate_fare" titulo="Nenhuma administradora cadastrada"
              descricao="Cadastre a primeira administradora e seu administrador para iniciar o uso da plataforma.">
              <button mat-flat-button (click)="cadastrar()">Cadastrar administradora</button>
            </tc-estado-vazio>
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="pagina.conteudo">
                <caption class="sr-only">Administradoras</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Nome</th>
                  <td mat-cell *matCellDef="let o"><strong>{{ o.nome }}</strong></td>
                </ng-container>
                <ng-container matColumnDef="cnpj">
                  <th mat-header-cell *matHeaderCellDef>CNPJ</th>
                  <td mat-cell *matCellDef="let o" class="mono">{{ o.cnpj | documento: 'cnpj' }}</td>
                </ng-container>
                <ng-container matColumnDef="created">
                  <th mat-header-cell *matHeaderCellDef>Cadastro</th>
                  <td mat-cell *matCellDef="let o">{{ o.createdAt | date: 'dd/MM/yyyy' }}</td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let o">
                    <span class="tc-status" [class.is-active]="o.ativo" [class.is-inactive]="!o.ativo">{{ o.ativo ? 'Ativa' : 'Inativa' }}</span>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                <tr mat-row *matRowDef="let linha; columns: colunas" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + linha.nome" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
              </table>
            </div>
            <mat-paginator [length]="pagina.totalElementos" [pageIndex]="pagina.pagina" [pageSize]="pagina.tamanho"
              [pageSizeOptions]="[20, 50, 100]" (page)="aoPaginar($event)" aria-label="Paginação" />
          }
        }
      </section>
    </div>
  `,
})
export class AdministradoraLista {
  private readonly service = inject(AdministradoraService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly router = inject(Router);

  protected readonly colunas = ['name', 'cnpj', 'created', 'status'];
  protected readonly consulta = signal({ pagina: 0, tamanho: 20 });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly resultado = toSignal(
    toObservable(this.consulta).pipe(
      tap(() => {
        this.carregando.set(true);
        this.erro.set(null);
      }),
      switchMap((q) =>
        this.service.listar(q.pagina, q.tamanho).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of<PaginaResponse<Administradora> | null>(null);
          }),
          finalize(() => this.carregando.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  cadastrar(): void {
    this.dialog
      .open<AdministradoraDialog, void, Administradora>(AdministradoraDialog, { width: '720px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((administradora): administradora is Administradora => !!administradora))
      .subscribe((administradora) => {
        this.notificacao.sucesso(`Administradora "${administradora.nome}" cadastrada.`);
        this.consulta.update((q) => ({ ...q }));
      });
  }

  abrir(o: Administradora): void {
    void this.router.navigate(['/organizacoes', o.id]);
  }

  aoPaginar(evento: PageEvent): void {
    this.consulta.set({ pagina: evento.pageIndex, tamanho: evento.pageSize });
  }
}

import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { UsuarioCadastroDialog } from './usuario-cadastro-dialog';
import { UsuarioDetalhe, ConsultaUsuario, UsuarioResumo } from './usuario.models';
import { UsuarioService } from './usuario.service';

@Component({
  selector: 'tc-usuario-lista',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    CabecalhoPagina,
    EstadoVazio,
  ],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Acesso" titulo="Usuários" subtitulo="Pessoas com acesso ao sistema e seus papéis.">
        @if (auth.pode('USER_MANAGE')) {
          <button mat-flat-button (click)="cadastrar()"><mat-icon>person_add</mat-icon> Novo usuário</button>
        }
      </tc-cabecalho-pagina>

      <div class="filters">
        <mat-form-field class="search">
          <mat-label>Buscar</mat-label>
          <mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="busca" placeholder="Nome ou e-mail" autocomplete="off" />
        </mat-form-field>
        <mat-form-field class="status">
          <mat-label>Situação</mat-label>
          <mat-select [formControl]="ativo">
            <mat-option value="">Todos</mat-option>
            <mat-option value="true">Ativos</mat-option>
            <mat-option value="false">Inativos</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <section class="tc-table-card" [attr.aria-busy]="carregando()">
        <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
        @if (erro(); as mensagem) {
          <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar os usuários" [descricao]="mensagem" />
        } @else if (resultado(); as pagina) {
          @if (pagina.totalElementos === 0) {
            <tc-estado-vazio icone="group" titulo="Nenhum usuário encontrado" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="pagina.conteudo">
                <caption class="sr-only">Usuários</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Usuário</th>
                  <td mat-cell *matCellDef="let u">
                    <strong class="block">{{ u.nome }}</strong>
                    <span class="tc-muted">{{ u.email }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="roles">
                  <th mat-header-cell *matHeaderCellDef>Papéis</th>
                  <td mat-cell *matCellDef="let u">{{ papeisDe(u) }}</td>
                </ng-container>
                <ng-container matColumnDef="last">
                  <th mat-header-cell *matHeaderCellDef>Último acesso</th>
                  <td mat-cell *matCellDef="let u">
                    @if (u.pendingInvite) {
                      <span class="tc-status is-pending">Convite pendente</span>
                    } @else {
                      {{ u.lastLoginAt | date: 'dd/MM/yyyy HH:mm' }}
                    }
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Situação</th>
                  <td mat-cell *matCellDef="let u">
                    <span class="tc-status" [class.is-active]="u.ativo" [class.is-inactive]="!u.ativo">{{ u.ativo ? 'Ativo' : 'Inativo' }}</span>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                <tr mat-row *matRowDef="let linha; colunas: colunas" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + linha.nome" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
              </table>
            </div>
            <mat-paginator [length]="pagina.totalElementos" [pageIndex]="pagina.pagina" [pageSize]="pagina.tamanho"
              [pageSizeOptions]="[20, 50, 100]" (page)="aoPaginar($event)" aria-label="Paginação de usuários" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .block { display: block; }
  `,
})
export class UsuarioLista {
  private readonly service = inject(UsuarioService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly auth = inject(AuthService);

  protected readonly colunas = ['name', 'roles', 'last', 'status'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly ativo = new FormControl<'' | 'true' | 'false'>('', { nonNullable: true });
  protected readonly consulta = signal<ConsultaUsuario>({ pagina: 0, tamanho: 20 });
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
            return of<PaginaResponse<UsuarioResumo> | null>(null);
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
      .subscribe((busca) => this.consulta.update((q) => ({ ...q, busca, pagina: 0 })));
    this.ativo.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((ativo) => this.consulta.update((q) => ({ ...q, ativo, pagina: 0 })));
  }

  papeisDe(u: UsuarioResumo): string {
    return u.papeis.length ? u.papeis.join(', ') : '—';
  }

  aoPaginar(evento: PageEvent): void {
    this.consulta.update((q) => ({ ...q, pagina: evento.pageIndex, tamanho: evento.pageSize }));
  }

  abrir(u: UsuarioResumo): void {
    void this.router.navigate(['/usuarios', u.id]);
  }

  cadastrar(): void {
    this.dialog
      .open<UsuarioCadastroDialog, void, UsuarioDetalhe>(UsuarioCadastroDialog, { width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((u): u is UsuarioDetalhe => !!u))
      .subscribe((u) => {
        this.notificacao.sucesso(`Convite enviado para ${u.email}.`);
        void this.router.navigate(['/usuarios', u.id]);
      });
  }
}

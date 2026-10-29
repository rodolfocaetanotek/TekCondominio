import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { PessoaDialog } from './pessoa-dialog';
import { PessoaDetalhe, PessoaResumo } from './estrutura.models';
import { EstruturaService } from './estrutura.service';

@Component({
  selector: 'tc-pessoa-lista',
  imports: [ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatProgressBarModule, MatCheckboxModule, CabecalhoPagina, EstadoVazio, DocumentoPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Cadastros" titulo="Pessoas" subtitulo="Proprietários, inquilinos e moradores — cadastro único por CPF/CNPJ.">
        @if (podeEditar()) {
          <button mat-flat-button (click)="cadastrar()"><mat-icon>person_add</mat-icon> Nova pessoa</button>
        }
      </tc-cabecalho-pagina>
      <div class="filters">
        <mat-form-field class="search">
          <mat-label>Buscar</mat-label>
          <mat-icon matPrefix>search</mat-icon>
          <input matInput [formControl]="busca" placeholder="Nome, e-mail ou CPF/CNPJ" autocomplete="off" />
        </mat-form-field>
        @if (contexto.id()) {
          <mat-checkbox [formControl]="apenasCondominio">Somente do condomínio selecionado</mat-checkbox>
        }
      </div>
      <section class="tc-table-card" [attr.aria-busy]="carregando()">
        <mat-progress-bar [mode]="carregando() ? 'indeterminate' : 'determinate'" [value]="0" [class.idle]="!carregando()" />
        @if (erro(); as mensagem) {
          <tc-estado-vazio icone="cloud_off" titulo="Não foi possível carregar" [descricao]="mensagem" />
        } @else if (resultado(); as pagina) {
          @if (pagina.totalElementos === 0) {
            <tc-estado-vazio icone="contacts" titulo="Nenhuma pessoa encontrada" />
          } @else {
            <div class="table-scroll">
              <table mat-table [dataSource]="pagina.conteudo">
                <caption class="sr-only">Pessoas</caption>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Nome</th>
                  <td mat-cell *matCellDef="let p"><strong class="block">{{ p.nome }}</strong>
                    <span class="tc-muted mono">{{ p.documento ? (p.documento | documento: 'cpfCnpj') : '' }}</span></td>
                </ng-container>
                <ng-container matColumnDef="contact">
                  <th mat-header-cell *matHeaderCellDef>Contato</th>
                  <td mat-cell *matCellDef="let p"><span class="block">{{ p.email ?? '' }}</span><span class="tc-muted">{{ p.telefone ? (p.telefone | documento: 'telefone') : '' }}</span></td>
                </ng-container>
                <ng-container matColumnDef="units">
                  <th mat-header-cell *matHeaderCellDef>Unidades (vínculo vigente)</th>
                  <td mat-cell *matCellDef="let p">{{ unidadesDe(p) }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="colunas"></tr>
                <tr mat-row *matRowDef="let linha; columns: colunas" class="is-clickable" tabindex="0" role="link"
                  [attr.aria-label]="'Abrir ' + linha.nome" (click)="abrir(linha)" (keydown.enter)="abrir(linha)"></tr>
              </table>
            </div>
            <mat-paginator [length]="pagina.totalElementos" [pageIndex]="pagina.pagina" [pageSize]="pagina.tamanho"
              [pageSizeOptions]="[20, 50, 100]" (page)="aoPaginar($event)" aria-label="Paginação de pessoas" />
          }
        }
      </section>
    </div>
  `,
  styles: `
    .block { display: block; }
  `,
})
export class PessoaLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly service = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);

  protected readonly colunas = ['name', 'contact', 'units'];
  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly apenasCondominio = new FormControl(false, { nonNullable: true });
  private readonly parametros = signal({ pagina: 0, tamanho: 20, busca: '', apenas: false });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditar = computed(() => this.contexto.podeAlgum('PEOPLE_EDIT'));
  private readonly consulta = computed(() => ({ ...this.parametros(), condominio: this.parametros().apenas ? this.contexto.id() : null }));

  protected readonly resultado = toSignal(
    toObservable(this.consulta).pipe(
      tap(() => {
        this.carregando.set(true);
        this.erro.set(null);
      }),
      switchMap((q) =>
        this.service.pessoas({ busca: q.busca, pagina: q.pagina, tamanho: q.tamanho, idCondominio: q.condominio ?? undefined }).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of<PaginaResponse<PessoaResumo> | null>(null);
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
      .subscribe((busca) => this.parametros.update((p) => ({ ...p, busca, pagina: 0 })));
    this.apenasCondominio.valueChanges.pipe(takeUntilDestroyed()).subscribe((apenas) => this.parametros.update((p) => ({ ...p, apenas, pagina: 0 })));
  }

  unidadesDe(p: PessoaResumo): string {
    return p.unidades.length ? p.unidades.join(', ') : '—';
  }

  aoPaginar(e: PageEvent): void {
    this.parametros.update((p) => ({ ...p, pagina: e.pageIndex, tamanho: e.pageSize }));
  }

  abrir(p: PessoaResumo): void {
    void this.router.navigate(['/pessoas', p.id]);
  }

  cadastrar(): void {
    this.dialog
      .open(PessoaDialog, { data: {}, width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((p): p is PessoaDetalhe => !!p))
      .subscribe((p) => {
        this.notificacao.sucesso(`${p.nome} cadastrado(a).`);
        void this.router.navigate(['/pessoas', p.id]);
      });
  }
}

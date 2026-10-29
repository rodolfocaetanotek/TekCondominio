import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, of, switchMap, tap } from 'rxjs';
import { PaginaResponse, paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import {
  ROTULOS_SITUACAO_CONDOMINIO,
  ROTULOS_TIPO_CONDOMINIO,
  ConsultaCondominio,
  SituacaoCondominio,
  CondominioResumo,
} from './condominio.models';
import { CondominioService } from './condominio.service';

@Component({
  selector: 'tc-condominio-lista',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    CabecalhoPagina,
    EstadoVazio,
    DocumentoPipe,
  ],
  templateUrl: './condominio-lista.html',
  styleUrl: './condominio-lista.scss',
})
export class CondominioLista {
  private readonly service = inject(CondominioService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly rotulosTipo = ROTULOS_TIPO_CONDOMINIO;
  protected readonly rotulosSituacao = ROTULOS_SITUACAO_CONDOMINIO;

  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly situacao = new FormControl<SituacaoCondominio | ''>('', { nonNullable: true });

  protected readonly consulta = signal<ConsultaCondominio>({ pagina: 0, tamanho: 20, ordem: 'nome,asc', busca: '', situacao: '' });
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);

  private readonly ehCelular = toSignal(
    inject(BreakpointObserver).observe('(max-width: 720px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
  protected readonly colunas = computed(() =>
    this.ehCelular() ? ['nome', 'situacao'] : ['nome', 'tipo', 'cidade', 'situacao'],
  );

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
            return of<PaginaResponse<CondominioResumo> | null>(null);
          }),
          finalize(() => this.carregando.set(false)),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly temFiltros = computed(() => !!this.consulta().busca || !!this.consulta().situacao);

  constructor() {
    this.busca.valueChanges
      .pipe(debounceTime(300), map((v) => v.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((busca) => this.consulta.update((q) => ({ ...q, busca, pagina: 0 })));
    this.situacao.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((situacao) => this.consulta.update((q) => ({ ...q, situacao, pagina: 0 })));
  }

  aoOrdenar(ordem: Sort): void {
    const valor = ordem.direction ? `${ordem.active},${ordem.direction}` : 'nome,asc';
    this.consulta.update((q) => ({ ...q, ordem: valor, pagina: 0 }));
  }

  aoPaginar(evento: PageEvent): void {
    this.consulta.update((q) => ({ ...q, pagina: evento.pageIndex, tamanho: evento.pageSize }));
  }

  limparFiltros(): void {
    this.busca.setValue('', { emitEvent: false });
    this.situacao.setValue('', { emitEvent: false });
    this.consulta.update((q) => ({ ...q, busca: '', situacao: '', pagina: 0 }));
  }

  tentarNovamente(): void {
    this.consulta.update((q) => ({ ...q }));
  }

  rotuloTipo(c: CondominioResumo): string {
    return this.rotulosTipo[c.tipo];
  }

  rotuloSituacao(c: CondominioResumo): string {
    return this.rotulosSituacao[c.situacao];
  }

  abrir(linha: CondominioResumo): void {
    void this.router.navigate(['/condominios', linha.id]);
  }
}

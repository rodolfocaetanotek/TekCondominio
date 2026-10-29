import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { abrirFormAssembleia } from './assembleia-form';
import { ResumoAssembleia, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-assembleia-lista',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, CabecalhoPagina, EstadoVazio, CondominioObrigatorio, RotuloPipe],
  template: `
    <div class="tc-page narrow">
      <tc-cabecalho-pagina sobretitulo="Governança" titulo="Assembleias" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) { <button mat-flat-button (click)="cadastrar()"><mat-icon>groups</mat-icon> Nova assembleia</button> }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <section class="tc-table-card">
          @for (a of lista() ?? []; track a.id) {
            <a class="row" [routerLink]="['/assembleias', a.id]">
              <span class="tc-ico" aria-hidden="true"><mat-icon>event</mat-icon></span>
              <div class="who"><strong>{{ a.titulo }}</strong>
                <span class="tc-muted">{{ a.tipo | rotulo: 'assemblyKind' }} · {{ a.primeiraConvocacaoEm | date: 'dd/MM/yyyy HH:mm' }} · {{ a.local }}</span></div>
              <span class="tc-status" [class.is-success]="a.situacao === 'CLOSED'" [class.is-pending]="a.situacao === 'CALLED'" [class.is-info]="a.situacao === 'IN_PROGRESS'"
                [class.is-inactive]="a.situacao === 'CANCELLED' || a.situacao === 'DRAFT'">{{ a.situacao | rotulo: 'assemblyStatus' }}</span>
              <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
            </a>
          } @empty { <tc-estado-vazio icone="groups" titulo="Nenhuma assembleia" /> }
        </section>
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .row { display: flex; align-items: center; gap: 8px 14px; flex-wrap: wrap; padding: 14px 20px; color: inherit; text-decoration: none; transition: background-color 0.12s; }
    .row + .row { border-top: 1px solid var(--tc-border); }
    .row:hover, .row:focus-visible { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .row:focus-visible { outline: none; box-shadow: inset 3px 0 0 var(--mat-sys-primary); }
    .row:hover strong { color: var(--mat-sys-primary); }
    .who { flex: 1 1 220px; display: grid; gap: 2px; min-width: 0; }
    .who strong { overflow-wrap: anywhere; }
    .who .tc-muted { font: var(--mat-sys-body-small); }
    .chev { color: var(--tc-text-muted); }
    @media (max-width: 600px) {
      .row { padding: 12px 14px; }
      .chev { display: none; }
      .tc-status { margin-left: 52px; }
    }
  `,
})
export class AssembleiaLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('ASSEMBLY_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  protected readonly lista = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.assembleias(c!).pipe(catchError(() => of([] as ResumoAssembleia[]))))));

  cadastrar(): void {
    abrirFormAssembleia(this.dialog, this.api, this.contexto.id()!).pipe(filter((r) => !!r)).subscribe((a) => {
      this.notificacao.sucesso('Assembleia criada como rascunho.');
      void this.router.navigate(['/assembleias', a!.id]);
    });
  }
}

import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, forkJoin, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EncerrarVinculoDialog, VinculoDialog } from './vinculo-dialogs';
import { ROTULOS_TIPO_VINCULO, Unidade, ROTULOS_TIPO_UNIDADE, Vinculo } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { UnidadeDialog, DadosUnidadeDialog } from './unidade-dialog';

@Component({
  selector: 'tc-unidade-detalhe',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule, CabecalhoPagina,
    EstadoVazio, CondominioObrigatorio, DataIsoPipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/unidades" class="back"><mat-icon>arrow_back</mat-icon> Unidades</a>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir a unidade" [descricao]="mensagem" /></section>
      } @else if (estado(); as s) {
        <tc-cabecalho-pagina sobretitulo="Unidade" [titulo]="s.unidade.rotulo" [subtitulo]="rotuloTipo(s.unidade) + (s.unidade.andar ? ' · ' + s.unidade.andar + 'º andar' : '')">
          <span class="tc-status big" [class.is-active]="s.unidade.ativo" [class.is-inactive]="!s.unidade.ativo">{{ s.unidade.ativo ? 'Ativa' : 'Inativa' }}</span>
          @if (podeEditarEstrutura()) {
            <button mat-stroked-button (click)="editar(s.unidade)"><mat-icon>edit</mat-icon> Editar</button>
            <button mat-button (click)="alternar(s.unidade)">{{ s.unidade.ativo ? 'Inativar' : 'Reativar' }}</button>
          }
        </tc-cabecalho-pagina>

        <section class="facts tc-dl cols tc-card">
          <div><span class="k">Fração ideal</span><strong class="mono">{{ s.unidade.fracaoIdeal | number: '1.2-6' }}%</strong></div>
          <div><span class="k">Área</span><strong>{{ s.unidade.area ? (s.unidade.area | number: '1.2-2') + ' m²' : '—' }}</strong></div>
          <div><span class="k">Proprietário(s)</span><strong>{{ s.unidade.nomeProprietario ?? '—' }}</strong></div>
          <div><span class="k">Responsável financeiro</span><strong>{{ s.unidade.nomeResponsavelFinanceiro ?? 'não definido' }}</strong></div>
          @if (s.unidade.observacoes) {
            <div class="wide"><span class="k">Observações</span><span>{{ s.unidade.observacoes }}</span></div>
          }
        </section>

        <section class="tc-table-card">
          <div class="tc-card-head is-divided">
            <h2 class="tc-section-title">Pessoas vinculadas e histórico de ocupação</h2>
            @if (podeEditarPessoas() && s.unidade.ativo) {
              <button mat-flat-button (click)="link(s.unidade, s.vinculos)"><mat-icon>person_add</mat-icon> Vincular pessoa</button>
            }
          </div>
          @for (o of s.vinculos; track o.id) {
            <div class="occ" [class.ended]="!o.vigente">
              <mat-icon class="tc-ico" aria-hidden="true">{{ o.tipo === 'OWNER' ? 'key' : o.tipo === 'TENANT' ? 'real_estate_agent' : 'person' }}</mat-icon>
              <div class="who">
                <a [routerLink]="['/pessoas', o.idPessoa]"><strong>{{ o.nomePessoa }}</strong></a>
                <span class="tc-muted">{{ rotuloPapel(o) }} · desde {{ o.dataInicio | dataIso }}
                  @if (!o.vigente) { até {{ o.dataFim | dataIso }}{{ o.motivoEncerramento ? ' — ' + o.motivoEncerramento : '' }} }
                </span>
              </div>
              <div class="occ-end">
              @if (o.responsavelFinanceiro) {
                <span class="tc-chip-soft"><mat-icon inline>payments</mat-icon> Resp. financeiro</span>
              }
              @if (!o.vigente) {
                <span class="tc-status is-inactive">Encerrado</span>
              } @else if (podeEditarPessoas()) {
                @if (!o.responsavelFinanceiro) {
                  <button mat-icon-button (click)="tornarResponsavelFinanceiro(o)" matTooltip="Definir como responsável financeiro" aria-label="Definir como responsável financeiro">
                    <mat-icon>payments</mat-icon>
                  </button>
                }
                <button mat-icon-button (click)="encerrar(o)" matTooltip="Encerrar vínculo" aria-label="Encerrar vínculo"><mat-icon>event_busy</mat-icon></button>
              }
              </div>
            </div>
          } @empty {
            <tc-estado-vazio icone="group_off" titulo="Nenhuma pessoa vinculada" descricao="Vincule proprietário, inquilino ou moradores." />
          }
        </section>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .back { margin: -8px 0 8px -12px; }
    .facts { --tc-dl-min: 180px; gap: 16px 24px; margin-bottom: 16px; }
    .facts .wide { padding-top: 12px; border-top: 1px solid var(--tc-border); }
    .occ { display: flex; align-items: center; gap: 8px 12px; padding: 12px 20px; flex-wrap: wrap; }
    .occ + .occ { border-top: 1px solid var(--tc-border); }
    .occ.ended { opacity: .7; }
    .who { flex: 1 1 200px; min-width: 0; display: grid; gap: 2px; }
    .occ-end { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-left: auto; }
    @media (max-width: 600px) { .occ { padding: 12px 16px; } }
  `,
})
export class UnidadeDetalhe {
  protected readonly contexto = inject(CondominioContexto);
  private readonly service = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);

  readonly id = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditarEstrutura = computed(() => this.contexto.pode('STRUCTURE_EDIT'));
  protected readonly podeEditarPessoas = computed(() => this.contexto.pode('PEOPLE_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');

  protected readonly estado = toSignal(
    combineLatest([toObservable(this.contexto.id), toObservable(this.id), toObservable(this.recarga)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, id]) =>
        forkJoin({
          unidade: this.service.unidade(c!, id),
          vinculos: this.contexto.pode('PEOPLE_VIEW') ? this.service.vinculos(c!, id) : of([] as Vinculo[]),
        }).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of(null);
          }),
        ),
      ),
    ),
    { initialValue: null },
  );

  rotuloTipo(u: Unidade): string {
    return ROTULOS_TIPO_UNIDADE[u.tipo];
  }

  rotuloPapel(o: Vinculo): string {
    return ROTULOS_TIPO_VINCULO[o.tipo];
  }

  editar(unidade: Unidade): void {
    const idCondominio = this.contexto.id()!;
    this.service.blocos(idCondominio).subscribe((blocos) =>
      this.dialog
        .open<UnidadeDialog, DadosUnidadeDialog, Unidade>(UnidadeDialog, { data: { idCondominio, blocos, unidade }, width: '640px', maxWidth: '95vw' })
        .afterClosed()
        .pipe(filter((u) => !!u))
        .subscribe(() => this.recarregar('Unidade atualizada.')),
    );
  }

  alternar(unidade: Unidade): void {
    this.service.alterarUnidadeAtiva(this.contexto.id()!, unidade.id, !unidade.ativo).subscribe({
      next: () => this.recarregar(unidade.ativo ? 'Unidade inativada.' : 'Unidade reativada.'),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  link(unidade: Unidade, vinculos: Vinculo[]): void {
    this.dialog
      .open(VinculoDialog, {
        data: { idCondominio: this.contexto.id()!, idUnidade: unidade.id, rotuloUnidade: unidade.rotulo, temFinanceiro: vinculos.some((o) => o.vigente && o.responsavelFinanceiro) },
        width: '560px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((o) => !!o))
      .subscribe(() => this.recarregar('Pessoa vinculada.'));
  }

  encerrar(o: Vinculo): void {
    this.dialog
      .open(EncerrarVinculoDialog, { data: { idCondominio: this.contexto.id()!, vinculo: o }, width: '440px' })
      .afterClosed()
      .pipe(filter((r) => !!r))
      .subscribe(() => this.recarregar('Vínculo encerrado.'));
  }

  tornarResponsavelFinanceiro(o: Vinculo): void {
    this.service.tornarResponsavelFinanceiro(this.contexto.id()!, o.id).subscribe({
      next: () => this.recarregar(`${o.nomePessoa} agora é o responsável financeiro.`),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }

  private recarregar(mensagem: string): void {
    this.notificacao.sucesso(mensagem);
    this.recarga.update((t) => t + 1);
  }
}

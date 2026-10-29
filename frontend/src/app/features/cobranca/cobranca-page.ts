import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Acordo, FinanceiroApi, Inadimplencia, Rateio } from '../financeiro/financeiro-api';
import { RateioDialog } from './rateio-dialog';
import { RetornoBancarioTab } from './retorno-bancario-tab';

@Component({
  selector: 'tc-cobranca-page',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, CabecalhoPagina, CondominioObrigatorio,
    EstadoVazio, DataIsoPipe, RotuloPipe, RetornoBancarioTab],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Cobrança" titulo="Cobrança condominial" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) {
          <button mat-flat-button (click)="gerar()"><mat-icon>request_quote</mat-icon> Gerar cobranças</button>
        }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Rateios / competências">
            <section class="tc-table-card">
              @for (a of rateios() ?? []; track a.id) {
                <div class="row" [class.off]="a.situacao === 'CANCELLED'">
                  <div class="who">
                    <strong>{{ a.descricao }}</strong>
                    <span class="tc-muted">{{ a.competencia.slice(5, 7) }}/{{ a.competencia.slice(0, 4) }} · {{ a.criterio | rotulo: 'allocationMethod' }} ·
                      {{ a.quantidadeUnidades }} unidades · vence {{ a.dataVencimento | dataIso }}{{ a.motivoCancelamento ? ' · cancelado: ' + a.motivoCancelamento : '' }}</span>
                  </div>
                  <div class="nums">
                    <span class="money">{{ a.valorGerado | currency: 'BRL' }}</span>
                    <small class="tc-muted">recebido {{ a.valorRecebido | currency: 'BRL' }} · {{ a.quantidadePagas }}/{{ a.quantidadeUnidades }} pagas</small>
                  </div>
                  @if (a.situacao === 'GENERATED' && podeEditar() && contexto.pode('FINANCE_CANCEL') && a.valorRecebido === 0) {
                    <button mat-button (click)="cancelarRateio(a)">Cancelar</button>
                  }
                </div>
              } @empty {
                <tc-estado-vazio icone="request_quote" titulo="Nenhuma cobrança gerada"
                  descricao="Gere as cobranças do mês: o valor é rateado entre as unidades e cada uma recebe seu título a receber." />
              }
            </section>
          </mat-tab>
          <mat-tab label="Inadimplência">
            <ng-template matTabContent>
              @if (inadimplencia(); as d) {
                <section class="tc-kpis" aria-label="Indicadores de inadimplência">
                  <div class="tc-kpi tc-card tone-danger">
                    <span class="icon"><mat-icon aria-hidden="true">money_off</mat-icon></span>
                    <span class="label">Principal vencido</span><span class="value">{{ d.totalPrincipal | currency: 'BRL' }}</span>
                  </div>
                  <div class="tc-kpi tc-card tone-warning">
                    <span class="icon"><mat-icon aria-hidden="true">percent</mat-icon></span>
                    <span class="label">Multa + juros até hoje</span><span class="value">{{ d.totalEncargos | currency: 'BRL' }}</span>
                  </div>
                  <div class="tc-kpi tc-card tone-info">
                    <span class="icon"><mat-icon aria-hidden="true">monitoring</mat-icon></span>
                    <span class="label">Índice de inadimplência</span><span class="value">{{ d.indiceInadimplencia | number: '1.2-2' }}%</span>
                    <span class="sub">vencido ÷ emitido em 12 meses</span>
                  </div>
                  <div class="tc-kpi tc-card">
                    <span class="icon"><mat-icon aria-hidden="true">door_front</mat-icon></span>
                    <span class="label">Unidades em débito</span><span class="value">{{ d.unidadesEmDebito }} de {{ d.unidadesAtivas }}</span>
                  </div>
                </section>
                <section class="tc-table-card">
                  @for (r of d.linhas; track r.idUnidade) {
                    <a class="row link" [routerLink]="['/cobranca/unidades', r.idUnidade]">
                      <div class="who">
                        <strong>{{ r.rotuloUnidade }}</strong>
                        <span class="tc-muted">{{ r.nomeResponsavel ?? 'sem responsável' }} · {{ r.quantidadeVencidas }} título(s) · {{ r.maximoDiasAtraso }} dias de atraso
                          {{ r.dataUltimoContato ? ' · último contato ' + (r.dataUltimoContato | dataIso) : '' }}</span>
                      </div>
                      <div class="nums"><span class="money negative">{{ r.total | currency: 'BRL' }}</span>
                        <small class="tc-muted">principal {{ r.principal | currency: 'BRL' }}</small></div>
                      <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
                    </a>
                  } @empty {
                    <tc-estado-vazio icone="sentiment_satisfied" titulo="Nenhuma unidade inadimplente" />
                  }
                </section>
              }
            </ng-template>
          </mat-tab>
          <mat-tab label="Acordos">
            <ng-template matTabContent>
              <section class="tc-table-card">
                @for (a of acordos() ?? []; track a.id) {
                  <div class="row" [class.off]="a.situacao === 'CANCELLED'">
                    <div class="who">
                      <a [routerLink]="['/cobranca/unidades', a.idUnidade]"><strong>{{ a.rotuloUnidade }}</strong></a>
                      <span class="tc-muted">{{ a.totalParcelas }}x a partir de {{ a.dataPrimeiroVencimento | dataIso }} · {{ a.parcelasPagas }}/{{ a.totalParcelas }} pagas
                        · original {{ a.valorOriginal | currency: 'BRL' }} + encargos {{ a.valorEncargos | currency: 'BRL' }} − desconto {{ a.desconto | currency: 'BRL' }}</span>
                    </div>
                    <div class="nums"><span class="money">{{ a.valorTotal | currency: 'BRL' }}</span>
                      <small class="tc-status" [class.is-active]="a.situacao === 'ACTIVE'" [class.is-success]="a.situacao === 'COMPLETED'" [class.is-inactive]="a.situacao === 'CANCELLED'">{{ a.situacao | rotulo: 'agreementStatus' }}</small></div>
                    @if (a.situacao === 'ACTIVE' && a.parcelasPagas === 0 && podeEditar()) {
                      <button mat-button (click)="cancelarAcordo(a)">Cancelar</button>
                    }
                  </div>
                } @empty {
                  <tc-estado-vazio icone="handshake" titulo="Nenhum acordo" descricao="Acordos são criados no demonstrativo de débitos da unidade (aba Inadimplência)." />
                }
              </section>
            </ng-template>
          </mat-tab>
          <mat-tab label="Retorno bancário"><ng-template matTabContent><tc-retorno-bancario-tab /></ng-template></mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
  styles: `
    .row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 16px; padding: 14px 20px; border-top: 1px solid var(--tc-border); color: inherit; text-decoration: none; }
    .row:first-child { border-top: 0; }
    .row.link { transition: background-color 0.12s; }
    .row.link:hover, .row.link:focus-visible { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .row.link:hover strong { color: var(--mat-sys-primary); }
    .row.off { opacity: .55; }
    .who { flex: 1 1 260px; display: grid; gap: 2px; min-width: 0; }
    .who .tc-muted { font: var(--mat-sys-body-small); }
    .nums { display: grid; justify-items: end; gap: 2px; text-align: right; }
    .nums .money { font-weight: 650; font-size: 15px; }
    .chev { color: var(--tc-text-muted); }
    @media (max-width: 600px) {
      .row { padding: 12px 16px; }
      .nums { flex: 1 1 auto; }
    }
  `,
})
export class CobrancaPage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(FinanceiroApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('BILLING_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly origem = combineLatest([toObservable(this.contexto.id), toObservable(this.recarga)]).pipe(filter(([c]) => !!c));

  protected readonly rateios = toSignal(this.origem.pipe(switchMap(([c]) => this.api.rateios(c!).pipe(catchError(() => of([] as Rateio[]))))));
  protected readonly inadimplencia = toSignal(this.origem.pipe(switchMap(([c]) => this.api.inadimplencia(c!).pipe(catchError(() => of(null as Inadimplencia | null))))));
  protected readonly acordos = toSignal(this.origem.pipe(switchMap(([c]) => this.api.acordos(c!).pipe(catchError(() => of([] as Acordo[]))))));

  gerar(): void {
    this.dialog.open(RateioDialog, { data: { idCondominio: this.contexto.id()! }, width: '760px', maxWidth: '95vw' })
      .afterClosed().pipe(filter((r) => !!r)).subscribe(() => {
        this.notificacao.sucesso('Cobranças geradas.');
        this.recarga.update((t) => t + 1);
      });
  }

  cancelarRateio(a: Rateio): void {
    pedirMotivo(this.dialog, 'Cancelar cobranças', `Cancelar "${a.descricao}" e as ${a.quantidadeUnidades} cobranças geradas?`, 'Cancelar cobranças')
      .pipe(switchMap((motivo) => this.api.cancelarRateio(this.contexto.id()!, a.id, motivo)))
      .subscribe({ next: () => { this.notificacao.sucesso('Cobranças canceladas.'); this.recarga.update((t) => t + 1); },
        error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  cancelarAcordo(a: Acordo): void {
    pedirMotivo(this.dialog, 'Cancelar acordo', 'As parcelas serão canceladas e os débitos originais voltarão a ficar em aberto.', 'Cancelar acordo')
      .pipe(switchMap((motivo) => this.api.cancelarAcordo(this.contexto.id()!, a.id, motivo)))
      .subscribe({ next: () => { this.notificacao.sucesso('Acordo cancelado.'); this.recarga.update((t) => t + 1); },
        error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }
}

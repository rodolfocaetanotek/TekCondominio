import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, combineLatest, filter, map, merge, of, scan, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DefinicaoCampo, abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { EstruturaService } from '../estrutura/estrutura.service';
import { AreaComum, Reserva, ReservaApi } from './reserva-api';

/** Administração das áreas comuns e das reservas (aprovação, cancelamento, reserva em nome da unidade). */
@Component({
  selector: 'tc-reserva-page',
  imports: [CurrencyPipe, ReactiveFormsModule, MatTabsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTooltipModule, MatMenuModule, CabecalhoPagina, CondominioObrigatorio, EstadoVazio, DataIsoPipe, RotuloPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Administração" titulo="Reservas de áreas comuns" [subtitulo]="contexto.selecionado()?.nome">
        @if (podeEditar()) {
          <button mat-stroked-button (click)="editarArea()"><mat-icon>add_business</mat-icon> Nova área</button>
          <button mat-flat-button (click)="reservar()" [disabled]="!(areas()?.length)"><mat-icon>event_available</mat-icon> Nova reserva</button>
        }
      </tc-cabecalho-pagina>
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Reservas">
            <div class="filters">
              <mat-form-field><mat-label>De</mat-label><input matInput type="date" [formControl]="de" /></mat-form-field>
              <mat-form-field><mat-label>Até</mat-label><input matInput type="date" [formControl]="ate" /></mat-form-field>
              <mat-form-field class="scope"><mat-label>Área</mat-label>
                <mat-select [formControl]="area"><mat-option [value]="''">Todas</mat-option>
                  @for (a of areas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}</mat-option> }
                </mat-select></mat-form-field>
            </div>
            <section class="tc-table-card">
              <div class="table-scroll">
              <table class="lines tc-cards-mobile">
                <thead><tr><th>Data</th><th>Horário</th><th>Área</th><th>Unidade</th><th>Solicitante</th><th class="money">Taxa</th><th>Situação</th><th></th></tr></thead>
                <tbody>
                  @for (r of reservas() ?? []; track r.id) {
                    <tr>
                      <td>{{ r.data | dataIso }}</td><td>{{ r.horaInicio.slice(0, 5) }}–{{ r.horaFim.slice(0, 5) }}</td>
                      <td>{{ r.nomeArea }}</td><td>{{ r.rotuloUnidade }}</td>
                      <td>{{ r.nomeSolicitante }}{{ r.peloMorador ? ' (portal)' : '' }}</td>
                      <td class="money">{{ r.taxa | currency: 'BRL' }}</td>
                      <td><span class="status-cell"><span class="tc-status" [class.is-success]="r.situacao === 'CONFIRMED'" [class.is-pending]="r.situacao === 'PENDING'"
                        [class.is-danger]="r.situacao === 'REJECTED'" [class.is-inactive]="r.situacao === 'CANCELLED'">{{ r.situacao | rotulo: 'reservationStatus' }}</span>
                        @if (r.motivoCancelamento || r.observacaoDecisao) { <span class="tc-muted small">{{ r.motivoCancelamento ?? r.observacaoDecisao }}</span> }</span></td>
                      <td class="acts">
                        @if (podeEditar()) {
                          @if (r.situacao === 'PENDING') {
                            <button mat-icon-button class="approve" (click)="aprovar(r)" matTooltip="Aprovar" aria-label="Aprovar"><mat-icon>check_circle</mat-icon></button>
                            <button mat-icon-button [matMenuTriggerFor]="maisAcoes" matTooltip="Mais ações" [attr.aria-label]="'Mais ações da reserva de ' + r.nomeArea + ', unidade ' + r.rotuloUnidade">
                              <mat-icon>more_vert</mat-icon></button>
                            <mat-menu #maisAcoes="matMenu" xPosition="before">
                              <button mat-menu-item (click)="reprovar(r)"><mat-icon>do_not_disturb_on</mat-icon><span>Recusar</span></button>
                              <button mat-menu-item (click)="cancelar(r)"><mat-icon>event_busy</mat-icon><span>Cancelar</span></button>
                            </mat-menu>
                          } @else if (r.situacao === 'CONFIRMED') {
                            <button mat-icon-button (click)="cancelar(r)" matTooltip="Cancelar" aria-label="Cancelar"><mat-icon>event_busy</mat-icon></button>
                          }
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr class="empty-row"><td colspan="8"><tc-estado-vazio icone="event" titulo="Nenhuma reserva no período" /></td></tr>
                  }
                </tbody>
              </table>
              </div>
            </section>
          </mat-tab>
          <mat-tab label="Áreas comuns">
            <div class="areas">
              @for (a of areas() ?? []; track a.id) {
                <section class="tc-card area" [class.inactive]="!a.ativa">
                  <div class="head"><strong class="name">{{ a.nome }}</strong>
                    @if (podeEditar()) { <button mat-icon-button (click)="editarArea(a)" aria-label="Editar área"><mat-icon>edit</mat-icon></button> }</div>
                  <span class="tc-muted meta">{{ a.horaAbertura.slice(0, 5) }}–{{ a.horaFechamento.slice(0, 5) }} · até {{ a.duracaoMaximaMinutos / 60 }} h
                    · taxa {{ a.taxa | currency: 'BRL' }}{{ a.capacidade ? ' · ' + a.capacidade + ' pessoas' : '' }}</span>
                  <span class="small">{{ a.exigeAprovacao ? 'Exige aprovação' : 'Confirmação automática' }} · antecedência mín. {{ a.antecedenciaMinimaHoras }} h
                    {{ a.limiteMensalPorUnidade ? ' · ' + a.limiteMensalPorUnidade + '/mês por unidade' : '' }}{{ a.bloqueiaInadimplente ? ' · bloqueia inadimplentes' : '' }}</span>
                  @if (!a.ativa) { <span class="tc-status is-inactive">Inativa</span> }
                </section>
              } @empty {
                <section class="tc-card empty">
                  <tc-estado-vazio icone="deck" titulo="Nenhuma área cadastrada" descricao="Cadastre salão de festas, churrasqueira, quadra etc." />
                </section>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
  styles: `
    table.lines th:first-child, table.lines td:first-child { padding-left: 20px; }
    table.lines th:last-child, table.lines td:last-child { padding-right: 20px; }
    .acts { white-space: nowrap; text-align: right; width: 1%; }
    .approve { color: var(--tc-success); }
    .status-cell { display: grid; gap: 2px; justify-items: start; }
    .small { font: var(--mat-sys-body-small); }
    .empty-row td { padding: 0; }
    .empty-row:hover td { background: transparent; }
    .areas { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr)); gap: 12px; }
    .area { display: grid; gap: 6px; align-content: start; }
    .area.inactive { opacity: .6; }
    .empty { grid-column: 1 / -1; }
    .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin: -4px -8px 0 0; }
    .name { font: var(--mat-sys-title-small); font-weight: 600; min-width: 0; overflow-wrap: anywhere; }
    .meta { font: var(--mat-sys-body-small); }
    .area .tc-status { justify-self: start; }
    @media (max-width: 720px) {
      .status-cell { justify-items: end; text-align: right; }
      table.lines td.acts { justify-content: flex-end; width: auto; }
      table.lines td.acts:empty { display: none; }
      table.lines tr.empty-row { padding: 0; }
      table.lines tr.empty-row td { display: block; }
    }
  `,
})
export class ReservaPage {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(ReservaApi);
  private readonly estrutura = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly de = new FormControl(hojeIso(), { nonNullable: true });
  protected readonly ate = new FormControl(somarDias(hojeIso(), 60), { nonNullable: true });
  protected readonly area = new FormControl('', { nonNullable: true });
  private readonly filtros = toSignal(merge(this.de.valueChanges, this.ate.valueChanges, this.area.valueChanges).pipe(scan((n) => n + 1, 0)), { initialValue: 0 });

  protected readonly podeEditar = computed(() => this.contexto.pode('RESERVATION_EDIT') && this.contexto.selecionado()?.situacao === 'ACTIVE');
  private readonly origem = combineLatest([toObservable(this.contexto.id), toObservable(this.recarga), toObservable(this.filtros)]).pipe(filter(([c]) => !!c));
  protected readonly areas = toSignal(this.origem.pipe(switchMap(([c]) => this.api.areas(c!).pipe(catchError(() => of([] as AreaComum[]))))));
  protected readonly reservas = toSignal(this.origem.pipe(switchMap(([c]) =>
    this.api.listar(c!, this.de.value, this.ate.value, this.area.value || undefined).pipe(catchError(() => of([] as Reserva[]))))));

  editarArea(a?: AreaComum): void {
    const campos: DefinicaoCampo[] = [
      { chave: 'nome', rotulo: 'Nome', obrigatorio: true, tamanhoMaximo: 80, valor: a?.nome, colunas: 8 },
      { chave: 'capacidade', rotulo: 'Capacidade (pessoas)', tipo: 'number', valor: a?.capacidade ?? null, colunas: 4 },
      { chave: 'horaAbertura', rotulo: 'Abre às', tipo: 'time', obrigatorio: true, valor: a?.horaAbertura.slice(0, 5) ?? '08:00', colunas: 4 },
      { chave: 'horaFechamento', rotulo: 'Fecha às', tipo: 'time', obrigatorio: true, valor: a?.horaFechamento.slice(0, 5) ?? '22:00', colunas: 4 },
      { chave: 'duracaoMaximaMinutos', rotulo: 'Duração máx. (min)', tipo: 'number', valor: a?.duracaoMaximaMinutos ?? 720, colunas: 4 },
      { chave: 'taxa', rotulo: 'Taxa de uso (R$)', tipo: 'number', valor: a?.taxa ?? 0, colunas: 4, dica: 'Gera cobrança na unidade ao confirmar' },
      { chave: 'antecedenciaMinimaHoras', rotulo: 'Antecedência mínima (h)', tipo: 'number', valor: a?.antecedenciaMinimaHoras ?? 24, colunas: 4 },
      { chave: 'antecedenciaMaximaDias', rotulo: 'Antecedência máxima (dias)', tipo: 'number', valor: a?.antecedenciaMaximaDias ?? 90, colunas: 4 },
      { chave: 'limiteMensalPorUnidade', rotulo: 'Limite por unidade/mês', tipo: 'number', valor: a?.limiteMensalPorUnidade ?? null, colunas: 6 },
      { chave: 'exigeAprovacao', rotulo: 'Reservas do portal exigem aprovação', tipo: 'checkbox', valor: a?.exigeAprovacao ?? false },
      { chave: 'bloqueiaInadimplente', rotulo: 'Bloquear unidades com cobranças vencidas', tipo: 'checkbox', valor: a?.bloqueiaInadimplente ?? false },
      { chave: 'regras', rotulo: 'Regras de uso (exibidas ao morador)', tipo: 'textarea', tamanhoMaximo: 2000, valor: a?.regras ?? null },
    ];
    if (a) campos.push({ chave: 'ativa', rotulo: 'Área ativa', tipo: 'checkbox', valor: a.ativa });
    abrirFormulario(this.dialog, {
      titulo: a ? 'Editar área comum' : 'Nova área comum',
      campos,
      enviar: (v) => this.api.salvarArea(this.contexto.id()!, v, a?.id),
    }).pipe(filter((r) => !!r)).subscribe(() => {
      this.notificacao.sucesso('Área salva.');
      this.recarga.update((t) => t + 1);
    });
  }

  reservar(): void {
    const c = this.contexto.id()!;
    this.estrutura.unidades(c, { pagina: 0, tamanho: 500, ativo: 'true' }).pipe(map((p) => p.conteudo)).subscribe((unidades) =>
      abrirFormulario(this.dialog, {
        titulo: 'Nova reserva',
        introducao: 'Reservas lançadas pela administração são confirmadas na hora (a antecedência mínima não se aplica).',
        campos: [
          { chave: 'idArea', rotulo: 'Área', tipo: 'select', obrigatorio: true, colunas: 6,
            opcoes: (this.areas() ?? []).filter((a) => a.ativa).map((a) => ({ valor: a.id, rotulo: a.nome })) },
          { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', obrigatorio: true, colunas: 6, opcoes: unidades.map((u) => ({ valor: u.id, rotulo: u.rotulo })) },
          { chave: 'data', rotulo: 'Data', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 4 },
          { chave: 'horaInicio', rotulo: 'Início', tipo: 'time', obrigatorio: true, colunas: 4 },
          { chave: 'horaFim', rotulo: 'Fim', tipo: 'time', obrigatorio: true, colunas: 4 },
          { chave: 'convidados', rotulo: 'Convidados', tipo: 'number', colunas: 4 },
          { chave: 'observacoes', rotulo: 'Observações', tamanhoMaximo: 500, colunas: 8 },
        ],
        enviar: (v) => this.api.cadastrar(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => {
        this.notificacao.sucesso('Reserva registrada.');
        this.recarga.update((t) => t + 1);
      }));
  }

  aprovar(r: Reserva): void {
    this.executar(this.api.aprovar(this.contexto.id()!, r.id, null), 'Reserva aprovada.');
  }

  reprovar(r: Reserva): void {
    pedirMotivo(this.dialog, 'Recusar reserva', `${r.nomeArea} · unidade ${r.rotuloUnidade}. O morador será notificado.`, 'Recusar')
      .subscribe((motivo) => this.executar(this.api.reprovar(this.contexto.id()!, r.id, motivo), 'Reserva recusada.'));
  }

  cancelar(r: Reserva): void {
    pedirMotivo(this.dialog, 'Cancelar reserva', 'A taxa de uso ainda não paga também será cancelada.', 'Cancelar reserva')
      .subscribe((motivo) => this.executar(this.api.cancelar(this.contexto.id()!, r.id, motivo), 'Reserva cancelada.'));
  }

  private executar(chamada: ReturnType<ReservaApi['aprovar']>, mensagem: string): void {
    chamada.subscribe({
      next: () => {
        this.notificacao.sucesso(mensagem);
        this.recarga.update((t) => t + 1);
      },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }
}

function somarDias(iso: string, dias: number): string {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
}

import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { pedirMotivo } from '../../comum/ui/confirmacao-dialog';
import { hojeIso } from '../../comum/ui/datas';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { AreaComum, HorarioOcupado, Reserva, ReservaApi } from './reserva-api';

/** Reservas no portal do morador: consulta de horários ocupados, pedido de reserva e cancelamento. */
@Component({
  selector: 'tc-portal-reservas',
  imports: [CurrencyPipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, EstadoVazio, DataIsoPipe, RotuloPipe],
  template: `
    <section class="tc-card mt">
      <div class="bar">
        <mat-form-field class="grow"><mat-label>Área comum</mat-label>
          <mat-select [formControl]="area">
            @for (a of areas() ?? []; track a.id) { <mat-option [value]="a.id">{{ a.nome }}</mat-option> }
          </mat-select></mat-form-field>
        @if (areaSelecionada()) { <button mat-flat-button class="primary" (click)="reservar(areaSelecionada()!)"><mat-icon>event_available</mat-icon> Reservar</button> }
      </div>
      @if (areaSelecionada(); as a) {
        <p class="small info">Funciona das {{ a.horaAbertura.slice(0, 5) }} às {{ a.horaFechamento.slice(0, 5) }} · até {{ a.duracaoMaximaMinutos / 60 }} h por reserva
          · antecedência mínima de {{ a.antecedenciaMinimaHoras }} h{{ a.taxa > 0 ? ' · taxa de uso ' : '' }}{{ a.taxa > 0 ? (a.taxa | currency: 'BRL') : '' }}
          {{ a.exigeAprovacao ? ' · sujeita à aprovação da administração' : '' }}</p>
        @if (a.regras) { <p class="rules small">{{ a.regras }}</p> }
        <h4 class="tc-section-label">Horários já reservados (próximos 60 dias)</h4>
        <ul class="slots">
          @for (s of horarios() ?? []; track s.data + s.horaInicio) {
            <li><mat-icon aria-hidden="true">event_busy</mat-icon><span>{{ s.data | dataIso }} · {{ s.horaInicio.slice(0, 5) }}–{{ s.horaFim.slice(0, 5) }}{{ s.situacao === 'PENDING' ? ' (aguardando aprovação)' : '' }}</span></li>
          } @empty { <li class="tc-muted none">Nenhum horário reservado.</li> }
        </ul>
      } @else if ((areas() ?? []).length === 0) {
        <tc-estado-vazio icone="deck" titulo="Nenhuma área disponível para reserva" />
      }
    </section>

    <section class="tc-card mt">
      <h3 class="tc-section-title first">Minhas reservas</h3>
      @for (r of minhas() ?? []; track r.id) {
        <div class="row">
          <div class="who"><strong>{{ r.nomeArea }}</strong> · unid. {{ r.rotuloUnidade }}<br />
            <span class="tc-muted small">{{ r.data | dataIso }} · {{ r.horaInicio.slice(0, 5) }}–{{ r.horaFim.slice(0, 5) }}
              {{ r.taxa > 0 ? ' · taxa ' : '' }}{{ r.taxa > 0 ? (r.taxa | currency: 'BRL') : '' }}</span>
            @if (r.observacaoDecisao || r.motivoCancelamento) { <br /><span class="small note">{{ r.motivoCancelamento ?? r.observacaoDecisao }}</span> }</div>
          <div class="right">
            <span class="tc-status" [class.is-success]="r.situacao === 'CONFIRMED'" [class.is-pending]="r.situacao === 'PENDING'"
              [class.is-danger]="r.situacao === 'REJECTED'" [class.is-inactive]="r.situacao === 'CANCELLED'">{{ r.situacao | rotulo: 'reservationStatus' }}</span>
            @if ((r.situacao === 'PENDING' || r.situacao === 'CONFIRMED') && r.data >= hoje) {
              <button mat-stroked-button (click)="cancelar(r)">Cancelar</button>
            }
          </div>
        </div>
      } @empty { <p class="tc-muted">Você ainda não fez reservas.</p> }
    </section>
  `,
  styles: `
    .mt { display: block; margin-top: 16px; }
    .bar { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .grow { flex: 1 1 240px; min-width: 0; }
    .bar .primary { margin-bottom: 20px; }
    .small { font: var(--mat-sys-body-small); }
    .info { margin: 0 0 12px; color: var(--tc-text-muted); }
    .rules { white-space: pre-line; margin: 0 0 16px; padding: 12px 14px; border-radius: var(--tc-radius-sm); background: var(--tc-surface-muted); border: 1px solid var(--tc-border); }
    h4.tc-section-label { margin: 8px 0 6px; }
    .slots { list-style: none; margin: 0; padding: 0; }
    .slots li { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--tc-border); font-variant-numeric: tabular-nums; }
    .slots li:last-child { border-bottom: 0; }
    .slots li mat-icon { color: var(--tc-text-muted); font-size: 20px; width: 20px; height: 20px; flex: 0 0 auto; }
    .slots li.none { padding: 6px 0; }
    .tc-section-title.first { margin-top: 0; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; }
    .who { flex: 1 1 220px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .note { display: inline-block; margin-top: 4px; }
    .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    @media (max-width: 600px) {
      .bar .primary { flex: 1 1 100%; margin-bottom: 8px; }
      .right { flex: 1 1 100%; justify-content: space-between; }
    }
  `,
})
export class PortalReservas {
  readonly idCondominio = input.required<string>();
  readonly unidades = input.required<{ idUnidade: string; rotulo: string }[]>();
  private readonly api = inject(ReservaApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);
  protected readonly hoje = hojeIso();
  protected readonly area = new FormControl<string | null>(null);
  private readonly valorArea = toSignal(this.area.valueChanges, { initialValue: null });

  protected readonly areas = toSignal(toObservable(this.idCondominio).pipe(
    switchMap((c) => this.api.portalAreas(c).pipe(catchError(() => of([] as AreaComum[]))))));
  protected readonly areaSelecionada = computed(() => (this.areas() ?? []).find((a) => a.id === this.valorArea()) ?? null);
  protected readonly horarios = toSignal(combineLatest([toObservable(this.valorArea), toObservable(this.recarga)]).pipe(
    filter(([a]) => !!a),
    switchMap(([a]) => this.api.portalOcupacao(this.idCondominio(), a!, this.hoje, somarDias(this.hoje, 60)).pipe(catchError(() => of([] as HorarioOcupado[]))))));
  protected readonly minhas = toSignal(combineLatest([toObservable(this.idCondominio), toObservable(this.recarga)]).pipe(
    switchMap(([c]) => this.api.portalMinhas(c).pipe(catchError(() => of([] as Reserva[]))))));

  reservar(a: AreaComum): void {
    abrirFormulario(this.dialog, {
      titulo: 'Reservar ' + a.nome,
      introducao: a.exigeAprovacao ? 'A reserva ficará aguardando a aprovação da administração.' : undefined,
      campos: [
        { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', obrigatorio: true, valor: this.unidades()[0]?.idUnidade,
          opcoes: this.unidades().map((u) => ({ valor: u.idUnidade, rotulo: u.rotulo })) },
        { chave: 'data', rotulo: 'Data', tipo: 'date', obrigatorio: true, colunas: 4 },
        { chave: 'horaInicio', rotulo: 'Início', tipo: 'time', obrigatorio: true, colunas: 4 },
        { chave: 'horaFim', rotulo: 'Fim', tipo: 'time', obrigatorio: true, colunas: 4 },
        { chave: 'convidados', rotulo: 'Convidados', tipo: 'number', colunas: 4 },
        { chave: 'observacoes', rotulo: 'Observações', tamanhoMaximo: 500, colunas: 8 },
      ],
      rotuloEnviar: 'Reservar',
      enviar: (v) => this.api.portalReservar(this.idCondominio(), { ...v, idArea: a.id }),
    }).pipe(filter((r): r is Reserva => !!r)).subscribe((r) => {
      this.notificacao.sucesso(r.situacao === 'PENDING' ? 'Pedido enviado para aprovação.' : 'Reserva confirmada.');
      this.recarga.update((t) => t + 1);
    });
  }

  cancelar(r: Reserva): void {
    pedirMotivo(this.dialog, 'Cancelar reserva', `${r.nomeArea} em ${r.data.split('-').reverse().join('/')}.`, 'Cancelar reserva')
      .pipe(switchMap((motivo) => this.api.portalCancelar(this.idCondominio(), r.id, motivo)))
      .subscribe({
        next: () => {
          this.notificacao.sucesso('Reserva cancelada.');
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

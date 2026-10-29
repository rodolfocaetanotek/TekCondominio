import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { hojeIso } from '../../comum/ui/datas';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { AutorizacaoVisita, PortariaApi } from './portaria-api';

/** Autorizações de visitantes/prestadores/entregas no portal do morador. */
@Component({
  selector: 'tc-portal-visitantes',
  imports: [MatButtonModule, MatIconModule, DataIsoPipe, RotuloPipe],
  template: `
    <section class="tc-card mt">
      <div class="head">
        <p class="tc-muted small">Autorize a entrada com antecedência. Informe o <strong>código</strong> ao visitante: a portaria libera a entrada com ele
          e você recebe um aviso quando ele chegar.</p>
        <button mat-flat-button class="primary" (click)="autorizar()"><mat-icon>person_add</mat-icon> Autorizar entrada</button>
      </div>
      @for (v of lista() ?? []; track v.id) {
        <div class="row">
          <div class="who"><strong>{{ v.nome }}</strong> · {{ v.tipo | rotulo: 'visitorKind' }} · unid. {{ v.rotuloUnidade }}<br />
            <span class="tc-muted small">{{ v.validaDe | dataIso }}{{ v.validaAte !== v.validaDe ? ' a ' + (v.validaAte | dataIso) : '' }}</span>
            <span class="tc-status" [class.is-pending]="v.situacao === 'EXPECTED'" [class.is-success]="v.situacao === 'INSIDE'"
              [class.is-inactive]="v.situacao === 'LEFT' || v.situacao === 'CANCELLED'">{{ v.situacao | rotulo: 'visitorStatus' }}</span></div>
          <div class="right">
            @if (v.codigoAcesso) { <span class="code" aria-label="Código de acesso">{{ v.codigoAcesso }}</span> }
            @if (v.situacao === 'EXPECTED') { <button mat-stroked-button (click)="cancelar(v)">Cancelar</button> }
          </div>
        </div>
      } @empty { <p class="tc-muted">Nenhuma autorização.</p> }
    </section>
  `,
  styles: `
    .mt { display: block; margin-top: 16px; } .small { font: var(--mat-sys-body-small); }
    .head { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 4px; border-bottom: 1px solid var(--tc-border); }
    .head p { margin: 0; flex: 1 1 280px; max-width: 60ch; line-height: 1.5; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 10px 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); flex-wrap: wrap; }
    .row:last-of-type { border-bottom: 0; }
    .who { flex: 1 1 220px; min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .who .tc-status { margin-left: 8px; vertical-align: middle; }
    .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .code { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; font-size: 22px; font-weight: 650; letter-spacing: .2em; font-variant-numeric: tabular-nums;
      padding: 4px 6px 4px 12px; border-radius: var(--tc-radius-sm); color: var(--mat-sys-primary);
      background: color-mix(in srgb, var(--mat-sys-primary) 8%, var(--tc-surface)); border: 1px dashed color-mix(in srgb, var(--mat-sys-primary) 40%, var(--tc-border)); }
    @media (max-width: 600px) {
      .head .primary { flex: 1 1 100%; }
      .right { flex: 1 1 100%; justify-content: space-between; }
    }
  `,
})
export class PortalVisitantes {
  readonly idCondominio = input.required<string>();
  readonly unidades = input.required<{ idUnidade: string; rotulo: string }[]>();
  private readonly api = inject(PortariaApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  private readonly recarga = signal(0);

  protected readonly lista = toSignal(combineLatest([toObservable(this.idCondominio), toObservable(this.recarga)]).pipe(
    switchMap(([c]) => this.api.portalListar(c).pipe(catchError(() => of([] as AutorizacaoVisita[]))))));

  autorizar(): void {
    abrirFormulario(this.dialog, {
      titulo: 'Autorizar entrada',
      campos: [
        { chave: 'idUnidade', rotulo: 'Unidade', tipo: 'select', obrigatorio: true, valor: this.unidades()[0]?.idUnidade, colunas: 6,
          opcoes: this.unidades().map((u) => ({ valor: u.idUnidade, rotulo: u.rotulo })) },
        { chave: 'tipo', rotulo: 'Tipo', tipo: 'select', obrigatorio: true, valor: 'VISITOR', colunas: 6, opcoes: opcoes('visitorKind') },
        { chave: 'nome', rotulo: 'Nome do visitante', obrigatorio: true, tamanhoMaximo: 150 },
        { chave: 'validaDe', rotulo: 'De', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 6 },
        { chave: 'validaAte', rotulo: 'Até', tipo: 'date', obrigatorio: true, valor: hojeIso(), colunas: 6 },
        { chave: 'empresa', rotulo: 'Empresa (prestador/entrega)', tamanhoMaximo: 120 },
        { chave: 'observacoes', rotulo: 'Observações para a portaria', tamanhoMaximo: 500 },
      ],
      rotuloEnviar: 'Gerar código',
      enviar: (v) => this.api.portalAutorizar(this.idCondominio(), v),
    }).pipe(filter((r): r is AutorizacaoVisita => !!r)).subscribe((r) => {
      this.notificacao.sucesso(`Autorização criada. Código: ${r.codigoAcesso}`);
      this.recarga.update((t) => t + 1);
    });
  }

  cancelar(v: AutorizacaoVisita): void {
    this.api.portalCancelar(this.idCondominio(), v.id).subscribe({
      next: () => this.recarga.update((t) => t + 1),
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }
}

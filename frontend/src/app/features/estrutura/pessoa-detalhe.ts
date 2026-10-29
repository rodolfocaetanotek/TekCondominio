import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { ConfirmacaoDialog, DadosConfirmacaoDialog } from '../../comum/ui/confirmacao-dialog';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { PessoaDialog } from './pessoa-dialog';
import { ROTULOS_TIPO_VINCULO, PessoaDetalhe, Vinculo } from './estrutura.models';
import { EstruturaService } from './estrutura.service';

@Component({
  selector: 'tc-pessoa-detalhe',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio, DocumentoPipe, DataIsoPipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/pessoas" class="back"><mat-icon>arrow_back</mat-icon> Pessoas</a>
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="error" titulo="Não foi possível abrir" [descricao]="mensagem" /></section>
      } @else if (pessoa(); as p) {
        <tc-cabecalho-pagina [sobretitulo]="p.tipo === 'COMPANY' ? 'Pessoa jurídica' : 'Pessoa física'" [titulo]="p.nome"
          [subtitulo]="p.documento ? (p.documento | documento: 'cpfCnpj') : 'Sem documento informado'">
          <span class="tc-status big" [class.is-active]="p.ativo" [class.is-inactive]="!p.ativo">{{ p.ativo ? 'Ativa' : 'Inativa' }}</span>
          @if (podeEditar()) {
            <button mat-stroked-button (click)="editar(p)"><mat-icon>edit</mat-icon> Editar</button>
          }
        </tc-cabecalho-pagina>

        <div class="layout">
          <section class="tc-table-card">
            <h2 class="tc-section-title tc-card-head is-divided">Vínculos com unidades</h2>
            @for (o of p.vinculos; track o.id) {
              <div class="occ" [class.ended]="!o.vigente">
                <div class="who">
                  <strong>{{ o.rotuloUnidade }}</strong>
                  <span class="tc-muted">{{ rotuloPapel(o) }} · {{ o.dataInicio | dataIso }}{{ o.vigente ? ' até hoje' : ' a ' + (o.dataFim | dataIso) }}</span>
                </div>
                <div class="occ-end">
                  @if (o.responsavelFinanceiro) {
                    <span class="tc-chip-soft">Resp. financeiro</span>
                  }
                  @if (o.idCondominio === contexto.id()) {
                    <a mat-button [routerLink]="['/unidades', o.idUnidade]">Ver unidade</a>
                  }
                </div>
              </div>
            } @empty {
              <tc-estado-vazio icone="link_off" titulo="Sem vínculos" descricao="Vincule a pessoa a uma unidade pela ficha da unidade." />
            }
          </section>

          <aside class="side">
            <section class="tc-card">
              <h2 class="tc-section-title">Contato</h2>
              <dl class="tc-dl">
                <div><dt>E-mail</dt><dd>{{ p.email ?? '—' }}</dd></div>
                <div><dt>Telefone</dt><dd>{{ p.telefone ? (p.telefone | documento: 'telefone') : '—' }}</dd></div>
                <div><dt>Alternativo</dt><dd>{{ p.telefoneSecundario ? (p.telefoneSecundario | documento: 'telefone') : '—' }}</dd></div>
              </dl>
              @if (p.observacoes) {
                <p class="tc-muted small">{{ p.observacoes }}</p>
              }
            </section>
            <section class="tc-card">
              <h2 class="tc-section-title">Portal do morador</h2>
              @if (p.usuarioPortal) {
                <p class="small"><mat-icon inline>verified</mat-icon> Possui acesso ao portal.</p>
              } @else {
                <p class="tc-muted small">Permite consultar cobranças, comunicados e documentos da(s) sua(s) unidade(s).</p>
              }
              @if (auth.pode('USER_MANAGE') && p.ativo) {
                <button mat-stroked-button (click)="concederPortal(p)" [disabled]="ocupado()">
                  <mat-icon>forward_to_inbox</mat-icon>{{ p.usuarioPortal ? 'Atualizar acesso / reenviar convite' : 'Conceder acesso ao portal' }}
                </button>
              }
              @if (podeEditar()) {
                <button mat-button class="toggle" (click)="alternar(p)" [disabled]="ocupado()">{{ p.ativo ? 'Inativar cadastro' : 'Reativar cadastro' }}</button>
              }
            </section>
          </aside>
        </div>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1080px; }
    .back { margin: -8px 0 8px -12px; }
    .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .side { display: grid; gap: 16px; }
    .occ { display: flex; align-items: center; gap: 8px 12px; padding: 12px 20px; flex-wrap: wrap; }
    .occ + .occ { border-top: 1px solid var(--tc-border); }
    .occ.ended { opacity: .65; }
    .who { flex: 1 1 200px; min-width: 0; display: grid; gap: 2px; }
    .occ-end { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-left: auto; }
    .small { font: var(--mat-sys-body-small); }
    .small mat-icon { color: var(--tc-success); vertical-align: middle; }
    .toggle { margin-top: 8px; display: block; }
    @media (max-width: 600px) { .occ { padding-inline: 16px; } }
    .tc-section-title { margin-top: 0; }
  `,
})
export class PessoaDetalhePage {
  protected readonly auth = inject(AuthService);
  protected readonly contexto = inject(CondominioContexto);
  private readonly service = inject(EstruturaService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);

  readonly id = input.required<string>();
  private readonly recarga = signal(0);
  protected readonly ocupado = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly podeEditar = computed(() => this.contexto.podeAlgum('PEOPLE_EDIT'));

  protected readonly pessoa = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.recarga)]).pipe(
      switchMap(([id]) =>
        this.service.pessoa(id).pipe(
          catchError((err: unknown) => {
            this.erro.set(paraProblema(err).detail);
            return of(null);
          }),
        ),
      ),
    ),
    { initialValue: null },
  );

  rotuloPapel(o: Vinculo): string {
    return ROTULOS_TIPO_VINCULO[o.tipo];
  }

  editar(p: PessoaDetalhe): void {
    this.dialog
      .open(PessoaDialog, { data: { pessoa: p }, width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((r) => !!r))
      .subscribe(() => this.recarregar('Cadastro atualizado.'));
  }

  alternar(p: PessoaDetalhe): void {
    this.executar(this.service.alterarPessoaAtiva(p.id, !p.ativo), p.ativo ? 'Cadastro inativado.' : 'Cadastro reativado.');
  }

  concederPortal(p: PessoaDetalhe): void {
    this.dialog
      .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, {
        data: { titulo: 'Acesso ao portal', mensagem: `Enviar convite de acesso ao portal para ${p.email ?? 'o e-mail cadastrado'}?`, rotuloConfirmar: 'Enviar convite' },
        width: '440px',
      })
      .afterClosed()
      .pipe(filter((r) => r === true))
      .subscribe(() => this.executar(this.service.concederAcessoPortal(p.id), 'Acesso concedido. Convite enviado por e-mail.'));
  }

  private executar(chamada$: ReturnType<EstruturaService['pessoa']>, mensagem: string): void {
    this.ocupado.set(true);
    chamada$.subscribe({
      next: () => {
        this.ocupado.set(false);
        this.recarregar(mensagem);
      },
      error: (err: unknown) => {
        this.ocupado.set(false);
        this.notificacao.erro(paraProblema(err).detail);
      },
    });
  }

  private recarregar(mensagem: string): void {
    this.notificacao.sucesso(mensagem);
    this.recarga.update((t) => t + 1);
  }
}

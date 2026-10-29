import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { NotificationService } from '../../core/notificacao/notificacao.service';
import { DocPipe } from '../../comum/formato/documento.pipe';
import { ConfirmDialog, ConfirmDialogData } from '../../comum/ui/confirmacao-dialog';
import { EmptyState } from '../../comum/ui/estado-vazio';
import { IsoDatePipe } from '../../comum/ui/data-iso.pipe';
import { PageHeader } from '../../comum/ui/cabecalho-pagina';
import { PersonDialog } from './pessoa-dialog';
import { OCCUPANCY_ROLE_LABELS, PessoaDetalhe as Person, Vinculo } from './estrutura.models';
import { StructureService } from './estrutura.service';

@Component({
  selector: 'tc-person-detail',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, PageHeader, EmptyState, DocPipe, IsoDatePipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/pessoas" class="back"><mat-icon>arrow_back</mat-icon> Pessoas</a>
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir" [description]="message" /></section>
      } @else if (person(); as p) {
        <tc-page-header [eyebrow]="p.tipo === 'COMPANY' ? 'Pessoa jurídica' : 'Pessoa física'" [title]="p.nome"
          [subtitle]="p.documento ? (p.documento | doc: 'cpfCnpj') : 'Sem documento informado'">
          <span class="tc-status big" [class.is-active]="p.ativo" [class.is-inactive]="!p.ativo">{{ p.ativo ? 'Ativa' : 'Inativa' }}</span>
          @if (canEdit()) {
            <button mat-stroked-button (click)="edit(p)"><mat-icon>edit</mat-icon> Editar</button>
          }
        </tc-page-header>

        <div class="layout">
          <section class="tc-table-card">
            <h2 class="tc-section-title tc-card-head is-divided">Vínculos com unidades</h2>
            @for (o of p.vinculos; track o.id) {
              <div class="occ" [class.ended]="!o.vigente">
                <div class="who">
                  <strong>{{ o.rotuloUnidade }}</strong>
                  <span class="tc-muted">{{ roleLabel(o) }} · {{ o.dataInicio | isoDate }}{{ o.vigente ? ' até hoje' : ' a ' + (o.dataFim | isoDate) }}</span>
                </div>
                <div class="occ-end">
                  @if (o.responsavelFinanceiro) {
                    <span class="tc-chip-soft">Resp. financeiro</span>
                  }
                  @if (o.idCondominio === context.id()) {
                    <a mat-button [routerLink]="['/unidades', o.idUnidade]">Ver unidade</a>
                  }
                </div>
              </div>
            } @empty {
              <tc-empty-state icon="link_off" title="Sem vínculos" description="Vincule a pessoa a uma unidade pela ficha da unidade." />
            }
          </section>

          <aside class="side">
            <section class="tc-card">
              <h2 class="tc-section-title">Contato</h2>
              <dl class="tc-dl">
                <div><dt>E-mail</dt><dd>{{ p.email ?? '—' }}</dd></div>
                <div><dt>Telefone</dt><dd>{{ p.telefone ? (p.telefone | doc: 'phone') : '—' }}</dd></div>
                <div><dt>Alternativo</dt><dd>{{ p.telefoneSecundario ? (p.telefoneSecundario | doc: 'phone') : '—' }}</dd></div>
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
              @if (auth.can('USER_MANAGE') && p.ativo) {
                <button mat-stroked-button (click)="grantPortal(p)" [disabled]="busy()">
                  <mat-icon>forward_to_inbox</mat-icon>{{ p.usuarioPortal ? 'Atualizar acesso / reenviar convite' : 'Conceder acesso ao portal' }}
                </button>
              }
              @if (canEdit()) {
                <button mat-button class="toggle" (click)="toggle(p)" [disabled]="busy()">{{ p.ativo ? 'Inativar cadastro' : 'Reativar cadastro' }}</button>
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
export class PersonDetail {
  protected readonly auth = inject(AuthService);
  protected readonly context = inject(CondominiumContext);
  private readonly service = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  readonly id = input.required<string>();
  private readonly tick = signal(0);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canEdit = computed(() => this.context.canAny('PEOPLE_EDIT'));

  protected readonly person = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.tick)]).pipe(
      switchMap(([id]) =>
        this.service.person(id).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of(null);
          }),
        ),
      ),
    ),
    { initialValue: null },
  );

  roleLabel(o: Vinculo): string {
    return OCCUPANCY_ROLE_LABELS[o.tipo];
  }

  edit(p: Person): void {
    this.dialog
      .open(PersonDialog, { data: { person: p }, width: '680px', maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((r) => !!r))
      .subscribe(() => this.reload('Cadastro atualizado.'));
  }

  toggle(p: Person): void {
    this.run(this.service.setPersonActive(p.id, !p.ativo), p.ativo ? 'Cadastro inativado.' : 'Cadastro reativado.');
  }

  grantPortal(p: Person): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, {
        data: { title: 'Acesso ao portal', message: `Enviar convite de acesso ao portal para ${p.email ?? 'o e-mail cadastrado'}?`, confirmLabel: 'Enviar convite' },
        width: '440px',
      })
      .afterClosed()
      .pipe(filter((r) => r === true))
      .subscribe(() => this.run(this.service.grantPortalAccess(p.id), 'Acesso concedido. Convite enviado por e-mail.'));
  }

  private run(call$: ReturnType<StructureService['person']>, message: string): void {
    this.busy.set(true);
    call$.subscribe({
      next: () => {
        this.busy.set(false);
        this.reload(message);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.notify.error(toProblem(err).detail);
      },
    });
  }

  private reload(message: string): void {
    this.notify.success(message);
    this.tick.update((t) => t + 1);
  }
}

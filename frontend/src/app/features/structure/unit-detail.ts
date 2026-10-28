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
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { EndOccupancyDialog, OccupancyDialog } from './occupancy-dialogs';
import { OCCUPANCY_ROLE_LABELS, Unidade, UNIT_TYPE_LABELS, Vinculo } from './structure.models';
import { StructureService } from './structure.service';
import { UnitDialog, UnitDialogData } from './unit-dialog';

@Component({
  selector: 'tc-unit-detail',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule, PageHeader,
    EmptyState, CondominiumRequired, IsoDatePipe],
  template: `
    <div class="tc-page narrow">
      <a mat-button routerLink="/unidades" class="back"><mat-icon>arrow_back</mat-icon> Unidades</a>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="error" title="Não foi possível abrir a unidade" [description]="message" /></section>
      } @else if (state(); as s) {
        <tc-page-header eyebrow="Unidade" [title]="s.unit.rotulo" [subtitle]="typeLabel(s.unit) + (s.unit.andar ? ' · ' + s.unit.andar + 'º andar' : '')">
          <span class="tc-status big" [class.is-active]="s.unit.ativo" [class.is-inactive]="!s.unit.ativo">{{ s.unit.ativo ? 'Ativa' : 'Inativa' }}</span>
          @if (canEditStructure()) {
            <button mat-stroked-button (click)="edit(s.unit)"><mat-icon>edit</mat-icon> Editar</button>
            <button mat-button (click)="toggle(s.unit)">{{ s.unit.ativo ? 'Inativar' : 'Reativar' }}</button>
          }
        </tc-page-header>

        <section class="facts tc-dl cols tc-card">
          <div><span class="k">Fração ideal</span><strong class="mono">{{ s.unit.fracaoIdeal | number: '1.2-6' }}%</strong></div>
          <div><span class="k">Área</span><strong>{{ s.unit.area ? (s.unit.area | number: '1.2-2') + ' m²' : '—' }}</strong></div>
          <div><span class="k">Proprietário(s)</span><strong>{{ s.unit.nomeProprietario ?? '—' }}</strong></div>
          <div><span class="k">Responsável financeiro</span><strong>{{ s.unit.nomeResponsavelFinanceiro ?? 'não definido' }}</strong></div>
          @if (s.unit.observacoes) {
            <div class="wide"><span class="k">Observações</span><span>{{ s.unit.observacoes }}</span></div>
          }
        </section>

        <section class="tc-table-card">
          <div class="tc-card-head is-divided">
            <h2 class="tc-section-title">Pessoas vinculadas e histórico de ocupação</h2>
            @if (canEditPeople() && s.unit.ativo) {
              <button mat-flat-button (click)="link(s.unit, s.occupancies)"><mat-icon>person_add</mat-icon> Vincular pessoa</button>
            }
          </div>
          @for (o of s.occupancies; track o.id) {
            <div class="occ" [class.ended]="!o.vigente">
              <mat-icon class="tc-ico" aria-hidden="true">{{ o.tipo === 'OWNER' ? 'key' : o.tipo === 'TENANT' ? 'real_estate_agent' : 'person' }}</mat-icon>
              <div class="who">
                <a [routerLink]="['/pessoas', o.idPessoa]"><strong>{{ o.nomePessoa }}</strong></a>
                <span class="tc-muted">{{ roleLabel(o) }} · desde {{ o.dataInicio | isoDate }}
                  @if (!o.vigente) { até {{ o.dataFim | isoDate }}{{ o.motivoEncerramento ? ' — ' + o.motivoEncerramento : '' }} }
                </span>
              </div>
              <div class="occ-end">
              @if (o.responsavelFinanceiro) {
                <span class="tc-chip-soft"><mat-icon inline>payments</mat-icon> Resp. financeiro</span>
              }
              @if (!o.vigente) {
                <span class="tc-status is-inactive">Encerrado</span>
              } @else if (canEditPeople()) {
                @if (!o.responsavelFinanceiro) {
                  <button mat-icon-button (click)="makeFinancial(o)" matTooltip="Definir como responsável financeiro" aria-label="Definir como responsável financeiro">
                    <mat-icon>payments</mat-icon>
                  </button>
                }
                <button mat-icon-button (click)="end(o)" matTooltip="Encerrar vínculo" aria-label="Encerrar vínculo"><mat-icon>event_busy</mat-icon></button>
              }
              </div>
            </div>
          } @empty {
            <tc-empty-state icon="group_off" title="Nenhuma pessoa vinculada" description="Vincule proprietário, inquilino ou moradores." />
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
export class UnitDetail {
  protected readonly context = inject(CondominiumContext);
  private readonly service = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);

  readonly id = input.required<string>();
  private readonly tick = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly canEditStructure = computed(() => this.context.can('STRUCTURE_EDIT'));
  protected readonly canEditPeople = computed(() => this.context.can('PEOPLE_EDIT') && this.context.selected()?.situacao === 'ACTIVE');

  protected readonly state = toSignal(
    combineLatest([toObservable(this.context.id), toObservable(this.id), toObservable(this.tick)]).pipe(
      filter(([c]) => !!c),
      switchMap(([c, id]) =>
        forkJoin({
          unit: this.service.unit(c!, id),
          occupancies: this.context.can('PEOPLE_VIEW') ? this.service.occupancies(c!, id) : of([] as Vinculo[]),
        }).pipe(
          catchError((err: unknown) => {
            this.error.set(toProblem(err).detail);
            return of(null);
          }),
        ),
      ),
    ),
    { initialValue: null },
  );

  typeLabel(u: Unidade): string {
    return UNIT_TYPE_LABELS[u.tipo];
  }

  roleLabel(o: Vinculo): string {
    return OCCUPANCY_ROLE_LABELS[o.tipo];
  }

  edit(unit: Unidade): void {
    const condominiumId = this.context.id()!;
    this.service.buildings(condominiumId).subscribe((buildings) =>
      this.dialog
        .open<UnitDialog, UnitDialogData, Unidade>(UnitDialog, { data: { condominiumId, buildings, unit }, width: '640px', maxWidth: '95vw' })
        .afterClosed()
        .pipe(filter((u) => !!u))
        .subscribe(() => this.reload('Unidade atualizada.')),
    );
  }

  toggle(unit: Unidade): void {
    this.service.setUnitActive(this.context.id()!, unit.id, !unit.ativo).subscribe({
      next: () => this.reload(unit.ativo ? 'Unidade inativada.' : 'Unidade reativada.'),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  link(unit: Unidade, occupancies: Vinculo[]): void {
    this.dialog
      .open(OccupancyDialog, {
        data: { condominiumId: this.context.id()!, unitId: unit.id, unitLabel: unit.rotulo, hasFinancial: occupancies.some((o) => o.vigente && o.responsavelFinanceiro) },
        width: '560px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((o) => !!o))
      .subscribe(() => this.reload('Pessoa vinculada.'));
  }

  end(o: Vinculo): void {
    this.dialog
      .open(EndOccupancyDialog, { data: { condominiumId: this.context.id()!, occupancy: o }, width: '440px' })
      .afterClosed()
      .pipe(filter((r) => !!r))
      .subscribe(() => this.reload('Vínculo encerrado.'));
  }

  makeFinancial(o: Vinculo): void {
    this.service.makeFinancialResponsible(this.context.id()!, o.id).subscribe({
      next: () => this.reload(`${o.nomePessoa} agora é o responsável financeiro.`),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  private reload(message: string): void {
    this.notify.success(message);
    this.tick.update((t) => t + 1);
  }
}

import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { StructureService } from '../structure/structure.service';
import { VisitorApi, VisitorPass } from './visitor-api';

/** Painel da portaria: entrada por código, chegadas sem aviso, quem está no condomínio e saídas. */
@Component({
  selector: 'tc-gate-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, PageHeader,
    CondominiumRequired, EmptyState, IsoDatePipe, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Administração" title="Portaria" [subtitle]="context.selected()?.name">
        @if (canEdit()) { <button mat-flat-button (click)="arrival()"><mat-icon>person_add</mat-icon> Registrar chegada</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (canEdit()) {
          <section class="tc-card code">
            <mat-form-field><mat-label>Código de acesso (6 dígitos)</mat-label>
              <input matInput [formControl]="code" inputmode="numeric" maxlength="6" (keydown.enter)="checkInByCode()" /></mat-form-field>
            <button mat-flat-button (click)="checkInByCode()" [disabled]="code.invalid"><mat-icon>login</mat-icon> Liberar entrada</button>
          </section>
        }
        @for (group of groups(); track group.title) {
          <section class="tc-card">
            <h3 class="tc-section-title">{{ group.title }} ({{ group.items.length }})</h3>
            @for (v of group.items; track v.id) {
              <div class="row">
                <div><strong>{{ v.name }}</strong> · {{ v.kind | label: 'visitorKind' }} · unid. {{ v.unitLabel }}<br />
                  <span class="tc-muted small">{{ v.company ?? '' }}{{ v.document ? ' · doc. ' + v.document : '' }}{{ v.vehiclePlate ? ' · placa ' + v.vehiclePlate : '' }}
                    · autorizado por {{ v.authorizedByName }}{{ v.validFrom !== v.validUntil ? ' · válido até ' + (v.validUntil | isoDate) : '' }}
                    {{ v.enteredAt ? ' · entrada ' + (v.enteredAt | date: 'HH:mm') : '' }}{{ v.leftAt ? ' · saída ' + (v.leftAt | date: 'HH:mm') : '' }}</span></div>
                @if (canEdit()) {
                  @if (v.status === 'EXPECTED') { <button mat-stroked-button (click)="act(v, 'in')"><mat-icon>login</mat-icon> Entrada</button> }
                  @if (v.status === 'INSIDE') { <button mat-stroked-button (click)="act(v, 'out')"><mat-icon>logout</mat-icon> Saída</button> }
                }
              </div>
            } @empty { <p class="tc-muted">Nenhum registro.</p> }
          </section>
        }
        @if ((list() ?? []).length === 0) { <tc-empty-state icon="badge" title="Sem movimentação hoje" /> }
      }
    </div>
  `,
  styles: `
    :host .tc-page > section { margin-bottom: 16px; }
    .code { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); flex-wrap: wrap; }
    .small { font: var(--mat-sys-body-small); }
  `,
})
export class GatePage {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(VisitorApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly code = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] });

  protected readonly canEdit = computed(() => this.context.can('ACCESS_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.today(c!).pipe(catchError(() => of([] as VisitorPass[]))))));
  protected readonly groups = computed(() => {
    const all = this.list() ?? [];
    return [
      { title: 'No condomínio agora', items: all.filter((v) => v.status === 'INSIDE') },
      { title: 'Esperados hoje', items: all.filter((v) => v.status === 'EXPECTED') },
      { title: 'Saíram hoje', items: all.filter((v) => v.status === 'LEFT') },
    ];
  });

  checkInByCode(): void {
    if (this.code.invalid) return;
    this.api.checkInByCode(this.context.id()!, this.code.value).subscribe({
      next: (v) => {
        this.notify.success(`Entrada liberada: ${v.name} (unid. ${v.unitLabel}).`);
        this.code.reset();
        this.tick.update((t) => t + 1);
      },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  act(v: VisitorPass, action: 'in' | 'out'): void {
    const c = this.context.id()!;
    (action === 'in' ? this.api.checkIn(c, v.id) : this.api.checkOut(c, v.id)).subscribe({
      next: () => this.tick.update((t) => t + 1),
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }

  arrival(): void {
    const c = this.context.id()!;
    this.structure.units(c, { page: 0, size: 500, active: 'true' }).pipe(map((p) => p.content)).subscribe((units) =>
      openForm(this.dialog, {
        title: 'Registrar chegada',
        intro: 'Colete apenas os dados necessários. O documento é exibido mascarado e os registros são anonimizados após o prazo de retenção.',
        fields: [
          { key: 'unitId', label: 'Unidade de destino', type: 'select', required: true, span: 6, options: units.map((u) => ({ value: u.id, label: u.label })) },
          { key: 'kind', label: 'Tipo', type: 'select', required: true, value: 'VISITOR', span: 6, options: options('visitorKind') },
          { key: 'name', label: 'Nome', required: true, maxLength: 150 },
          { key: 'document', label: 'Documento (opcional)', maxLength: 20, span: 6 },
          { key: 'vehiclePlate', label: 'Placa (opcional)', maxLength: 8, span: 6 },
          { key: 'company', label: 'Empresa (prestador/entrega)', maxLength: 120 },
        ],
        submitLabel: 'Registrar entrada',
        submit: (v) => this.api.arrival(c, v),
      }).pipe(filter((r) => !!r)).subscribe(() => this.tick.update((t) => t + 1)));
  }
}

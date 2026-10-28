import { PortalReservations } from '../reservations/portal-reservations';
import { PortalVisitors } from '../visitors/portal-visitors';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { Announcement, OccurrenceDetail } from '../ops/ops-api';
import { PortalApi } from './portal-api';

/** Portal do morador: somente dados das unidades do próprio morador (o backend aplica a restrição). */
@Component({
  selector: 'tc-portal-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatTabsModule, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatInputModule, MatExpansionModule, MatProgressBarModule, PageHeader, EmptyState, IsoDatePipe, LabelPipe, PortalReservations, PortalVisitors],
  template: `
    <div class="tc-page narrow">
      @if (error(); as message) {
        <section class="tc-card"><tc-empty-state icon="home" title="Portal indisponível" [description]="message" /></section>
      } @else if (me(); as m) {
        <tc-page-header eyebrow="Portal do morador" [title]="'Olá, ' + m.name.split(' ')[0]" [subtitle]="condo()?.name">
          @if (m.condominiums.length > 1) {
            <mat-form-field><mat-label>Condomínio</mat-label>
              <mat-select [value]="condoId()" (valueChange)="condoId.set($event)">
                @for (c of m.condominiums; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }</mat-select></mat-form-field>
          }
        </tc-page-header>
        <section class="units">
          @for (u of condo()?.units ?? []; track u.unitId) {
            <div class="tc-card unit"><mat-icon aria-hidden="true">home</mat-icon><div><strong>Unidade {{ u.label }}</strong>
              <span class="tc-muted">{{ u.role === 'OWNER' ? 'Proprietário(a)' : u.role === 'TENANT' ? 'Inquilino(a)' : 'Morador(a)' }}{{ u.financialResponsible ? ' · responsável financeiro' : '' }}</span></div></div>
          }
        </section>
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Agenda">
            <section class="tc-card mt">
              @for (e of calendar() ?? []; track $index) {
                <div class="ev"><span class="date">{{ e.date | isoDate }}</span><mat-icon aria-hidden="true">{{ e.type === 'ASSEMBLY' ? 'groups' : 'receipt' }}</mat-icon><span>{{ e.title }}</span></div>
              } @empty { <p class="tc-muted">Nenhum evento próximo.</p> }
            </section>
          </mat-tab>
          <mat-tab label="Cobranças">
            <ng-template matTabContent>
              @for (u of charges() ?? []; track u.unitId) {
                <section class="tc-card mt">
                  <h3 class="tc-section-title">Unidade {{ u.unitLabel }} · em aberto {{ u.openTotal | currency: 'BRL' }}</h3>
                  @for (l of u.lines; track l.entryId) {
                    <div class="charge">
                      <div><strong>{{ l.description }}</strong><br /><small class="tc-muted">vencimento {{ l.dueDate | isoDate }}{{ l.daysLate > 0 ? ' · ' + l.daysLate + ' dias em atraso' : '' }}</small></div>
                      <div class="right"><span class="money">{{ (l.remaining > 0 ? l.updatedTotal : l.amount) | currency: 'BRL' }}</span>
                        <span class="tc-status" [class.is-active]="l.status === 'PAID'" [class.is-pending]="l.remaining > 0">{{ l.status | label: 'entryStatus' }}</span></div>
                    </div>
                  }
                  <p class="tc-muted small">Valores em atraso incluem multa e juros calculados até hoje. Para 2ª via ou negociação, fale com a administradora.</p>
                </section>
              } @empty {
                <section class="tc-card mt"><tc-empty-state icon="receipt_long" title="Sem cobranças para exibir"
                  description="As cobranças aparecem para o proprietário ou responsável financeiro da unidade." /></section>
              }
            </ng-template>
          </mat-tab>
          <mat-tab label="Comunicados">
            <ng-template matTabContent>
              @for (a of announcements() ?? []; track a.id) {
                <article class="tc-card mt" [class.unread]="!a.read" (click)="read(a)">
                  <h3 class="tc-section-title">@if (a.pinned) { <mat-icon inline>push_pin</mat-icon> } {{ a.title }}</h3>
                  <p class="pre">{{ a.body }}</p><small class="tc-muted">{{ a.publishedAt | date: 'dd/MM/yyyy HH:mm' }}</small>
                </article>
              } @empty { <section class="tc-card mt"><tc-empty-state icon="campaign" title="Nenhum comunicado" /></section> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Documentos">
            <ng-template matTabContent>
              <section class="tc-card mt">
                @for (d of documents() ?? []; track d.id) {
                  <div class="doc"><mat-icon aria-hidden="true">description</mat-icon><span>{{ d.title }} <small class="tc-muted">({{ d.category | label: 'docCategory' }})</small></span>
                    <button mat-icon-button (click)="download(d.id)" [attr.aria-label]="'Baixar ' + d.title"><mat-icon>download</mat-icon></button></div>
                } @empty { <p class="tc-muted">Nenhum documento disponível.</p> }
              </section>
            </ng-template>
          </mat-tab>
          <mat-tab label="Ocorrências">
            <ng-template matTabContent>
              <div class="tc-actions mt"><button mat-flat-button (click)="newOccurrence()"><mat-icon>add</mat-icon> Abrir solicitação</button></div>
              <mat-accordion>
                @for (o of occurrences() ?? []; track o.id) {
                  <mat-expansion-panel (opened)="openOccurrence(o.id)">
                    <mat-expansion-panel-header><mat-panel-title>Nº {{ o.number }} · {{ o.title }}</mat-panel-title>
                      <mat-panel-description>{{ o.status | label: 'occurrenceStatus' }}</mat-panel-description></mat-expansion-panel-header>
                    @if (detail()?.summary?.id === o.id) {
                      @let d = detail()!;
                      <p class="pre">{{ d.description }}</p>
                      @if (d.resolution) { <p><strong>Solução:</strong> {{ d.resolution }}</p> }
                      @for (i of d.interactions; track i.id) { <p class="msg"><strong>{{ i.authorName }}</strong> <small class="tc-muted">{{ i.createdAt | date: 'dd/MM HH:mm' }}</small><br />{{ i.message }}</p> }
                      @if (o.status !== 'RESOLVED' && o.status !== 'CANCELLED') {
                        <mat-form-field class="full"><mat-label>Mensagem</mat-label><textarea matInput rows="2" [formControl]="message" maxlength="2000"></textarea></mat-form-field>
                        <button mat-stroked-button (click)="comment(o.id)" [disabled]="message.invalid">Enviar</button>
                      }
                    } @else { <mat-progress-bar mode="indeterminate" /> }
                  </mat-expansion-panel>
                } @empty { <section class="tc-card"><tc-empty-state icon="build" title="Nenhuma solicitação" /></section> }
              </mat-accordion>
            </ng-template>
          </mat-tab>
          <mat-tab label="Reservas">
            <ng-template matTabContent>
              @if (condo(); as c) { <tc-portal-reservations [condominiumId]="c.id" [units]="c.units" /> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Visitantes">
            <ng-template matTabContent>
              @if (condo(); as c) { <tc-portal-visitors [condominiumId]="c.id" [units]="c.units" /> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Assembleias">
            <ng-template matTabContent>
              <mat-accordion class="mt">
                @for (a of assemblies() ?? []; track a.id) {
                  <mat-expansion-panel>
                    <mat-expansion-panel-header><mat-panel-title>{{ a.title }}</mat-panel-title>
                      <mat-panel-description>{{ a.scheduledAt | date: 'dd/MM/yyyy HH:mm' }} · {{ a.status | label: 'assemblyStatus' }}</mat-panel-description></mat-expansion-panel-header>
                    <p>Local: {{ a.location }}</p>
                    @if (a.minutes) { <h4>Ata</h4><p class="pre">{{ a.minutes }}</p> } @else { <p class="tc-muted">A ata será publicada após o encerramento.</p> }
                  </mat-expansion-panel>
                } @empty { <section class="tc-card"><tc-empty-state icon="groups" title="Nenhuma assembleia" /></section> }
              </mat-accordion>
            </ng-template>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 900px; }
    .mt { margin-top: 16px; }
    .units { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
    .unit { display: flex; gap: 12px; align-items: center; padding: 12px 16px; } .unit div { display: grid; } .unit mat-icon { color: var(--mat-sys-primary); }
    .ev { display: flex; gap: 12px; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .date { width: 92px; font-variant-numeric: tabular-nums; font-weight: 600; }
    .charge { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .right { display: grid; justify-items: end; gap: 4px; }
    .small { font: var(--mat-sys-body-small); }
    .unread { border-left: 4px solid var(--mat-sys-primary); }
    .pre { white-space: pre-wrap; }
    .doc { display: flex; align-items: center; gap: 10px; padding: 4px 0; } .doc span { flex: 1; }
    .msg { border-left: 3px solid var(--mat-sys-primary); padding-left: 10px; white-space: pre-wrap; }
    .full { width: 100%; }
  `,
})
export class PortalPage {
  private readonly api = inject(PortalApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly error = signal<string | null>(null);
  protected readonly condoId = signal<string | null>(null);
  private readonly tick = signal(0);
  protected readonly detail = signal<OccurrenceDetail | null>(null);
  protected readonly message = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  protected readonly me = toSignal(this.api.me().pipe(catchError((err: unknown) => { this.error.set(toProblem(err).detail); return of(null); })));
  protected readonly condo = computed(() => {
    const m = this.me();
    if (!m) return null;
    return m.condominiums.find((c) => c.id === this.condoId()) ?? m.condominiums[0] ?? null;
  });
  private readonly source = combineLatest([toObservable(this.condo), toObservable(this.tick)]).pipe(filter(([c]) => !!c));
  protected readonly calendar = toSignal(this.source.pipe(switchMap(([c]) => this.api.calendar(c!.id).pipe(catchError(() => of([]))))));
  protected readonly charges = toSignal(this.source.pipe(switchMap(([c]) => this.api.charges(c!.id).pipe(catchError(() => of([]))))));
  protected readonly announcements = toSignal(this.source.pipe(switchMap(([c]) => this.api.announcements(c!.id).pipe(catchError(() => of([] as Announcement[]))))));
  protected readonly documents = toSignal(this.source.pipe(switchMap(([c]) => this.api.documents(c!.id).pipe(catchError(() => of([]))))));
  protected readonly occurrences = toSignal(this.source.pipe(switchMap(([c]) => this.api.occurrences(c!.id).pipe(catchError(() => of([]))))));
  protected readonly assemblies = toSignal(this.source.pipe(switchMap(([c]) => this.api.assemblies(c!.id).pipe(catchError(() => of([]))))));

  private c(): string {
    return this.condo()!.id;
  }

  read(a: Announcement): void {
    if (!a.read) this.api.read(this.c(), a.id).subscribe(() => this.tick.update((t) => t + 1));
  }

  download(id: string): void {
    this.api.download(this.c(), id).subscribe({ error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  openOccurrence(id: string): void {
    this.detail.set(null);
    this.api.occurrence(this.c(), id).subscribe((d) => this.detail.set(d));
  }

  newOccurrence(): void {
    const units = this.condo()?.units ?? [];
    openForm(this.dialog, {
      title: 'Abrir solicitação ou ocorrência',
      fields: [
        { key: 'title', label: 'Assunto', required: true, maxLength: 150 },
        { key: 'description', label: 'Descreva', type: 'textarea', required: true, maxLength: 4000 },
        { key: 'category', label: 'Categoria', type: 'select', required: true, value: 'REQUEST', span: 6, options: options('occurrenceCategory') },
        { key: 'unitId', label: 'Relacionada à unidade', type: 'select', value: units[0]?.unitId ?? null, span: 6,
          options: [{ value: null, label: 'Área comum' }, ...units.map((u) => ({ value: u.unitId, label: u.label }))] },
      ],
      submitLabel: 'Enviar',
      submit: (v) => this.api.openOccurrence(this.c(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Solicitação enviada à administração.'); this.tick.update((t) => t + 1); });
  }

  comment(id: string): void {
    this.api.comment(this.c(), id, this.message.value.trim()).subscribe({
      next: (d) => { this.detail.set(d); this.message.reset(); },
      error: (err: unknown) => this.notify.error(toProblem(err).detail),
    });
  }
}

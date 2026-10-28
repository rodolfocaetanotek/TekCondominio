import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { catchError, combineLatest, filter, forkJoin, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { askConfirm } from '../../shared/ui/confirm-dialog';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { openForm } from '../../shared/ui/form-dialog';
import { PageHeader } from '../../shared/ui/page-header';
import { StructureService } from '../structure/structure.service';
import { Announcement, OpsApi } from './ops-api';

@Component({
  selector: 'tc-announcement-list',
  imports: [DatePipe, MatButtonModule, MatIconModule, PageHeader, EmptyState, CondominiumRequired],
  template: `
    <div class="tc-page narrow">
      <tc-page-header eyebrow="Comunicação" title="Comunicados" [subtitle]="context.selected()?.name">
        @if (canEdit()) { <button mat-flat-button (click)="publish()"><mat-icon>campaign</mat-icon> Novo comunicado</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @for (a of list() ?? []; track a.id) {
          <article class="tc-card item" [class.unread]="!a.read" (click)="markRead(a)">
            <header class="item-head">
              @if (a.pinned) { <mat-icon class="pin" aria-label="Fixado">push_pin</mat-icon> }
              <h3>{{ a.title }}</h3>
              <span class="tc-chip-soft">{{ a.audience === 'ALL' ? 'Todos' : a.audience === 'BUILDING' ? 'Bloco ' + a.buildingName : 'Unidade ' + a.unitLabel }}</span>
            </header>
            <p class="body">{{ a.body }}</p>
            <footer class="item-foot">
              <span class="meta tc-muted">
                {{ a.publishedAt | date: 'dd/MM/yyyy HH:mm' }} · {{ a.createdByName }}{{ a.sendEmail ? ' · enviado por e-mail' : '' }}
                · {{ a.readCount }} leitura(s){{ a.expiresOn ? ' · até ' + a.expiresOn.slice(8, 10) + '/' + a.expiresOn.slice(5, 7) : '' }}
              </span>
              @if (canEdit()) { <button mat-button (click)="archive(a, $event)"><mat-icon>archive</mat-icon> Arquivar</button> }
            </footer>
          </article>
        } @empty {
          <section class="tc-card"><tc-empty-state icon="campaign" title="Nenhum comunicado publicado" /></section>
        }
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 900px; }
    .item { display: grid; gap: 10px; margin-bottom: 12px; cursor: default; }
    .item.unread { border-left: 4px solid var(--mat-sys-primary); }
    .item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .item-head h3 { margin: 0; flex: 1 1 200px; min-width: 0; font: var(--mat-sys-title-medium); font-weight: 600; color: var(--tc-text); overflow-wrap: anywhere; }
    .pin { color: var(--tc-accent); }
    .body { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .item-foot { display: flex; align-items: center; gap: 8px 12px; flex-wrap: wrap; padding-top: 10px; border-top: 1px solid var(--tc-border); }
    .meta { flex: 1 1 240px; min-width: 0; font: var(--mat-sys-body-small); }
    .item-foot button { margin-left: auto; }
  `,
})
export class AnnouncementList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly structure = inject(StructureService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('ANNOUNCEMENT_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.announcements(c!).pipe(catchError(() => of([] as Announcement[]))))));

  publish(): void {
    const c = this.context.id()!;
    forkJoin({ buildings: this.structure.buildings(c), units: this.structure.units(c, { page: 0, size: 100, active: 'true' }).pipe(map((p) => p.content)) })
      .subscribe(({ buildings, units }) => openForm(this.dialog, {
        title: 'Novo comunicado',
        intro: 'Os moradores com acesso ao portal e a equipe recebem notificação. Com "enviar por e-mail", os moradores cadastrados também recebem e-mail.',
        fields: [
          { key: 'title', label: 'Título', required: true, maxLength: 150 },
          { key: 'body', label: 'Mensagem', type: 'textarea', required: true, maxLength: 8000 },
          { key: 'audience', label: 'Público', type: 'select', value: 'ALL', span: 4,
            options: [{ value: 'ALL', label: 'Todos' }, { value: 'BUILDING', label: 'Um bloco' }, { value: 'UNIT', label: 'Uma unidade' }] },
          { key: 'buildingId', label: 'Bloco (se público = bloco)', type: 'select', value: null, span: 4, options: [{ value: null, label: '—' }, ...buildings.map((b) => ({ value: b.id, label: b.name }))] },
          { key: 'unitId', label: 'Unidade (se público = unidade)', type: 'select', value: null, span: 4, options: [{ value: null, label: '—' }, ...units.map((u) => ({ value: u.id, label: u.label }))] },
          { key: 'expiresOn', label: 'Exibir até', type: 'date', span: 6 },
          { key: 'pinned', label: 'Fixar no topo', type: 'checkbox' },
          { key: 'sendEmail', label: 'Enviar também por e-mail', type: 'checkbox' },
        ],
        submitLabel: 'Publicar',
        submit: (v) => this.api.publish(c, v),
      }, '680px').pipe(filter((r) => !!r)).subscribe(() => { this.notify.success('Comunicado publicado.'); this.tick.update((t) => t + 1); }));
  }

  markRead(a: Announcement): void {
    if (!a.read) this.api.readAnnouncement(this.context.id()!, a.id).subscribe(() => this.tick.update((t) => t + 1));
  }

  archive(a: Announcement, event: Event): void {
    event.stopPropagation();
    askConfirm(this.dialog, 'Arquivar comunicado', `Arquivar "${a.title}"? Ele deixa de ser exibido aos moradores.`, 'Arquivar')
      .pipe(switchMap(() => this.api.archiveAnnouncement(this.context.id()!, a.id)))
      .subscribe({ next: () => this.tick.update((t) => t + 1), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }
}

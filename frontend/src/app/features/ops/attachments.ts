import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { NotificationService } from '../../core/notify/notification.service';
import { Doc, OpsApi } from './ops-api';

/** Anexos de um registro (lançamento, ocorrência, contrato, assembleia). Download sempre autenticado. */
@Component({
  selector: 'tc-attachments',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <section class="tc-card">
      <div class="row">
        <h3 class="tc-section-title">Anexos</h3>
        @if (canEdit()) {
          <label class="upload" mat-button>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.txt" (change)="upload($event)" />
            <mat-icon>attach_file</mat-icon> Anexar
          </label>
        }
      </div>
      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
      @for (d of docs(); track d.id) {
        <div class="doc">
          <mat-icon aria-hidden="true">{{ d.contentType.includes('pdf') ? 'picture_as_pdf' : d.contentType.startsWith('image') ? 'image' : 'description' }}</mat-icon>
          <span class="name">{{ d.title }} <small class="tc-muted">({{ size(d) }})</small></span>
          <button mat-icon-button (click)="download(d)" [attr.aria-label]="'Baixar ' + d.title"><mat-icon>download</mat-icon></button>
        </div>
      } @empty {
        <p class="tc-muted small">Nenhum anexo (notas fiscais, comprovantes, fotos).</p>
      }
    </section>
  `,
  styles: `
    .row { display: flex; justify-content: space-between; align-items: center; }
    .row h3 { margin: 0; }
    .upload { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; color: var(--mat-sys-primary); font: var(--mat-sys-label-large); padding: 8px; border-radius: 20px; }
    .upload:focus-within { outline: 2px solid var(--mat-sys-primary); }
    .upload input { position: absolute; opacity: 0; width: 1px; height: 1px; }
    .doc { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
    .name { flex: 1; word-break: break-word; }
    .small { font: var(--mat-sys-body-small); }
  `,
})
export class Attachments {
  readonly condominiumId = input.required<string>();
  readonly entityType = input.required<string>();
  readonly entityId = input.required<string>();
  readonly category = input('OTHER');
  readonly canEdit = input(false);
  private readonly api = inject(OpsApi);
  private readonly notify = inject(NotificationService);
  protected readonly busy = signal(false);
  private readonly tick = signal(0);

  protected readonly docs = toSignal(
    combineLatest([toObservable(this.condominiumId), toObservable(this.entityId), toObservable(this.tick)]).pipe(
      switchMap(([c, id]) => this.api.documents(c, { entityType: this.entityType(), entityId: id }).pipe(
        map((p) => p.content), catchError(() => of([] as Doc[])))),
    ),
    { initialValue: [] as Doc[] },
  );

  upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.busy.set(true);
    this.api.upload(this.condominiumId(), file, { category: this.category(), title: file.name, entityType: this.entityType(), entityId: this.entityId() })
      .subscribe({
        next: () => { this.busy.set(false); this.tick.update((t) => t + 1); this.notify.success('Arquivo anexado.'); },
        error: (err: unknown) => { this.busy.set(false); this.notify.error(toProblem(err).detail); },
      });
  }

  download(d: Doc): void {
    this.api.download(this.condominiumId(), d.id).subscribe({ error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  size(d: Doc): string {
    return d.sizeBytes > 1024 * 1024 ? (d.sizeBytes / 1024 / 1024).toFixed(1) + ' MB' : Math.ceil(d.sizeBytes / 1024) + ' KB';
  }
}

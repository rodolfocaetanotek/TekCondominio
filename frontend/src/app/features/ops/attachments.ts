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
          <label class="tc-file-button is-text" mat-button>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.txt" (change)="upload($event)" />
            <mat-icon>attach_file</mat-icon> Anexar
          </label>
        }
      </div>
      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }
      @for (d of docs(); track d.id) {
        <div class="doc">
          <mat-icon class="ftype" aria-hidden="true">{{ d.contentType.includes('pdf') ? 'picture_as_pdf' : d.contentType.startsWith('image') ? 'image' : 'description' }}</mat-icon>
          <span class="name">{{ d.title }} <small class="tc-muted">({{ size(d) }})</small></span>
          <button mat-icon-button (click)="download(d)" [attr.aria-label]="'Baixar ' + d.title"><mat-icon>download</mat-icon></button>
        </div>
      } @empty {
        <p class="tc-muted small">Nenhum anexo (notas fiscais, comprovantes, fotos).</p>
      }
    </section>
  `,
  styles: `
    .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
    .row h3 { margin: 0; }
    .doc { display: flex; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px solid var(--tc-border); }
    .doc:last-of-type { border-bottom: 0; }
    .ftype { color: var(--mat-sys-primary); flex: 0 0 auto; }
    .name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); margin: 4px 0 0; }
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
        map((p) => p.conteudo), catchError(() => of([] as Doc[])))),
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

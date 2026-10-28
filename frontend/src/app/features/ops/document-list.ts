import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { toProblem } from '../../core/api/api.models';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { options } from '../../shared/labels';
import { askConfirm } from '../../shared/ui/confirm-dialog';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Doc, OpsApi } from './ops-api';

@Component({
  selector: 'tc-document-list',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, PageHeader,
    EmptyState, CondominiumRequired, LabelPipe],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Arquivo" title="Documentos" [subtitle]="context.selected()?.nome" />
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        @if (canEdit()) {
          <section class="tc-card upload">
            <h3 class="tc-section-title">Enviar documento</h3>
            <div class="row">
              <mat-form-field><mat-label>Título</mat-label><input matInput [formControl]="title" maxlength="200" /></mat-form-field>
              <mat-form-field><mat-label>Categoria</mat-label><mat-select [formControl]="category">
                @for (o of categories; track o.value) { <mat-option [value]="o.value">{{ o.label }}</mat-option> }</mat-select></mat-form-field>
              <mat-form-field><mat-label>Visibilidade</mat-label><mat-select [formControl]="visibility">
                <mat-option value="INTERNAL">Interno (equipe)</mat-option><mat-option value="RESIDENTS">Moradores (portal)</mat-option></mat-select></mat-form-field>
              <label class="tc-file-button file"><input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.txt" (change)="pick($event)" />
                <mat-icon>upload_file</mat-icon> {{ file()?.name ?? 'Escolher arquivo' }}</label>
              <button mat-flat-button (click)="upload()" [disabled]="!file() || busy()">Enviar</button>
            </div>
            <p class="tc-muted small">PDF, PNG, JPG, DOCX, XLSX, CSV ou TXT até 10 MB. O conteúdo é verificado e o arquivo só é acessível a usuários autorizados.</p>
          </section>
        }
        <div class="filters">
          <mat-form-field><mat-label>Categoria</mat-label><mat-select [formControl]="filterCategory">
            <mat-option value="">Todas</mat-option>
            @for (o of categories; track o.value) { <mat-option [value]="o.value">{{ o.label }}</mat-option> }</mat-select></mat-form-field>
        </div>
        <section class="tc-table-card">
          @for (d of docs(); track d.id) {
            <div class="doc">
              <span class="tc-ico" aria-hidden="true"><mat-icon>{{ d.contentType.includes('pdf') ? 'picture_as_pdf' : d.contentType.startsWith('image') ? 'image' : 'description' }}</mat-icon></span>
              <div class="who"><strong>{{ d.title }}</strong>
                <span class="meta tc-muted">{{ d.category | label: 'docCategory' }} · {{ d.visibility === 'RESIDENTS' ? 'visível aos moradores' : 'interno' }}
                  · {{ d.fileName }} · {{ d.createdAt | date: 'dd/MM/yyyy' }} · {{ d.createdByName }}{{ d.entityType ? ' · anexo' : '' }}</span></div>
              <div class="acts">
              <button mat-icon-button (click)="download(d)" [attr.aria-label]="'Baixar ' + d.title"><mat-icon>download</mat-icon></button>
              @if (canEdit() && !d.entityType) { <button mat-icon-button (click)="archive(d)" [attr.aria-label]="'Arquivar ' + d.title"><mat-icon>archive</mat-icon></button> }
              </div>
            </div>
          } @empty { <tc-empty-state icon="folder_open" title="Nenhum documento" /> }
        </section>
      }
    </div>
  `,
  styles: `
    .upload { margin-bottom: 16px; } .upload h3 { margin-top: 0; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .row > mat-form-field { flex: 1 1 200px; min-width: 0; }
    /* Seletor com aparÃªncia de campo (fica na linha dos campos do formulÃ¡rio). */
    .file { min-height: 44px; padding: 8px 14px; background: var(--tc-field-bg); color: var(--tc-text); font-weight: inherit; }
    .file mat-icon { color: var(--mat-sys-primary); }
    .file:hover { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .small { font: var(--mat-sys-body-small); margin: 8px 0 0; }
    .doc { display: flex; align-items: center; gap: 12px; padding: 12px 20px; transition: background-color 0.12s; }
    .doc + .doc { border-top: 1px solid var(--tc-border); }
    .doc:hover { background: color-mix(in srgb, var(--mat-sys-primary) 3%, var(--tc-surface)); }
    .who { flex: 1; display: grid; gap: 2px; min-width: 0; }
    .who strong, .meta { overflow-wrap: anywhere; }
    .meta { font: var(--mat-sys-body-small); }
    .acts { display: flex; align-items: center; flex: 0 0 auto; }
    @media (max-width: 600px) {
      .row > *, .row > button { flex: 1 1 100%; }
      .file { justify-content: center; }
      .doc { padding: 12px 14px; align-items: flex-start; }
    }
  `,
})
export class DocumentList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  protected readonly categories = options('docCategory');
  protected readonly title = new FormControl('', { nonNullable: true });
  protected readonly category = new FormControl('OTHER', { nonNullable: true });
  protected readonly visibility = new FormControl('INTERNAL', { nonNullable: true });
  protected readonly filterCategory = new FormControl('', { nonNullable: true });
  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('DOCUMENT_EDIT'));
  private readonly categoryValue = toSignal(this.filterCategory.valueChanges, { initialValue: '' });
  protected readonly docs = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.categoryValue), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, cat]) => this.api.documents(c!, cat ? { category: cat } : {}).pipe(map((p) => p.conteudo), catchError(() => of([] as Doc[]))))),
    { initialValue: [] as Doc[] });

  pick(event: Event): void {
    const f = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file.set(f);
    if (f && !this.title.value) this.title.setValue(f.name.replace(/\.[^.]+$/, ''));
  }

  upload(): void {
    const f = this.file();
    if (!f) return;
    this.busy.set(true);
    this.api.upload(this.context.id()!, f, { title: this.title.value || f.name, category: this.category.value, visibility: this.visibility.value })
      .subscribe({
        next: () => { this.busy.set(false); this.file.set(null); this.title.reset(); this.notify.success('Documento enviado.'); this.tick.update((t) => t + 1); },
        error: (err: unknown) => { this.busy.set(false); this.notify.error(toProblem(err).detail); },
      });
  }

  download(d: Doc): void {
    this.api.download(this.context.id()!, d.id).subscribe({ error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }

  archive(d: Doc): void {
    askConfirm(this.dialog, 'Arquivar documento', `Arquivar "${d.title}"? Ele sai da lista, mas é preservado no histórico.`, 'Arquivar')
      .pipe(switchMap(() => this.api.archiveDocument(this.context.id()!, d.id)))
      .subscribe({ next: () => this.tick.update((t) => t + 1), error: (err: unknown) => this.notify.error(toProblem(err).detail) });
  }
}

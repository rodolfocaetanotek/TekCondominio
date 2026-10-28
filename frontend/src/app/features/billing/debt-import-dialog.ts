import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { toProblem } from '../../core/api/api.models';
import { DialogClose } from '../../shared/ui/dialog-close';
import { DebtImportResult, FinanceApi } from '../finance/finance-api';

const TEMPLATE = 'bloco;unidade;competencia;vencimento;valor;descricao;documento;conta\n'
  + 'A;101;08/2026;10/08/2026;450,00;Taxa condominial 08/2026;12345;1.01\n'
  + 'A;102;09/2026;10/09/2026;1.200,50;Taxa extra;12346;1.03\n';

@Component({
  selector: 'tc-debt-import-dialog',
  imports: [CurrencyPipe, DialogClose, MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>Importar débitos em aberto <tc-dialog-close /></h2>
    <mat-dialog-content>
      <p class="tc-muted">Traga os débitos do sistema anterior. Colunas:
        <code>bloco; unidade; competencia; vencimento; valor; descricao; documento; conta</code>.
        Obrigatórias: <strong>unidade, competencia (MM/AAAA), vencimento (DD/MM/AAAA) e valor</strong>.
        A conta padrão é 1.01 (taxa condominial). O <strong>documento</strong> (número do título no sistema anterior) evita importar o mesmo débito duas vezes.</p>
      <p><a href="" (click)="downloadTemplate($event)">Baixar modelo de planilha</a></p>

      <label class="file">
        <input type="file" accept=".csv,text/csv" (change)="pick($event)" />
        <mat-icon>upload_file</mat-icon>
        <span>{{ file()?.name ?? 'Escolher arquivo CSV (até 2 MB)' }}</span>
      </label>

      @if (busy()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (error(); as message) {
        <p class="tc-alert" role="alert">{{ message }}</p>
      }
      @if (result(); as r) {
        @if (r.errors.length) {
          <p class="tc-alert" role="alert">{{ r.errors.length }} problema(s) encontrado(s). Nada foi gravado — corrija a planilha e envie novamente.</p>
          <ul class="errors">
            @for (e of r.errors; track $index) {
              <li><strong>Linha {{ e.line }}:</strong> {{ e.message }}</li>
            }
          </ul>
        } @else if (r.dryRun) {
          <p class="ok" role="status"><mat-icon>check_circle</mat-icon>
            Planilha válida: {{ r.debts }} débito(s), total {{ r.total | currency: 'BRL' }}. Confirme para importar.</p>
        } @else {
          <p class="ok" role="status"><mat-icon>task_alt</mat-icon>
            Importação concluída: {{ r.debts }} débito(s), total {{ r.total | currency: 'BRL' }}.</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (result() && !result()!.dryRun && !result()!.errors.length) {
        <button mat-flat-button (click)="ref.close(true)">Concluir</button>
      } @else {
        <button mat-button mat-dialog-close>Cancelar</button>
        @if (result()?.dryRun && !result()!.errors.length) {
          <button mat-flat-button (click)="run(false)" [disabled]="busy()">Importar agora</button>
        } @else {
          <button mat-flat-button (click)="run(true)" [disabled]="!file() || busy()">Validar planilha</button>
        }
      }
    </mat-dialog-actions>
  `,
  styles: `
    .file { display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px dashed var(--tc-border-strong); border-radius: var(--tc-radius); background: var(--tc-surface-muted); cursor: pointer; margin-bottom: 12px; overflow-wrap: anywhere; }
    .file:hover { border-color: var(--mat-sys-primary); }
    .file input { position: absolute; opacity: 0; width: 1px; height: 1px; }
    .file:focus-within { outline: 2px solid var(--mat-sys-primary); }
    .errors { max-height: 220px; overflow: auto; padding-left: 18px; font: var(--mat-sys-body-small); }
    .ok { display: flex; gap: 8px; align-items: center; color: var(--tc-success); }
    code { font-size: .85em; }
  `,
})
export class DebtImportDialog {
  private readonly data = inject<{ condominiumId: string }>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<DebtImportDialog, boolean>);
  private readonly api = inject(FinanceApi);

  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly result = signal<DebtImportResult | null>(null);
  protected readonly error = signal<string | null>(null);

  pick(event: Event): void {
    this.file.set((event.target as HTMLInputElement).files?.[0] ?? null);
    this.result.set(null);
    this.error.set(null);
  }

  run(dryRun: boolean): void {
    const file = this.file();
    if (!file) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.importDebts(this.data.condominiumId, file, dryRun).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.result.set(r);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }

  downloadTemplate(event: Event): void {
    event.preventDefault();
    const url = URL.createObjectURL(new Blob(['﻿' + TEMPLATE], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'modelo-debitos.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}

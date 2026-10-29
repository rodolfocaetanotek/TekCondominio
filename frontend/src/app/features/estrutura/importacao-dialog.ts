import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { toProblem } from '../../core/api/api.models';
import { ResultadoImportacao } from './estrutura.models';
import { StructureService } from './estrutura.service';
import { DialogClose } from '../../comum/ui/fechar-dialog';

const TEMPLATE = 'bloco;unidade;tipo;fracao;area;proprietario;documento;email;telefone\n'
  + 'A;101;apartamento;2,5;72,40;Maria da Silva;529.982.247-25;maria@email.com;(91) 98888-7777\n'
  + 'A;102;apartamento;2,5;72,40;;;;\n';

/** Importação de unidades e proprietários: valida primeiro (simulação) e só grava após confirmação. */
@Component({
  selector: 'tc-import-dialog',
  imports: [DialogClose, MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>Importar unidades (CSV) <tc-dialog-close /></h2>
    <mat-dialog-content>
      <p class="tc-muted">Colunas: <code>bloco; unidade; tipo; fracao; area; proprietario; documento; email; telefone</code>.
        Apenas <strong>unidade</strong> é obrigatória. Pessoas já cadastradas com o mesmo CPF/CNPJ são reaproveitadas.</p>
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
        @if (r.erros.length) {
          <p class="tc-alert" role="alert">{{ r.erros.length }} problema(s) encontrado(s). Nada foi gravado — corrija a planilha e envie novamente.</p>
          <ul class="errors">
            @for (e of r.erros; track $index) {
              <li><strong>Linha {{ e.linha }}:</strong> {{ e.mensagem }}</li>
            }
          </ul>
        } @else if (r.simulacao) {
          <p class="ok" role="status"><mat-icon>check_circle</mat-icon>
            Planilha válida: {{ r.unidadesCriadas }} unidade(s){{ r.blocosCriados ? ', ' + r.blocosCriados + ' bloco(s) novo(s)' : '' }}.
            Confirme para importar.</p>
        } @else {
          <p class="ok" role="status"><mat-icon>task_alt</mat-icon>
            Importação concluída: {{ r.unidadesCriadas }} unidade(s), {{ r.blocosCriados }} bloco(s) novo(s),
            {{ r.pessoasCriadas }} pessoa(s) nova(s), {{ r.pessoasReaproveitadas }} reaproveitada(s).</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (result() && !result()!.simulacao && !result()!.erros.length) {
        <button mat-flat-button (click)="ref.close(true)">Concluir</button>
      } @else {
        <button mat-button mat-dialog-close>Cancelar</button>
        @if (result()?.simulacao && !result()!.erros.length) {
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
export class ImportDialog {
  private readonly data = inject<{ condominiumId: string }>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ImportDialog, boolean>);
  private readonly service = inject(StructureService);

  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly result = signal<ResultadoImportacao | null>(null);
  protected readonly error = signal<string | null>(null);

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.result.set(null);
    this.error.set(null);
  }

  run(simulacao: boolean): void {
    const file = this.file();
    if (!file) return;
    this.busy.set(true);
    this.error.set(null);
    this.service.importUnits(this.data.condominiumId, file, simulacao).subscribe({
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
    const blob = new Blob(['﻿' + TEMPLATE], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'modelo-unidades.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}

import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { paraProblema } from '../../core/api/api.models';
import { ResultadoImportacao } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

const MODELO = 'bloco;unidade;tipo;fracao;area;proprietario;documento;email;telefone\n'
  + 'A;101;apartamento;2,5;72,40;Maria da Silva;529.982.247-25;maria@email.com;(91) 98888-7777\n'
  + 'A;102;apartamento;2,5;72,40;;;;\n';

/** Importação de unidades e proprietários: valida primeiro (simulação) e só grava após confirmação. */
@Component({
  selector: 'tc-importacao-dialog',
  imports: [FecharDialog, MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>Importar unidades (CSV) <tc-fechar-dialog /></h2>
    <mat-dialog-content>
      <p class="tc-muted">Colunas: <code>bloco; unidade; tipo; fracao; area; proprietario; documento; email; telefone</code>.
        Apenas <strong>unidade</strong> é obrigatória. Pessoas já cadastradas com o mesmo CPF/CNPJ são reaproveitadas.</p>
      <p><a href="" (click)="baixarModelo($event)">Baixar modelo de planilha</a></p>

      <label class="file">
        <input type="file" accept=".csv,text/csv" (change)="escolher($event)" />
        <mat-icon>upload_file</mat-icon>
        <span>{{ arquivo()?.name ?? 'Escolher arquivo CSV (até 2 MB)' }}</span>
      </label>

      @if (ocupado()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (erro(); as mensagem) {
        <p class="tc-alert" role="alert">{{ mensagem }}</p>
      }
      @if (resultado(); as r) {
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
      @if (resultado() && !resultado()!.simulacao && !resultado()!.erros.length) {
        <button mat-flat-button (click)="ref.close(true)">Concluir</button>
      } @else {
        <button mat-button mat-dialog-close>Cancelar</button>
        @if (resultado()?.simulacao && !resultado()!.erros.length) {
          <button mat-flat-button (click)="executar(false)" [disabled]="ocupado()">Importar agora</button>
        } @else {
          <button mat-flat-button (click)="executar(true)" [disabled]="!arquivo() || ocupado()">Validar planilha</button>
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
export class ImportacaoDialog {
  private readonly data = inject<{ idCondominio: string }>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ImportacaoDialog, boolean>);
  private readonly service = inject(EstruturaService);

  protected readonly arquivo = signal<File | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly resultado = signal<ResultadoImportacao | null>(null);
  protected readonly erro = signal<string | null>(null);

  escolher(evento: Event): void {
    const campoArquivo = evento.target as HTMLInputElement;
    this.arquivo.set(campoArquivo.files?.[0] ?? null);
    this.resultado.set(null);
    this.erro.set(null);
  }

  executar(simulacao: boolean): void {
    const arquivo = this.arquivo();
    if (!arquivo) return;
    this.ocupado.set(true);
    this.erro.set(null);
    this.service.importarUnidades(this.data.idCondominio, arquivo, simulacao).subscribe({
      next: (r) => {
        this.ocupado.set(false);
        this.resultado.set(r);
      },
      error: (err: unknown) => {
        this.ocupado.set(false);
        this.erro.set(paraProblema(err).detail);
      },
    });
  }

  baixarModelo(evento: Event): void {
    evento.preventDefault();
    const blob = new Blob(['﻿' + MODELO], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'modelo-unidades.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}

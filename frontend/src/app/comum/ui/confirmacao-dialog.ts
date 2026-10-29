import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable, filter } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FecharDialog } from './fechar-dialog';

export interface DadosConfirmacaoDialog {
  titulo: string;
  mensagem: string;
  rotuloConfirmar: string;
  perigo?: boolean;
  /** Quando informado, exige justificativa (ex.: inativação, estorno). */
  rotuloMotivo?: string;
}

/** Confirmação para operações críticas; opcionalmente coleta justificativa. Fecha com a justificativa ou true. */
@Component({
  selector: 'tc-confirmacao-dialog',
  imports: [FecharDialog, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title [class.is-danger]="data.perigo">
      {{ data.titulo }} <tc-fechar-dialog />
    </h2>
    <mat-dialog-content>
      <p>{{ data.mensagem }}</p>
      @if (data.rotuloMotivo) {
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ data.rotuloMotivo }}</mat-label>
          <textarea matInput [formControl]="motivo" rows="3" maxlength="500" cdkFocusInitial></textarea>
          <mat-hint align="end">{{ motivo.value.length }}/500</mat-hint>
          @if (motivo.touched && motivo.invalid) {
            <mat-error>Informe uma justificativa com pelo menos 5 caracteres.</mat-error>
          }
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button [class.danger]="data.perigo" (click)="confirmar()">{{ data.rotuloConfirmar }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; margin-top: 8px; }
    p { margin: 0; line-height: 1.5; }
  `,
})
export class ConfirmacaoDialog {
  protected readonly data = inject<DadosConfirmacaoDialog>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ConfirmacaoDialog, string | true>);
  protected readonly motivo = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(5), Validators.maxLength(500)],
  });

  confirmar(): void {
    if (!this.data.rotuloMotivo) {
      this.ref.close(true);
      return;
    }
    this.motivo.markAsTouched();
    if (this.motivo.valid) {
      this.ref.close(this.motivo.value.trim());
    }
  }
}


/** Pede justificativa (operações críticas: cancelar, estornar). Emite somente se confirmado. */
export function pedirMotivo(dialog: MatDialog, titulo: string, mensagem: string, rotuloConfirmar = 'Confirmar'): Observable<string> {
  return dialog
    .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, {
      data: { titulo, mensagem, rotuloConfirmar, perigo: true, rotuloMotivo: 'Justificativa' },
      width: '480px',
    })
    .afterClosed()
    .pipe(filter((v): v is string => typeof v === 'string'));
}

/** Confirmação simples. Emite somente se confirmado. */
export function pedirConfirmacao(dialog: MatDialog, titulo: string, mensagem: string, rotuloConfirmar = 'Confirmar', perigo = false): Observable<true> {
  return dialog
    .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, { data: { titulo, mensagem, rotuloConfirmar, perigo }, width: '440px' })
    .afterClosed()
    .pipe(filter((v): v is true => v === true));
}

import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable, filter } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DialogClose } from './dialog-close';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
  /** Quando informado, exige justificativa (ex.: inativação, estorno). */
  reasonLabel?: string;
}

/** Confirmação para operações críticas; opcionalmente coleta justificativa. Fecha com a justificativa ou true. */
@Component({
  selector: 'tc-confirm-dialog',
  imports: [DialogClose, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title [class.is-danger]="data.danger">
      {{ data.title }} <tc-dialog-close />
    </h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @if (data.reasonLabel) {
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ data.reasonLabel }}</mat-label>
          <textarea matInput [formControl]="reason" rows="3" maxlength="500" cdkFocusInitial></textarea>
          <mat-hint align="end">{{ reason.value.length }}/500</mat-hint>
          @if (reason.touched && reason.invalid) {
            <mat-error>Informe uma justificativa com pelo menos 5 caracteres.</mat-error>
          }
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button [class.danger]="data.danger" (click)="confirm()">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; margin-top: 8px; }
    p { margin: 0; line-height: 1.5; }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ConfirmDialog, string | true>);
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(5), Validators.maxLength(500)],
  });

  confirm(): void {
    if (!this.data.reasonLabel) {
      this.ref.close(true);
      return;
    }
    this.reason.markAsTouched();
    if (this.reason.valid) {
      this.ref.close(this.reason.value.trim());
    }
  }
}


/** Pede justificativa (operações críticas: cancelar, estornar). Emite somente se confirmado. */
export function askReason(dialog: MatDialog, title: string, message: string, confirmLabel = 'Confirmar'): Observable<string> {
  return dialog
    .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, {
      data: { title, message, confirmLabel, danger: true, reasonLabel: 'Justificativa' },
      width: '480px',
    })
    .afterClosed()
    .pipe(filter((v): v is string => typeof v === 'string'));
}

/** Confirmação simples. Emite somente se confirmado. */
export function askConfirm(dialog: MatDialog, title: string, message: string, confirmLabel = 'Confirmar', danger = false): Observable<true> {
  return dialog
    .open<ConfirmDialog, ConfirmDialogData, string | true>(ConfirmDialog, { data: { title, message, confirmLabel, danger }, width: '440px' })
    .afterClosed()
    .pipe(filter((v): v is true => v === true));
}

import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

/** Botão "×" da faixa de título dos diálogos (equivale a Cancelar). */
@Component({
  selector: 'tc-fechar-dialog',
  imports: [MatButtonModule, MatDialogModule, MatIconModule],
  template: `<button mat-icon-button type="button" class="tc-dialog-close" mat-dialog-close aria-label="Fechar"><mat-icon>close</mat-icon></button>`,
})
export class FecharDialog {}

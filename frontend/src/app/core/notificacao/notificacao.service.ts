import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificacaoService {
  private readonly snackBar = inject(MatSnackBar);

  sucesso(mensagem: string): void {
    this.snackBar.open(mensagem, 'OK', { duration: 4000, panelClass: 'tc-snack-success', politeness: 'polite' });
  }

  erro(mensagem: string): void {
    this.snackBar.open(mensagem, 'Fechar', { duration: 7000, panelClass: 'tc-snack-error', politeness: 'assertive' });
  }
}

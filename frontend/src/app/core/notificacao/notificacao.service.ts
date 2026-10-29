import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000, panelClass: 'tc-snack-success', politeness: 'polite' });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Fechar', { duration: 7000, panelClass: 'tc-snack-error', politeness: 'assertive' });
  }
}

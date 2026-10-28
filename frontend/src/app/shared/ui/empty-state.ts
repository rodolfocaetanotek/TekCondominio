import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'tc-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty" role="status">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      <p class="title">{{ title() }}</p>
      @if (description()) {
        <p class="tc-muted">{{ description() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty { display: grid; justify-items: center; gap: 4px; padding: 48px 16px; text-align: center; }
    mat-icon { font-size: 44px; width: 44px; height: 44px; color: var(--mat-sys-primary); opacity: .8; }
    .title { font: var(--mat-sys-title-medium); margin: 8px 0 0; }
    p { margin: 0; max-width: 420px; }
  `,
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly description = input<string>();
}

import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'tc-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty" role="status">
      <span class="icon" aria-hidden="true"><mat-icon>{{ icon() }}</mat-icon></span>
      <p class="title">{{ title() }}</p>
      @if (description()) {
        <p class="tc-muted">{{ description() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty { display: grid; justify-items: center; gap: 6px; padding: 48px 16px; text-align: center; }
    .icon { display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%; margin-bottom: 6px;
      background: color-mix(in srgb, var(--mat-sys-primary) 10%, transparent); color: var(--mat-sys-primary); }
    .icon mat-icon { font-size: 32px; width: 32px; height: 32px; }
    .title { font-size: 16px; font-weight: 600; margin: 0; color: var(--tc-text); }
    p { margin: 0; max-width: 440px; }
    .empty ::ng-deep > button, .empty ::ng-deep > a { margin-top: 10px; }
  `,
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly description = input<string>();
}

import { Component, input } from '@angular/core';

@Component({
  selector: 'tc-page-header',
  template: `
    <header class="head">
      <div>
        @if (eyebrow()) {
          <p class="eyebrow">{{ eyebrow() }}</p>
        }
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="tc-muted sub">{{ subtitle() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `,
  styles: `
    .head { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; }
    .head > div:first-child { flex: 1 1 280px; }
    h1 { font: var(--mat-sys-headline-small); margin: 0; letter-spacing: -0.01em; }
    .eyebrow { font: var(--mat-sys-label-medium); color: var(--mat-sys-tertiary); text-transform: uppercase; letter-spacing: .08em; margin: 0 0 4px; }
    .sub { margin: 4px 0 0; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly eyebrow = input<string>();
}

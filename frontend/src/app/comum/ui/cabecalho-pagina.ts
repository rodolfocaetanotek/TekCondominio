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
    .head { display: flex; align-items: flex-end; gap: 12px 16px; flex-wrap: wrap; margin-bottom: 20px; }
    .head > div:first-child { flex: 1 1 280px; min-width: 0; }
    h1 { font-size: 24px; line-height: 1.25; font-weight: 650; letter-spacing: -0.02em; margin: 0; color: var(--tc-text); }
    .eyebrow { font-size: 11.5px; font-weight: 600; color: var(--tc-text-muted); text-transform: uppercase; letter-spacing: .08em; margin: 0 0 4px; }
    .sub { margin: 4px 0 0; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
    @media (max-width: 600px) {
      h1 { font-size: 20px; }
      .actions { width: 100%; }
      .actions ::ng-deep > * { flex: 1 1 auto; }
    }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly eyebrow = input<string>();
}

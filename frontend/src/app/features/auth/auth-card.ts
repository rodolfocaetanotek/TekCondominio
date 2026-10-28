import { Component, input } from '@angular/core';

/** Moldura das telas públicas de conta (esqueci a senha, definir senha). */
@Component({
  selector: 'tc-auth-card',
  template: `
    <main class="wrap">
      <div class="brand">
        <img src="favicon.svg" alt="" width="36" height="36" />
        <span>Tek Condomínio</span>
      </div>
      <section class="card">
        <h1>{{ title() }}</h1>
        <ng-content />
      </section>
    </main>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; place-content: center; justify-items: center; gap: 20px; padding: 24px 16px; }
    .brand { display: flex; align-items: center; gap: 10px; font: var(--mat-sys-title-large); color: var(--mat-sys-primary); font-weight: 700; }
    .card { width: min(440px, calc(100vw - 32px)); background: var(--mat-sys-surface); border: 1px solid var(--mat-sys-outline-variant);
      border-radius: var(--tc-radius); padding: 28px; }
    h1 { font: var(--mat-sys-headline-small); margin: 0 0 8px; }
  `,
})
export class AuthCard {
  readonly title = input.required<string>();
}

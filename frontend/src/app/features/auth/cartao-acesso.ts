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
    .wrap { min-height: 100vh; display: grid; place-content: center; justify-items: center; gap: 24px; padding: 24px 16px;
      background: radial-gradient(circle at 80% 0%, rgb(238 122 12 / 0.08), transparent 45%), var(--tc-bg); }
    .brand { display: flex; align-items: center; gap: 10px; font-size: 20px; letter-spacing: -0.02em; color: var(--mat-sys-primary); font-weight: 700; }
    .card { width: min(440px, calc(100vw - 32px)); background: var(--tc-surface); border: 1px solid var(--tc-border);
      border-radius: 14px; box-shadow: var(--tc-shadow-lg); padding: 32px 28px; }
    h1 { font-size: 22px; font-weight: 700; letter-spacing: -0.02em; margin: 0 0 8px; }
    @media (max-width: 600px) { .card { padding: 24px 18px; box-shadow: var(--tc-shadow); } }
  `,
})
export class AuthCard {
  readonly title = input.required<string>();
}

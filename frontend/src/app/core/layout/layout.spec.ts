import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ResponsiveTables } from './responsive-tables';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('segue o sistema por padrão e aplica a escolha explícita no <html>', () => {
    const theme = TestBed.inject(ThemeService);
    expect(theme.mode()).toBe('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);

    theme.set('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('tc-theme')).toBe('dark');

    theme.set('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('tc-theme')).toBeNull();
  });

  it('recupera a escolha guardada e ignora valores desconhecidos', () => {
    localStorage.setItem('tc-theme', 'light');
    expect(TestBed.inject(ThemeService).mode()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    TestBed.resetTestingModule();
    localStorage.setItem('tc-theme', 'roxo');
    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });
});

@Component({
  imports: [ResponsiveTables],
  template: `
    <div tcResponsiveTables>
      <table>
        <thead><tr><th>Unidade</th><th>Situação</th><th></th></tr></thead>
        <tbody>
          @for (r of rows(); track r) { <tr><td>{{ r }}</td><td>Ativa</td><td><button>Abrir</button></td></tr> }
        </tbody>
      </table>
    </div>
  `,
})
class Host {
  readonly rows = signal(['101']);
}

describe('ResponsiveTables', () => {
  const frame = () => new Promise((r) => requestAnimationFrame(() => r(null)));

  it('copia o cabeçalho para data-label das células, inclusive de linhas novas', async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await frame();
    const cells = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr:last-child td')) as HTMLElement[];
    expect(cells().map((c) => c.getAttribute('data-label'))).toEqual(['Unidade', 'Situação', null]);

    fixture.componentInstance.rows.set(['101', '102']);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // MutationObserver
    await frame();
    expect(cells()[0].textContent).toBe('102');
    expect(cells()[1].getAttribute('data-label')).toBe('Situação');
  });
});

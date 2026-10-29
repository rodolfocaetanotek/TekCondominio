import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TabelasResponsivas } from './tabelas-responsivas';
import { TemaService } from './tema.service';

describe('TemaService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('segue o sistema por padrão e aplica a escolha explícita no <html>', () => {
    const tema = TestBed.inject(TemaService);
    expect(tema.modo()).toBe('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);

    tema.definir('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('tc-theme')).toBe('dark');

    tema.definir('system');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('tc-theme')).toBeNull();
  });

  it('recupera a escolha guardada e ignora valores desconhecidos', () => {
    localStorage.setItem('tc-theme', 'light');
    expect(TestBed.inject(TemaService).modo()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    TestBed.resetTestingModule();
    localStorage.setItem('tc-theme', 'roxo');
    expect(TestBed.inject(TemaService).modo()).toBe('system');
  });
});

@Component({
  imports: [TabelasResponsivas],
  template: `
    <div tcTabelasResponsivas>
      <table>
        <thead><tr><th>Unidade</th><th>Situação</th><th></th></tr></thead>
        <tbody>
          @for (r of linhas(); track r) { <tr><td>{{ r }}</td><td>Ativa</td><td><button>Abrir</button></td></tr> }
        </tbody>
      </table>
    </div>
  `,
})
class Hospedeiro {
  readonly linhas = signal(['101']);
}

describe('TabelasResponsivas', () => {
  const quadro = () => new Promise((r) => requestAnimationFrame(() => r(null)));

  it('copia o cabeçalho para data-label das células, inclusive de linhas novas', async () => {
    const fixture = TestBed.createComponent(Hospedeiro);
    fixture.detectChanges();
    await quadro();
    const celulas = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr:last-child td')) as HTMLElement[];
    expect(celulas().map((c) => c.getAttribute('data-label'))).toEqual(['Unidade', 'Situação', null]);

    fixture.componentInstance.linhas.set(['101', '102']);
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // MutationObserver
    await quadro();
    expect(celulas()[0].textContent).toBe('102');
    expect(celulas()[1].getAttribute('data-label')).toBe('Situação');
  });
});

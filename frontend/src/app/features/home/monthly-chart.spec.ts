import { TestBed } from '@angular/core/testing';
import { label, options } from '../../shared/labels';
import { MonthlyChart } from './monthly-chart';

describe('MonthlyChart', () => {
  it('desenha colunas ancoradas na base e escala pelo maior valor', () => {
    const fixture = TestBed.createComponent(MonthlyChart);
    fixture.componentRef.setInput('points', [
      { mes: '2026-09', receitas: 1000, despesas: 400 },
      { mes: '2026-10', receitas: 0, despesas: 1500 },
    ]);
    fixture.detectChanges();
    const paths = Array.from(fixture.nativeElement.querySelectorAll('path.bar')) as SVGPathElement[];
    const drawn = paths.filter((p) => p.getAttribute('d'));
    expect(drawn.length).toBe(3); // receita zero não desenha coluna
    expect(fixture.nativeElement.textContent).toContain('2 mil'); // topo do eixo arredondado
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toContain('receitas');
  });

  it('oferece tabela alternativa com resultado do mês', () => {
    const fixture = TestBed.createComponent(MonthlyChart);
    fixture.componentRef.setInput('points', [{ mes: '2026-10', receitas: 300, despesas: 100 }]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const text = fixture.nativeElement.querySelector('table').textContent as string;
    expect(text).toContain('out/26');
    expect(text).toMatch(/200[.,]00/);
  });
});

describe('labels', () => {
  it('traduz códigos e devolve o próprio código quando desconhecido', () => {
    expect(label('entryStatus', 'PAID')).toBe('Pago');
    expect(label('entryStatus', 'XYZ')).toBe('XYZ');
    expect(label('entryStatus', null)).toBe('—');
    expect(options('priority').map((o) => o.value)).toEqual(['LOW', 'MEDIUM', 'HIGH', 'URGENT']);
  });
});

import { TestBed } from '@angular/core/testing';
import { rotulo, opcoes } from '../../comum/rotulos';
import { GraficoMensal } from './grafico-mensal';

describe('GraficoMensal', () => {
  it('desenha colunas ancoradas na base e escala pelo maior valor', () => {
    const fixture = TestBed.createComponent(GraficoMensal);
    fixture.componentRef.setInput('pontos', [
      { mes: '2026-09', receitas: 1000, despesas: 400 },
      { mes: '2026-10', receitas: 0, despesas: 1500 },
    ]);
    fixture.detectChanges();
    const caminhos = Array.from(fixture.nativeElement.querySelectorAll('path.bar')) as SVGPathElement[];
    const desenhado = caminhos.filter((p) => p.getAttribute('d'));
    expect(desenhado.length).toBe(3); // receita zero não desenha coluna
    expect(fixture.nativeElement.textContent).toContain('2 mil'); // topo do eixo arredondado
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toContain('receitas');
  });

  it('oferece tabela alternativa com resultado do mês', () => {
    const fixture = TestBed.createComponent(GraficoMensal);
    fixture.componentRef.setInput('pontos', [{ mes: '2026-10', receitas: 300, despesas: 100 }]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    const texto = fixture.nativeElement.querySelector('table').textContent as string;
    expect(texto).toContain('out/26');
    expect(texto).toMatch(/200[.,]00/);
  });
});

describe('rotulos', () => {
  it('traduz códigos e devolve o próprio código quando desconhecido', () => {
    expect(rotulo('entryStatus', 'PAID')).toBe('Pago');
    expect(rotulo('entryStatus', 'XYZ')).toBe('XYZ');
    expect(rotulo('entryStatus', null)).toBe('—');
    expect(opcoes('priority').map((o) => o.valor)).toEqual(['LOW', 'MEDIUM', 'HIGH', 'URGENT']);
  });
});

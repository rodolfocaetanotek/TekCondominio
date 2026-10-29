import { AfterViewInit, Directive, ElementRef, OnDestroy, inject } from '@angular/core';

/**
 * No celular as linhas das tabelas viram cartões (ver styles.scss); cada célula precisa do rótulo da coluna.
 * Em vez de repetir o rótulo em cada template, copia o texto do cabeçalho para `data-label` das células,
 * acompanhando as mudanças da página (paginação, filtros, troca de aba).
 */
@Directive({ selector: '[tcTabelasResponsivas]' })
export class TabelasResponsivas implements AfterViewInit, OnDestroy {
  private readonly hospedeiro = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private observador?: MutationObserver;
  private quadro = 0;

  ngAfterViewInit(): void {
    this.observador = new MutationObserver(() => this.agendar());
    this.observador.observe(this.hospedeiro, { childList: true, subtree: true });
    this.agendar();
  }

  ngOnDestroy(): void {
    this.observador?.disconnect();
    cancelAnimationFrame(this.quadro);
  }

  private agendar(): void {
    cancelAnimationFrame(this.quadro);
    this.quadro = requestAnimationFrame(() => this.rotular());
  }

  private rotular(): void {
    for (const tabela of Array.from(this.hospedeiro.querySelectorAll('table'))) {
      const cabecalhos = Array.from(tabela.querySelectorAll('thead tr:first-child th, tr.mat-mdc-header-row th')).map((th) =>
        (th.textContent ?? '').trim(),
      );
      if (!cabecalhos.length) continue;
      for (const linha of Array.from(tabela.querySelectorAll('tbody tr, tr.mat-mdc-row'))) {
        Array.from(linha.children).forEach((celula, i) => {
          const rotulo = cabecalhos[i];
          if (rotulo && celula.getAttribute('data-label') !== rotulo) celula.setAttribute('data-label', rotulo);
          else if (!rotulo && celula.hasAttribute('data-label')) celula.removeAttribute('data-label');
        });
      }
    }
  }
}

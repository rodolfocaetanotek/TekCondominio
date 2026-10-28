import { AfterViewInit, Directive, ElementRef, OnDestroy, inject } from '@angular/core';

/**
 * No celular as linhas das tabelas viram cartões (ver styles.scss); cada célula precisa do rótulo da coluna.
 * Em vez de repetir o rótulo em cada template, copia o texto do cabeçalho para `data-label` das células,
 * acompanhando as mudanças da página (paginação, filtros, troca de aba).
 */
@Directive({ selector: '[tcResponsiveTables]' })
export class ResponsiveTables implements AfterViewInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  private observer?: MutationObserver;
  private frame = 0;

  ngAfterViewInit(): void {
    this.observer = new MutationObserver(() => this.schedule());
    this.observer.observe(this.host, { childList: true, subtree: true });
    this.schedule();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    cancelAnimationFrame(this.frame);
  }

  private schedule(): void {
    cancelAnimationFrame(this.frame);
    this.frame = requestAnimationFrame(() => this.label());
  }

  private label(): void {
    for (const table of Array.from(this.host.querySelectorAll('table'))) {
      const headers = Array.from(table.querySelectorAll('thead tr:first-child th, tr.mat-mdc-header-row th')).map((th) =>
        (th.textContent ?? '').trim(),
      );
      if (!headers.length) continue;
      for (const row of Array.from(table.querySelectorAll('tbody tr, tr.mat-mdc-row'))) {
        Array.from(row.children).forEach((cell, i) => {
          const label = headers[i];
          if (label && cell.getAttribute('data-label') !== label) cell.setAttribute('data-label', label);
          else if (!label && cell.hasAttribute('data-label')) cell.removeAttribute('data-label');
        });
      }
    }
  }
}

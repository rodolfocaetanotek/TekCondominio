import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ModoTema = 'system' | 'light' | 'dark';

const CHAVE = 'tc-theme';

/** Tema claro/escuro: segue o sistema operacional, salvo escolha do usuário (guardada só neste navegador). */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly raiz = inject(DOCUMENT).documentElement;
  readonly modo = signal<ModoTema>(ler());

  constructor() {
    this.aplicar(this.modo());
  }

  definir(modo: ModoTema): void {
    this.modo.set(modo);
    this.aplicar(modo);
    try {
      if (modo === 'system') localStorage.removeItem(CHAVE);
      else localStorage.setItem(CHAVE, modo);
    } catch {
      // armazenamento indisponível (janela privada): a escolha vale só nesta sessão
    }
  }

  private aplicar(modo: ModoTema): void {
    if (modo === 'system') this.raiz.removeAttribute('data-theme');
    else this.raiz.setAttribute('data-theme', modo);
  }
}

function ler(): ModoTema {
  try {
    const v = localStorage.getItem(CHAVE);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

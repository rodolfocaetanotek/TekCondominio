import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const KEY = 'tc-theme';

/** Tema claro/escuro: segue o sistema operacional, salvo escolha do usuário (guardada só neste navegador). */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly root = inject(DOCUMENT).documentElement;
  readonly mode = signal<ThemeMode>(read());

  constructor() {
    this.apply(this.mode());
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    this.apply(mode);
    try {
      if (mode === 'system') localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, mode);
    } catch {
      // armazenamento indisponível (janela privada): a escolha vale só nesta sessão
    }
  }

  private apply(mode: ThemeMode): void {
    if (mode === 'system') this.root.removeAttribute('data-theme');
    else this.root.setAttribute('data-theme', mode);
  }
}

function read(): ThemeMode {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

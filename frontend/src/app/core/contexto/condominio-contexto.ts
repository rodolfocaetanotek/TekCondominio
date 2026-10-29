import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Permissao } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';

export interface OpcaoCondominio {
  id: string;
  nome: string;
  situacao: 'ACTIVE' | 'INACTIVE';
  permissoes: Permissao[];
}

const STORAGE_KEY = 'tc.condominio';

/**
 * Condomínio em foco na interface (seletor da barra superior). As telas por condomínio usam esta seleção;
 * o backend continua validando cada acesso. A escolha é lembrada por navegador (preferência, não dado sensível).
 */
@Injectable({ providedIn: 'root' })
export class CondominiumContext {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly options = signal<OpcaoCondominio[]>([]);
  readonly loaded = signal(false);
  private readonly selectedId = signal<string | null>(readStored());

  readonly selected = computed(() => this.options().find((o) => o.id === this.selectedId()) ?? null);
  readonly id = computed(() => this.selected()?.id ?? null);

  constructor() {
    // Carrega ao entrar (membros de administradora) e limpa ao sair.
    effect(() => {
      const user = this.auth.user();
      untracked(() => {
        if (user?.idAdministradora) {
          this.reload();
        } else {
          this.options.set([]);
          this.loaded.set(false);
        }
      });
    });
  }

  reload(): void {
    this.http.get<OpcaoCondominio[]>('/api/v1/condominios/acessiveis').subscribe({
      next: (options) => {
        this.options.set(options);
        if (!options.some((o) => o.id === this.selectedId())) {
          const first = options.find((o) => o.situacao === 'ACTIVE') ?? options[0];
          this.select(first?.id ?? null);
        }
        this.loaded.set(true);
      },
      error: () => {
        this.options.set([]);
        this.loaded.set(true);
      },
    });
  }

  select(id: string | null): void {
    this.selectedId.set(id);
    try {
      if (id) localStorage.setItem(STORAGE_KEY, id);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // armazenamento indisponível: a seleção vale só para esta aba
    }
  }

  /** Permissão no condomínio selecionado (apenas para exibir/ocultar; o backend decide). */
  can(permission: Permissao): boolean {
    return this.selected()?.permissoes.includes(permission) ?? false;
  }

  /** Permissão em algum condomínio acessível. */
  canAny(permission: Permissao): boolean {
    return this.options().some((o) => o.permissoes.includes(permission));
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

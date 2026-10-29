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

const CHAVE_ARMAZENAMENTO = 'tc.condominio';

/**
 * Condomínio em foco na interface (seletor da barra superior). As telas por condomínio usam esta seleção;
 * o backend continua validando cada acesso. A escolha é lembrada por navegador (preferência, não dado sensível).
 */
@Injectable({ providedIn: 'root' })
export class CondominioContexto {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly opcoes = signal<OpcaoCondominio[]>([]);
  readonly carregado = signal(false);
  private readonly idSelecionado = signal<string | null>(lerArmazenado());

  readonly selecionado = computed(() => this.opcoes().find((o) => o.id === this.idSelecionado()) ?? null);
  readonly id = computed(() => this.selecionado()?.id ?? null);

  constructor() {
    // Carrega ao entrar (membros de administradora) e limpa ao sair.
    effect(() => {
      const usuario = this.auth.usuario();
      untracked(() => {
        if (usuario?.idAdministradora) {
          this.recarregar();
        } else {
          this.opcoes.set([]);
          this.carregado.set(false);
        }
      });
    });
  }

  recarregar(): void {
    this.http.get<OpcaoCondominio[]>('/api/v1/condominios/acessiveis').subscribe({
      next: (opcoes) => {
        this.opcoes.set(opcoes);
        if (!opcoes.some((o) => o.id === this.idSelecionado())) {
          const primeiro = opcoes.find((o) => o.situacao === 'ACTIVE') ?? opcoes[0];
          this.selecionar(primeiro?.id ?? null);
        }
        this.carregado.set(true);
      },
      error: () => {
        this.opcoes.set([]);
        this.carregado.set(true);
      },
    });
  }

  selecionar(id: string | null): void {
    this.idSelecionado.set(id);
    try {
      if (id) localStorage.setItem(CHAVE_ARMAZENAMENTO, id);
      else localStorage.removeItem(CHAVE_ARMAZENAMENTO);
    } catch {
      // armazenamento indisponível: a seleção vale só para esta aba
    }
  }

  /** Permissão no condomínio selecionado (apenas para exibir/ocultar; o backend decide). */
  pode(permissao: Permissao): boolean {
    return this.selecionado()?.permissoes.includes(permissao) ?? false;
  }

  /** Permissão em algum condomínio acessível. */
  podeAlgum(permissao: Permissao): boolean {
    return this.opcoes().some((o) => o.permissoes.includes(permissao));
  }
}

function lerArmazenado(): string | null {
  try {
    return localStorage.getItem(CHAVE_ARMAZENAMENTO);
  } catch {
    return null;
  }
}

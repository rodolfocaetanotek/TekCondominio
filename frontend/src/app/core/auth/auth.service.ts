import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { Perfil, Permissao, TokenResponse } from './auth.models';

/** Cabeçalho exigido pela API nos endpoints baseados em cookie (proteção CSRF). */
const CABECALHOS_CLIENTE = new HttpHeaders({ 'X-TekCondo-Client': 'web' });

/**
 * Sessão do usuário. O access token fica somente em memória (nunca em localStorage);
 * o refresh token é um cookie HttpOnly inacessível ao JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private renovacaoEmAndamento: Observable<string> | null = null;

  readonly usuario = signal<Perfil | null>(null);
  readonly autenticado = computed(() => this.usuario() !== null);
  readonly membroAdministradora = computed(() => !!this.usuario()?.idAdministradora);

  accessToken(): string | null {
    return this.token();
  }

  login(email: string, senha: string): Observable<Perfil> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, senha }).pipe(
      tap((res) => this.token.set(res.accessToken)),
      switchMap(() => this.carregarPerfil()),
    );
  }

  /** Tenta restaurar a sessão a partir do cookie de refresh (ex.: recarga da página). */
  restaurarSessao(): Promise<void> {
    return firstValueFrom(
      this.renovar().pipe(
        switchMap(() => this.carregarPerfil()),
        map(() => undefined),
        catchError(() => {
          this.limpar();
          return of(undefined);
        }),
      ),
    );
  }

  /** Renova o access token; chamadas simultâneas compartilham a mesma requisição. */
  renovar(): Observable<string> {
    if (!this.renovacaoEmAndamento) {
      this.renovacaoEmAndamento = this.http
        .post<TokenResponse>('/api/v1/auth/refresh', null, { headers: CABECALHOS_CLIENTE, withCredentials: true })
        .pipe(
          map((res) => res.accessToken),
          tap((token) => this.token.set(token)),
          finalize(() => (this.renovacaoEmAndamento = null)),
          shareReplay(1),
        );
    }
    return this.renovacaoEmAndamento;
  }

  logout(): void {
    this.http
      .post('/api/v1/auth/logout', null, { headers: CABECALHOS_CLIENTE, withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.encerrarSessao());
  }

  /** Encerra a sessão local e volta ao login (ex.: refresh expirado). */
  encerrarSessao(expirada = false): void {
    this.limpar();
    void this.router.navigate(['/login'], expirada ? { queryParams: { expirada: 1 } } : undefined);
  }

  /** Permissão válida para toda a organização. */
  pode(permissao: Permissao): boolean {
    return this.usuario()?.permissoesAdministradora.includes(permissao) ?? false;
  }

  /** Permissão em um condomínio específico (ou na organização inteira). */
  podeEm(permissao: Permissao, idCondominio: string): boolean {
    const perfil = this.usuario();
    if (!perfil) {
      return false;
    }
    return this.pode(permissao) || (perfil.permissoesCondominio[idCondominio]?.includes(permissao) ?? false);
  }

  /** Possui a permissão em pelo menos um condomínio. */
  podeAlgum(permissao: Permissao): boolean {
    const perfil = this.usuario();
    if (!perfil) {
      return false;
    }
    return this.pode(permissao) || Object.values(perfil.permissoesCondominio).some((p) => p.includes(permissao));
  }

  private carregarPerfil(): Observable<Perfil> {
    return this.http.get<Perfil>('/api/v1/auth/perfil').pipe(tap((perfil) => this.usuario.set(perfil)));
  }

  private limpar(): void {
    this.token.set(null);
    this.usuario.set(null);
  }
}

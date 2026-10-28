import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { Me, Permission, TokenResponse } from './auth.models';

/** Cabeçalho exigido pela API nos endpoints baseados em cookie (proteção CSRF). */
const CLIENT_HEADERS = new HttpHeaders({ 'X-TekCondo-Client': 'web' });

/**
 * Sessão do usuário. O access token fica somente em memória (nunca em localStorage);
 * o refresh token é um cookie HttpOnly inacessível ao JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly user = signal<Me | null>(null);
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly isOrganizationMember = computed(() => !!this.user()?.organizationId);

  accessToken(): string | null {
    return this.token();
  }

  login(email: string, password: string): Observable<Me> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password }).pipe(
      tap((res) => this.token.set(res.accessToken)),
      switchMap(() => this.loadProfile()),
    );
  }

  /** Tenta restaurar a sessão a partir do cookie de refresh (ex.: recarga da página). */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        switchMap(() => this.loadProfile()),
        map(() => undefined),
        catchError(() => {
          this.clear();
          return of(undefined);
        }),
      ),
    );
  }

  /** Renova o access token; chamadas simultâneas compartilham a mesma requisição. */
  refresh(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<TokenResponse>('/api/v1/auth/refresh', null, { headers: CLIENT_HEADERS, withCredentials: true })
        .pipe(
          map((res) => res.accessToken),
          tap((token) => this.token.set(token)),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  logout(): void {
    this.http
      .post('/api/v1/auth/logout', null, { headers: CLIENT_HEADERS, withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.endSession());
  }

  /** Encerra a sessão local e volta ao login (ex.: refresh expirado). */
  endSession(expired = false): void {
    this.clear();
    void this.router.navigate(['/login'], expired ? { queryParams: { expirada: 1 } } : undefined);
  }

  /** Permissão válida para toda a organização. */
  can(permission: Permission): boolean {
    return this.user()?.organizationPermissions.includes(permission) ?? false;
  }

  /** Permissão em um condomínio específico (ou na organização inteira). */
  canIn(permission: Permission, condominiumId: string): boolean {
    const me = this.user();
    if (!me) {
      return false;
    }
    return this.can(permission) || (me.condominiumPermissions[condominiumId]?.includes(permission) ?? false);
  }

  /** Possui a permissão em pelo menos um condomínio. */
  canAny(permission: Permission): boolean {
    const me = this.user();
    if (!me) {
      return false;
    }
    return this.can(permission) || Object.values(me.condominiumPermissions).some((p) => p.includes(permission));
  }

  private loadProfile(): Observable<Me> {
    return this.http.get<Me>('/api/v1/auth/me').pipe(tap((me) => this.user.set(me)));
  }

  private clear(): void {
    this.token.set(null);
    this.user.set(null);
  }
}

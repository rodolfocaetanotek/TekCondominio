import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface TokenSenhaInfo {
  finalidade: 'INVITE' | 'RESET';
  nome: string;
  email: string;
}

const BASE = '/api/v1/auth/senha';

@Injectable({ providedIn: 'root' })
export class SenhaService {
  private readonly http = inject(HttpClient);

  esqueciSenha(email: string): Observable<void> {
    return this.http.post<void>(`${BASE}/esqueci`, { email });
  }

  consultarToken(token: string): Observable<TokenSenhaInfo> {
    return this.http.post<TokenSenhaInfo>(`${BASE}/token`, { token });
  }

  redefinir(token: string, novaSenha: string): Observable<void> {
    return this.http.post<void>(`${BASE}/redefinir`, { token, novaSenha });
  }

  alterar(senhaAtual: string, novaSenha: string): Observable<void> {
    return this.http.post<void>(`${BASE}/alterar`, { senhaAtual, novaSenha }, { withCredentials: true });
  }
}

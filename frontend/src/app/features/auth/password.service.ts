import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface PasswordTokenInfo {
  purpose: 'INVITE' | 'RESET';
  name: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class PasswordService {
  private readonly http = inject(HttpClient);

  forgot(email: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password/forgot', { email });
  }

  describe(token: string): Observable<PasswordTokenInfo> {
    return this.http.post<PasswordTokenInfo>('/api/v1/auth/password/token', { token });
  }

  reset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password/reset', { token, newPassword });
  }

  change(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password/change', { currentPassword, newPassword }, { withCredentials: true });
  }
}

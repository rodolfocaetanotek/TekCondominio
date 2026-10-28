import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

export interface ViaCepAddress {
  street: string;
  district: string;
  city: string;
  state: string;
}

interface ViaCepResponse {
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean | string;
}

@Injectable({ providedIn: 'root' })
export class ViaCepService {
  private readonly http = inject(HttpClient);

  find(zipCode: string): Observable<ViaCepAddress | null> {
    const digits = zipCode.replace(/\D/g, '');
    if (digits.length !== 8) return of(null);
    return this.http.get<ViaCepResponse>(`https://viacep.com.br/ws/${digits}/json/`).pipe(
      map((r) =>
        r.erro
          ? null
          : { street: r.logradouro ?? '', district: r.bairro ?? '', city: r.localidade ?? '', state: r.uf ?? '' },
      ),
      catchError(() => of(null)),
    );
  }
}

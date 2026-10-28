import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, Signal, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { Observable, catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';

export interface ZipCodeAddress {
  zipCode: string;
  street: string | null;
  district: string | null;
  city: string | null;
  state: string | null;
}

@Injectable({ providedIn: 'root' })
export class ZipCodeService {
  private readonly http = inject(HttpClient);

  find(zipCode: string): Observable<ZipCodeAddress | null> {
    return this.http.get<ZipCodeAddress | null>(`/api/v1/zip-codes/${zipCode}`).pipe(catchError(() => of(null)));
  }
}

export interface ZipCodeLookup {
  searching: Signal<boolean>;
  notFound: Signal<boolean>;
}

/**
 * Preenche logradouro, bairro, cidade e UF do grupo de endereço quando o CEP digitado fica completo.
 * Chamar na criação do componente. Só reage a edição do usuário, então carregar um registro com reset() não
 * sobrescreve o endereço salvo.
 */
export function zipCodeLookup(address: FormGroup, focusAfter?: string): ZipCodeLookup {
  const service = inject(ZipCodeService);
  const document = inject(DOCUMENT);
  const searching = signal(false);
  const notFound = signal(false);
  const zipCode = address.get('zipCode')!;

  zipCode.valueChanges
    .pipe(
      filter(() => zipCode.dirty),
      map((value: string | null) => (value ?? '').replace(/\D/g, '')),
      distinctUntilChanged(),
      tap(() => notFound.set(false)),
      debounceTime(300),
      filter((digits) => digits.length === 8),
      tap(() => searching.set(true)),
      switchMap((digits) => service.find(digits).pipe(finalize(() => searching.set(false)))),
      takeUntilDestroyed(),
    )
    .subscribe((found) => {
      if (!found) {
        notFound.set(true);
        return;
      }
      const { street, district, city, state } = found;
      address.patchValue(Object.fromEntries(Object.entries({ street, district, city, state }).filter(([, v]) => v)));
      address.markAsDirty();
      if (focusAfter) document.getElementById(focusAfter)?.focus();
    });

  return { searching, notFound };
}

import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, Signal, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { Observable, catchError, debounceTime, distinctUntilChanged, filter, finalize, map, of, switchMap, tap } from 'rxjs';

export interface EnderecoCep {
  cep: string;
  logradouro: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
}

@Injectable({ providedIn: 'root' })
export class CepService {
  private readonly http = inject(HttpClient);

  buscar(cep: string): Observable<EnderecoCep | null> {
    return this.http.get<EnderecoCep | null>(`/api/v1/ceps/${cep}`).pipe(catchError(() => of(null)));
  }
}

export interface BuscaCep {
  buscando: Signal<boolean>;
  naoEncontrado: Signal<boolean>;
}

/**
 * Preenche logradouro, bairro, cidade e UF do grupo de endereço quando o CEP digitado fica completo.
 * Chamar na criação do componente. Só reage a edição do usuário, então carregar um registro com reset() não
 * sobrescreve o endereço salvo.
 */
export function buscaCep(endereco: FormGroup, focarDepois?: string): BuscaCep {
  const service = inject(CepService);
  const document = inject(DOCUMENT);
  const buscando = signal(false);
  const naoEncontrado = signal(false);
  const cep = endereco.get('cep')!;

  cep.valueChanges
    .pipe(
      filter(() => cep.dirty),
      map((valor: string | null) => (valor ?? '').replace(/\D/g, '')),
      distinctUntilChanged(),
      tap(() => naoEncontrado.set(false)),
      debounceTime(300),
      filter((digitos) => digitos.length === 8),
      tap(() => buscando.set(true)),
      switchMap((digitos) => service.buscar(digitos).pipe(finalize(() => buscando.set(false)))),
      takeUntilDestroyed(),
    )
    .subscribe((encontrado) => {
      if (!encontrado) {
        naoEncontrado.set(true);
        return;
      }
      const { logradouro, bairro, cidade, uf } = encontrado;
      endereco.patchValue(Object.fromEntries(Object.entries({ logradouro, bairro, cidade, uf }).filter(([, v]) => v)));
      endereco.markAsDirty();
      if (focarDepois) document.getElementById(focarDepois)?.focus();
    });

  return { buscando, naoEncontrado };
}

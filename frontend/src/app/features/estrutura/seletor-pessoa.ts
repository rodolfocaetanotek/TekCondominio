import { Component, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { DocumentoPipe } from '../../comum/formato/documento.pipe';
import { PessoaResumo } from './estrutura.models';
import { EstruturaService } from './estrutura.service';

/** Busca de pessoa por nome, e-mail ou CPF/CNPJ (autocomplete no servidor). */
@Component({
  selector: 'tc-seletor-pessoa',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule, MatIconModule, DocumentoPipe],
  template: `
    <mat-form-field class="full">
      <mat-label>{{ rotulo() }}</mat-label>
      <mat-icon matPrefix>person_search</mat-icon>
      <input matInput [formControl]="termo" [matAutocomplete]="autocompletar" placeholder="Nome, e-mail ou CPF/CNPJ" />
      <mat-autocomplete #autocompletar="matAutocomplete" [displayWith]="exibir" (optionSelected)="escolhido.emit($event.option.value)">
        @for (p of resultados(); track p.id) {
          <mat-option [value]="p">
            <span>{{ p.nome }}</span>
            <small class="tc-muted"> · {{ p.documento ? (p.documento | documento: (p.documento.length === 14 ? 'cnpj' : 'cpf')) : (p.email ?? 'sem documento') }}</small>
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `.full { width: 100%; }`,
})
export class SeletorPessoa {
  private readonly service = inject(EstruturaService);
  readonly rotulo = input('Pessoa');
  readonly escolhido = output<PessoaResumo>();
  protected readonly termo = new FormControl<string | PessoaResumo>('', { nonNullable: true });

  protected readonly resultados = toSignal(
    this.termo.valueChanges.pipe(
      startWith(''),
      map((v) => (typeof v === 'string' ? v.trim() : '')),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((busca) =>
        busca.length < 2
          ? of([])
          : this.service.pessoas({ busca, ativo: 'true', pagina: 0, tamanho: 10 }).pipe(
              map((p) => p.conteudo),
              catchError(() => of([])),
            ),
      ),
    ),
    { initialValue: [] as PessoaResumo[] },
  );

  exibir = (p: PessoaResumo | string | null): string => (p && typeof p === 'object' ? p.nome : (p ?? ''));
}

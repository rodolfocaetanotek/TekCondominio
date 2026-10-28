import { Component, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { DocPipe } from '../../shared/format/doc.pipe';
import { PersonSummary } from './structure.models';
import { StructureService } from './structure.service';

/** Busca de pessoa por nome, e-mail ou CPF/CNPJ (autocomplete no servidor). */
@Component({
  selector: 'tc-person-picker',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule, MatIconModule, DocPipe],
  template: `
    <mat-form-field class="full">
      <mat-label>{{ label() }}</mat-label>
      <mat-icon matPrefix>person_search</mat-icon>
      <input matInput [formControl]="term" [matAutocomplete]="auto" placeholder="Nome, e-mail ou CPF/CNPJ" />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="display" (optionSelected)="picked.emit($event.option.value)">
        @for (p of results(); track p.id) {
          <mat-option [value]="p">
            <span>{{ p.name }}</span>
            <small class="tc-muted"> · {{ p.document ? (p.document | doc: (p.document.length === 14 ? 'cnpj' : 'cpf')) : (p.email ?? 'sem documento') }}</small>
          </mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `.full { width: 100%; }`,
})
export class PersonPicker {
  private readonly service = inject(StructureService);
  readonly label = input('Pessoa');
  readonly picked = output<PersonSummary>();
  protected readonly term = new FormControl<string | PersonSummary>('', { nonNullable: true });

  protected readonly results = toSignal(
    this.term.valueChanges.pipe(
      startWith(''),
      map((v) => (typeof v === 'string' ? v.trim() : '')),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((search) =>
        search.length < 2
          ? of([])
          : this.service.people({ search, active: 'true', page: 0, size: 10 }).pipe(
              map((p) => p.conteudo),
              catchError(() => of([])),
            ),
      ),
    ),
    { initialValue: [] as PersonSummary[] },
  );

  display = (p: PersonSummary | string | null): string => (p && typeof p === 'object' ? p.name : (p ?? ''));
}

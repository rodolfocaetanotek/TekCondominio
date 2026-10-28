import { Component, computed, effect, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { FieldError } from '../../shared/forms/field-error';
import { CondominiumService } from '../condominiums/condominium.service';
import { PapelOpcao } from './user.models';
import { UserService } from './user.service';

/**
 * Seleção de papel + condomínio. O campo de condomínio só aparece (e só é obrigatório) para papéis de escopo
 * CONDOMINIUM. A regra de escopo é decidida pelo backend; aqui apenas orientamos o preenchimento.
 */
@Component({
  selector: 'tc-role-fields',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, FieldError],
  template: `
    <div class="tc-grid">
      <mat-form-field [class.span-6]="needsCondominium()">
        <mat-label>Papel</mat-label>
        <mat-select [formControl]="roleCode()" required>
          <mat-optgroup label="Toda a administradora">
            @for (r of orgRoles(); track r.codigo) {
              <mat-option [value]="r.codigo">{{ r.nome }}</mat-option>
            }
          </mat-optgroup>
          <mat-optgroup label="Por condomínio">
            @for (r of condoRoles(); track r.codigo) {
              <mat-option [value]="r.codigo">{{ r.nome }}</mat-option>
            }
          </mat-optgroup>
        </mat-select>
        @if (selectedRole()?.descricao; as d) {
          <mat-hint>{{ d }}</mat-hint>
        }
        <mat-error><tc-field-error [control]="roleCode()" /></mat-error>
      </mat-form-field>

      @if (needsCondominium()) {
        <mat-form-field class="span-6">
          <mat-label>Condomínio</mat-label>
          <mat-select [formControl]="condominiumId()" required>
            @for (c of condominiums(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            } @empty {
              <mat-option disabled>Nenhum condomínio ativo</mat-option>
            }
          </mat-select>
          <mat-error><tc-field-error [control]="condominiumId()" /></mat-error>
        </mat-form-field>
      }
    </div>
  `,
})
export class RoleFields {
  readonly roleCode = input.required<FormControl<string>>();
  readonly condominiumId = input.required<FormControl<string | null>>();

  private readonly roles = toSignal(inject(UserService).roles().pipe(catchError(() => of<PapelOpcao[]>([]))), {
    initialValue: [] as PapelOpcao[],
  });
  protected readonly condominiums = toSignal(
    inject(CondominiumService)
      .list({ page: 0, size: 100, status: 'ACTIVE', sort: 'name,asc' })
      .pipe(
        map((p) => p.conteudo),
        catchError(() => of([])),
      ),
    { initialValue: [] },
  );

  private readonly roleValue = toSignal(
    toObservable(this.roleCode).pipe(switchMap((c) => c.valueChanges.pipe(startWith(c.value)))),
    { initialValue: '' },
  );

  protected readonly orgRoles = computed(() => this.roles().filter((r) => r.escopo === 'ORGANIZATION'));
  protected readonly condoRoles = computed(() => this.roles().filter((r) => r.escopo === 'CONDOMINIUM'));
  protected readonly selectedRole = computed(() => this.roles().find((r) => r.codigo === this.roleValue()));
  protected readonly needsCondominium = computed(() => this.selectedRole()?.escopo === 'CONDOMINIUM');

  constructor() {
    effect(() => {
      const control = this.condominiumId();
      if (this.needsCondominium()) {
        control.setValidators(Validators.required);
      } else {
        control.clearValidators();
        control.setValue(null, { emitEvent: false });
      }
      control.updateValueAndValidity({ emitEvent: false });
    });
  }
}

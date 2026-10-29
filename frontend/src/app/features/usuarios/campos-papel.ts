import { Component, computed, effect, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { CondominioService } from '../condominios/condominio.service';
import { PapelOpcao } from './usuario.models';
import { UsuarioService } from './usuario.service';

/**
 * Seleção de papel + condomínio. O campo de condomínio só aparece (e só é obrigatório) para papéis de escopo
 * CONDOMINIUM. A regra de escopo é decidida pelo backend; aqui apenas orientamos o preenchimento.
 */
@Component({
  selector: 'tc-campos-papel',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MensagemErroCampo],
  template: `
    <div class="tc-grid">
      <mat-form-field [class.span-6]="exigeCondominio()">
        <mat-label>Papel</mat-label>
        <mat-select [formControl]="codigoPapel()" required>
          <mat-optgroup label="Toda a administradora">
            @for (r of papeisAdministradora(); track r.codigo) {
              <mat-option [value]="r.codigo">{{ r.nome }}</mat-option>
            }
          </mat-optgroup>
          <mat-optgroup label="Por condomínio">
            @for (r of papeisCondominio(); track r.codigo) {
              <mat-option [value]="r.codigo">{{ r.nome }}</mat-option>
            }
          </mat-optgroup>
        </mat-select>
        @if (papelSelecionado()?.descricao; as d) {
          <mat-hint>{{ d }}</mat-hint>
        }
        <mat-error><tc-erro-campo [controle]="codigoPapel()" /></mat-error>
      </mat-form-field>

      @if (exigeCondominio()) {
        <mat-form-field class="span-6">
          <mat-label>Condomínio</mat-label>
          <mat-select [formControl]="idCondominio()" required>
            @for (c of condominios(); track c.id) {
              <mat-option [value]="c.id">{{ c.nome }}</mat-option>
            } @empty {
              <mat-option disabled>Nenhum condomínio ativo</mat-option>
            }
          </mat-select>
          <mat-error><tc-erro-campo [controle]="idCondominio()" /></mat-error>
        </mat-form-field>
      }
    </div>
  `,
})
export class CamposPapel {
  readonly codigoPapel = input.required<FormControl<string>>();
  readonly idCondominio = input.required<FormControl<string | null>>();

  private readonly papeis = toSignal(inject(UsuarioService).papeis().pipe(catchError(() => of<PapelOpcao[]>([]))), {
    initialValue: [] as PapelOpcao[],
  });
  protected readonly condominios = toSignal(
    inject(CondominioService)
      .listar({ pagina: 0, tamanho: 100, situacao: 'ACTIVE', ordem: 'nome,asc' })
      .pipe(
        map((p) => p.conteudo),
        catchError(() => of([])),
      ),
    { initialValue: [] },
  );

  private readonly valorPapel = toSignal(
    toObservable(this.codigoPapel).pipe(switchMap((c) => c.valueChanges.pipe(startWith(c.value)))),
    { initialValue: '' },
  );

  protected readonly papeisAdministradora = computed(() => this.papeis().filter((r) => r.escopo === 'ORGANIZATION'));
  protected readonly papeisCondominio = computed(() => this.papeis().filter((r) => r.escopo === 'CONDOMINIUM'));
  protected readonly papelSelecionado = computed(() => this.papeis().find((r) => r.codigo === this.valorPapel()));
  protected readonly exigeCondominio = computed(() => this.papelSelecionado()?.escopo === 'CONDOMINIUM');

  constructor() {
    effect(() => {
      const controle = this.idCondominio();
      if (this.exigeCondominio()) {
        controle.setValidators(Validators.required);
      } else {
        controle.clearValidators();
        controle.setValue(null, { emitEvent: false });
      }
      controle.updateValueAndValidity({ emitEvent: false });
    });
  }
}

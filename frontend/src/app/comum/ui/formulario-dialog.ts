import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { TipoMascara } from '../formato/formato-br';
import { MascaraDirective } from '../formato/mascara.directive';
import { MensagemErroCampo } from '../formularios/erro-campo';
import { aplicarErrosServidor } from '../formularios/erros-servidor';
import { FecharDialog } from './fechar-dialog';

export interface DefinicaoCampo {
  chave: string;
  rotulo: string;
  tipo?: 'text' | 'number' | 'date' | 'time' | 'datetime' | 'select' | 'textarea' | 'checkbox' | 'email';
  obrigatorio?: boolean;
  opcoes?: { valor: string | null; rotulo: string }[];
  valor?: unknown;
  dica?: string;
  colunas?: 3 | 4 | 6 | 8 | 12;
  mascara?: TipoMascara;
  tamanhoMaximo?: number;
  min?: number;
}

export interface DadosFormularioDialog<T> {
  titulo: string;
  campos: DefinicaoCampo[];
  rotuloEnviar?: string;
  introducao?: string;
  enviar: (valores: Record<string, unknown>) => Observable<T>;
}

/**
 * Formulário declarativo em diálogo. Os valores vazios são enviados como null; erros de validação da API
 * (problem+json) aparecem no campo correspondente. Fecha com o resultado da chamada.
 */
@Component({
  selector: 'tc-formulario-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MascaraDirective, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>
      {{ data.titulo }} <tc-fechar-dialog />
    </h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (data.introducao) {
          <p class="tc-muted">{{ data.introducao }}</p>
        }
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <div class="tc-grid">
          @for (f of data.campos; track f.chave) {
            @switch (f.tipo ?? 'text') {
              @case ('checkbox') {
                <mat-checkbox [formControlName]="f.chave" class="span-12">{{ f.rotulo }}</mat-checkbox>
              }
              @case ('select') {
                <mat-form-field [class]="'span-' + (f.colunas ?? 12)">
                  <mat-label>{{ f.rotulo }}</mat-label>
                  <mat-select [formControlName]="f.chave" [required]="!!f.obrigatorio">
                    @for (o of f.opcoes ?? []; track o.valor) {
                      <mat-option [value]="o.valor">{{ o.rotulo }}</mat-option>
                    }
                  </mat-select>
                  @if (f.dica) { <mat-hint>{{ f.dica }}</mat-hint> }
                  <mat-error><tc-erro-campo [controle]="form.get(f.chave)" /></mat-error>
                </mat-form-field>
              }
              @case ('textarea') {
                <mat-form-field [class]="'span-' + (f.colunas ?? 12)">
                  <mat-label>{{ f.rotulo }}</mat-label>
                  <textarea matInput [formControlName]="f.chave" rows="4" [required]="!!f.obrigatorio" [attr.maxlength]="f.tamanhoMaximo ?? null"></textarea>
                  @if (f.dica) { <mat-hint>{{ f.dica }}</mat-hint> }
                  <mat-error><tc-erro-campo [controle]="form.get(f.chave)" /></mat-error>
                </mat-form-field>
              }
              @default {
                <mat-form-field [class]="'span-' + (f.colunas ?? 12)">
                  <mat-label>{{ f.rotulo }}</mat-label>
                  @if (f.mascara) {
                    <input matInput [formControlName]="f.chave" [tcMascara]="f.mascara" [required]="!!f.obrigatorio" />
                  } @else {
                    <input matInput [formControlName]="f.chave" [type]="tipoInput(f)" [required]="!!f.obrigatorio"
                      [attr.step]="f.tipo === 'number' ? '0.01' : null" [attr.min]="f.min ?? null" [attr.maxlength]="f.tamanhoMaximo ?? null" />
                  }
                  @if (f.dica) { <mat-hint>{{ f.dica }}</mat-hint> }
                  <mat-error><tc-erro-campo [controle]="form.get(f.chave)" /></mat-error>
                </mat-form-field>
              }
            }
          }
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : (data.rotuloEnviar ?? 'Salvar') }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class FormularioDialog<T> {
  protected readonly data = inject<DadosFormularioDialog<T>>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<FormularioDialog<T>, T>);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly form = new FormGroup<Record<string, FormControl>>({});

  constructor() {
    for (const f of this.data.campos) {
      const validadores: ValidatorFn[] = [];
      if (f.obrigatorio && f.tipo !== 'checkbox') validadores.push(Validators.required);
      if (f.tipo === 'email') validadores.push(Validators.email);
      const inicial = f.valor ?? (f.tipo === 'checkbox' ? false : null);
      this.form.addControl(f.chave, new FormControl(inicial, validadores));
    }
  }

  tipoInput(f: DefinicaoCampo): string {
    return f.tipo === 'datetime' ? 'datetime-local' : (f.tipo ?? 'text');
  }

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const valores: Record<string, unknown> = {};
    for (const f of this.data.campos) {
      let v = this.form.controls[f.chave].value as unknown;
      if (typeof v === 'string') v = v.trim() === '' ? null : v.trim();
      if (f.tipo === 'datetime' && typeof v === 'string') v = new Date(v).toISOString();
      valores[f.chave] = v;
    }
    this.data.enviar(valores).subscribe({
      next: (resultado) => this.ref.close(resultado),
      error: (err: unknown) => {
        this.salvando.set(false);
        const p = paraProblema(err);
        const restante = p.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, p) : [p.detail];
        if (restante.length) this.erro.set(restante.join(' '));
      },
    });
  }
}

/** Atalho: abre o formulário e devolve o resultado (ou undefined se cancelado). */
export function abrirFormulario<T>(dialog: MatDialog, data: DadosFormularioDialog<T>, largura = '560px'): Observable<T | undefined> {
  return dialog.open<FormularioDialog<T>, DadosFormularioDialog<T>, T>(FormularioDialog, { data, width: largura, maxWidth: '95vw' }).afterClosed();
}

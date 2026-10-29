import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { toProblem } from '../../core/api/api.models';
import { formatCpfCnpj, formatPhone } from '../../comum/formato/formato-br';
import { MaskDirective } from '../../comum/formato/mascara.directive';
import { FieldError } from '../../comum/formularios/erro-campo';
import { applyServerErrors } from '../../comum/formularios/erros-servidor';
import { PessoaDetalhe, TipoPessoa } from './estrutura.models';
import { StructureService } from './estrutura.service';
import { DialogClose } from '../../comum/ui/fechar-dialog';

@Component({
  selector: 'tc-person-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MaskDirective, FieldError],
  template: `
    <h2 mat-dialog-title>{{ data.person ? 'Editar pessoa' : 'Nova pessoa' }} <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-8">
            <mat-label>Nome / razão social</mat-label>
            <input matInput formControlName="nome" required maxlength="150" cdkFocusInitial />
            <mat-error><tc-field-error [control]="form.controls.nome" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-4">
            <mat-label>Tipo</mat-label>
            <mat-select formControlName="tipo">
              <mat-option value="INDIVIDUAL">Pessoa física</mat-option>
              <mat-option value="COMPANY">Pessoa jurídica</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>{{ form.controls.tipo.value === 'COMPANY' ? 'CNPJ' : 'CPF' }}</mat-label>
            <input matInput formControlName="documento" tcMask="cpfCnpj" class="mono" />
            <mat-error><tc-field-error [control]="form.controls.documento" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" />
            <mat-hint>Necessário para acesso ao portal e avisos</mat-hint>
            <mat-error><tc-field-error [control]="form.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone</mat-label>
            <input matInput type="tel" formControlName="telefone" tcMask="phone" />
            <mat-error><tc-field-error [control]="form.controls.telefone" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone alternativo</mat-label>
            <input matInput type="tel" formControlName="telefoneSecundario" tcMask="phone" />
            <mat-error><tc-field-error [control]="form.controls.telefoneSecundario" /></mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Observações</mat-label>
            <textarea matInput formControlName="observacoes" rows="2" maxlength="500"></textarea>
          </mat-form-field>
        </div>
        <p class="tc-muted small">Colete somente os dados necessários à administração do condomínio (LGPD).</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Salvando…' : 'Salvar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.small { font: var(--mat-sys-body-small); }`,
})
export class PersonDialog {
  protected readonly data = inject<{ person?: PessoaDetalhe }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PersonDialog, PessoaDetalhe>);
  private readonly service = inject(StructureService);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly p = this.data.person;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: [this.p?.nome ?? '', [Validators.required, Validators.maxLength(150)]],
    tipo: [this.p?.tipo ?? ('INDIVIDUAL' as TipoPessoa)],
    documento: [formatCpfCnpj(this.p?.documento)],
    email: [this.p?.email ?? '', [Validators.email]],
    telefone: [formatPhone(this.p?.telefone)],
    telefoneSecundario: [formatPhone(this.p?.telefoneSecundario)],
    observacoes: [this.p?.observacoes ?? ''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const blank = (s: string) => s.trim() || null;
    this.service
      .savePerson(
        { nome: v.nome.trim(), tipo: v.tipo, documento: blank(v.documento), email: blank(v.email), telefone: blank(v.telefone),
          telefoneSecundario: blank(v.telefoneSecundario), observacoes: blank(v.observacoes), versao: this.p?.versao },
        this.p?.id,
      )
      .subscribe({
        next: (person) => this.ref.close(person),
        error: (err: unknown) => {
          this.saving.set(false);
          const problem = toProblem(err);
          if (problem.codigo === 'PERSON_DOCUMENT_TAKEN' || problem.codigo === 'DOCUMENT_KIND_MISMATCH') {
            this.form.controls.documento.setErrors({ server: problem.detail });
            return;
          }
          const rest = problem.codigo === 'VALIDATION_ERROR' ? applyServerErrors(this.form, problem) : [problem.detail];
          if (rest.length) this.error.set(rest.join(' '));
        },
      });
  }
}

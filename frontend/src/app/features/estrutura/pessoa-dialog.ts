import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { paraProblema } from '../../core/api/api.models';
import { formatarCpfCnpj, formatarTelefone } from '../../comum/formato/formato-br';
import { MascaraDirective } from '../../comum/formato/mascara.directive';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { PessoaDetalhe, TipoPessoa } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

@Component({
  selector: 'tc-pessoa-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MascaraDirective, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>{{ data.pessoa ? 'Editar pessoa' : 'Nova pessoa' }} <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-8">
            <mat-label>Nome / razão social</mat-label>
            <input matInput formControlName="nome" required maxlength="150" cdkFocusInitial />
            <mat-error><tc-erro-campo [controle]="form.controls.nome" /></mat-error>
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
            <input matInput formControlName="documento" tcMascara="cpfCnpj" class="mono" />
            <mat-error><tc-erro-campo [controle]="form.controls.documento" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" />
            <mat-hint>Necessário para acesso ao portal e avisos</mat-hint>
            <mat-error><tc-erro-campo [controle]="form.controls.email" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone</mat-label>
            <input matInput type="tel" formControlName="telefone" tcMascara="telefone" />
            <mat-error><tc-erro-campo [controle]="form.controls.telefone" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Telefone alternativo</mat-label>
            <input matInput type="tel" formControlName="telefoneSecundario" tcMascara="telefone" />
            <mat-error><tc-erro-campo [controle]="form.controls.telefoneSecundario" /></mat-error>
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
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Salvar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.small { font: var(--mat-sys-body-small); }`,
})
export class PessoaDialog {
  protected readonly data = inject<{ pessoa?: PessoaDetalhe }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PessoaDialog, PessoaDetalhe>);
  private readonly service = inject(EstruturaService);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  private readonly p = this.data.pessoa;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: [this.p?.nome ?? '', [Validators.required, Validators.maxLength(150)]],
    tipo: [this.p?.tipo ?? ('INDIVIDUAL' as TipoPessoa)],
    documento: [formatarCpfCnpj(this.p?.documento)],
    email: [this.p?.email ?? '', [Validators.email]],
    telefone: [formatarTelefone(this.p?.telefone)],
    telefoneSecundario: [formatarTelefone(this.p?.telefoneSecundario)],
    observacoes: [this.p?.observacoes ?? ''],
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    const vazio = (s: string) => s.trim() || null;
    this.service
      .salvarPessoa(
        { nome: v.nome.trim(), tipo: v.tipo, documento: vazio(v.documento), email: vazio(v.email), telefone: vazio(v.telefone),
          telefoneSecundario: vazio(v.telefoneSecundario), observacoes: vazio(v.observacoes), versao: this.p?.versao },
        this.p?.id,
      )
      .subscribe({
        next: (pessoa) => this.ref.close(pessoa),
        error: (err: unknown) => {
          this.salvando.set(false);
          const problema = paraProblema(err);
          if (problema.codigo === 'PERSON_DOCUMENT_TAKEN' || problema.codigo === 'DOCUMENT_KIND_MISMATCH') {
            this.form.controls.documento.setErrors({ server: problema.detail });
            return;
          }
          const restante = problema.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, problema) : [problema.detail];
          if (restante.length) this.erro.set(restante.join(' '));
        },
      });
  }
}

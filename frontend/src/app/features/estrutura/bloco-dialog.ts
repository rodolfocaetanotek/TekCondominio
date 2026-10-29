import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { Bloco } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

@Component({
  selector: 'tc-bloco-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>{{ data.bloco ? 'Editar bloco' : 'Novo bloco/torre' }} <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <mat-form-field class="full">
          <mat-label>Nome</mat-label>
          <input matInput formControlName="nome" required maxlength="80" placeholder="Ex.: Bloco A, Torre Norte" cdkFocusInitial />
          <mat-error><tc-erro-campo [controle]="form.controls.nome" /></mat-error>
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Descrição</mat-label>
          <input matInput formControlName="descricao" maxlength="255" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.full { width: 100%; }`,
})
export class BlocoDialog {
  protected readonly data = inject<{ idCondominio: string; bloco?: Bloco }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<BlocoDialog, Bloco>);
  private readonly service = inject(EstruturaService);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: [this.data.bloco?.nome ?? '', [Validators.required, Validators.maxLength(80)]],
    descricao: [this.data.bloco?.descricao ?? ''],
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    const v = this.form.getRawValue();
    this.service
      .salvarBloco(this.data.idCondominio, { nome: v.nome.trim(), descricao: v.descricao.trim() || null }, this.data.bloco?.id)
      .subscribe({
        next: (b) => this.ref.close(b),
        error: (err: unknown) => {
          this.salvando.set(false);
          const p = paraProblema(err);
          if (p.codigo === 'BUILDING_NAME_TAKEN') this.form.controls.nome.setErrors({ server: p.detail });
          else this.erro.set(p.detail);
        },
      });
  }
}

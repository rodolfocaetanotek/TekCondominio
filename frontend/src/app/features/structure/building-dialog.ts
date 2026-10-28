import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { toProblem } from '../../core/api/api.models';
import { FieldError } from '../../shared/forms/field-error';
import { Bloco } from './structure.models';
import { StructureService } from './structure.service';
import { DialogClose } from '../../shared/ui/dialog-close';

@Component({
  selector: 'tc-building-dialog',
  imports: [DialogClose, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FieldError],
  template: `
    <h2 mat-dialog-title>{{ data.building ? 'Editar bloco' : 'Novo bloco/torre' }} <tc-dialog-close /></h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        @if (error(); as message) {
          <p class="tc-alert" role="alert">{{ message }}</p>
        }
        <mat-form-field class="full">
          <mat-label>Nome</mat-label>
          <input matInput formControlName="nome" required maxlength="80" placeholder="Ex.: Bloco A, Torre Norte" cdkFocusInitial />
          <mat-error><tc-field-error [control]="form.controls.nome" /></mat-error>
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Descrição</mat-label>
          <input matInput formControlName="descricao" maxlength="255" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Salvar</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.full { width: 100%; }`,
})
export class BuildingDialog {
  protected readonly data = inject<{ condominiumId: string; building?: Bloco }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<BuildingDialog, Bloco>);
  private readonly service = inject(StructureService);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: [this.data.building?.nome ?? '', [Validators.required, Validators.maxLength(80)]],
    descricao: [this.data.building?.descricao ?? ''],
  });

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.service
      .saveBuilding(this.data.condominiumId, { nome: v.nome.trim(), descricao: v.descricao.trim() || null }, this.data.building?.id)
      .subscribe({
        next: (b) => this.ref.close(b),
        error: (err: unknown) => {
          this.saving.set(false);
          const p = toProblem(err);
          if (p.codigo === 'BUILDING_NAME_TAKEN') this.form.controls.nome.setErrors({ server: p.detail });
          else this.error.set(p.detail);
        },
      });
  }
}

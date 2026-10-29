import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { Bloco, TipoUnidade, ROTULOS_TIPO_UNIDADE, Unidade } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

export interface DadosUnidadeDialog {
  idCondominio: string;
  blocos: Bloco[];
  unidade?: Unidade;
}

@Component({
  selector: 'tc-unidade-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>{{ data.unidade ? 'Editar unidade' : 'Nova unidade' }} <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Bloco/torre</mat-label>
            <mat-select formControlName="idBloco">
              <mat-option [value]="null">Sem bloco</mat-option>
              @for (b of blocosAtivos; track b.id) {
                <mat-option [value]="b.id">{{ b.nome }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Identificação</mat-label>
            <input matInput formControlName="identificacao" required maxlength="30" placeholder="Ex.: 101, Casa 12, Loja 3" cdkFocusInitial />
            <mat-error><tc-erro-campo [controle]="form.controls.identificacao" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Tipo</mat-label>
            <mat-select formControlName="tipo" required>
              @for (t of tipos; track t[0]) {
                <mat-option [value]="t[0]">{{ t[1] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Andar</mat-label>
            <input matInput formControlName="andar" maxlength="10" />
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Fração ideal (%)</mat-label>
            <input matInput type="number" step="0.000001" min="0" max="100" formControlName="fracaoIdeal" />
            <mat-hint>Usada no rateio por fração ideal</mat-hint>
            <mat-error><tc-erro-campo [controle]="form.controls.fracaoIdeal" /></mat-error>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Área privativa (m²)</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="area" />
            <mat-error><tc-erro-campo [controle]="form.controls.area" /></mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Observações</mat-label>
            <textarea matInput formControlName="observacoes" rows="2" maxlength="500"></textarea>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">{{ salvando() ? 'Salvando…' : 'Salvar' }}</button>
      </mat-dialog-actions>
    </form>
  `,
})
export class UnidadeDialog {
  protected readonly data = inject<DadosUnidadeDialog>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<UnidadeDialog, Unidade>);
  private readonly service = inject(EstruturaService);

  protected readonly tipos = Object.entries(ROTULOS_TIPO_UNIDADE) as [TipoUnidade, string][];
  protected readonly blocosAtivos = this.data.blocos.filter((b) => b.ativo || b.id === this.data.unidade?.idBloco);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    idBloco: [this.data.unidade?.idBloco ?? (null as string | null)],
    identificacao: [this.data.unidade?.identificacao ?? '', [Validators.required, Validators.maxLength(30)]],
    tipo: [this.data.unidade?.tipo ?? ('APARTMENT' as TipoUnidade), Validators.required],
    andar: [this.data.unidade?.andar ?? ''],
    fracaoIdeal: [this.data.unidade?.fracaoIdeal ?? (null as number | null)],
    area: [this.data.unidade?.area ?? (null as number | null)],
    observacoes: [this.data.unidade?.observacoes ?? ''],
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    this.service
      .salvarUnidade(
        this.data.idCondominio,
        {
          idBloco: v.idBloco ?? null,
          identificacao: (v.identificacao ?? '').trim(),
          tipo: v.tipo ?? 'APARTMENT',
          andar: v.andar?.trim() || null,
          fracaoIdeal: v.fracaoIdeal ?? null,
          area: v.area ?? null,
          observacoes: v.observacoes?.trim() || null,
          versao: this.data.unidade?.versao,
        },
        this.data.unidade?.id,
      )
      .subscribe({
        next: (u) => this.ref.close(u),
        error: (err: unknown) => {
          this.salvando.set(false);
          const p = paraProblema(err);
          const restante = p.codigo === 'VALIDATION_ERROR' ? aplicarErrosServidor(this.form, p) : [p.detail];
          if (restante.length) this.erro.set(restante.join(' '));
        },
      });
  }
}

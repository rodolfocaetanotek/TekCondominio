import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { paraProblema } from '../../core/api/api.models';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { hojeIso } from '../../comum/ui/datas';
import { SeletorPessoa } from './seletor-pessoa';
import { ROTULOS_TIPO_VINCULO, PessoaResumo, TipoVinculo, Vinculo } from './estrutura.models';
import { EstruturaService } from './estrutura.service';
import { FecharDialog } from '../../comum/ui/fechar-dialog';

/** Vincular pessoa a uma unidade. */
@Component({
  selector: 'tc-vinculo-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MensagemErroCampo, SeletorPessoa],
  template: `
    <h2 mat-dialog-title>Vincular pessoa · {{ data.rotuloUnidade }} <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <tc-seletor-pessoa rotulo="Pessoa" (escolhido)="pessoa.set($event)" />
        @if (!pessoa()) {
          <p class="tc-muted small">Não encontrou? Cadastre a pessoa no menu <strong>Pessoas</strong> e volte aqui.</p>
        }
        <div class="tc-grid">
          <mat-form-field class="span-6">
            <mat-label>Vínculo</mat-label>
            <mat-select formControlName="tipo">
              @for (r of papeis; track r[0]) {
                <mat-option [value]="r[0]">{{ r[1] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="span-6">
            <mat-label>Início</mat-label>
            <input matInput type="date" formControlName="dataInicio" required />
            <mat-error><tc-erro-campo [controle]="form.controls.dataInicio" /></mat-error>
          </mat-form-field>
        </div>
        <mat-checkbox formControlName="responsavelFinanceiro">Responsável financeiro (recebe as cobranças da unidade)</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando() || !pessoa()">Vincular</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.small { font: var(--mat-sys-body-small); margin-top: -8px; }`,
})
export class VinculoDialog {
  protected readonly data = inject<{ idCondominio: string; idUnidade: string; rotuloUnidade: string; temFinanceiro: boolean }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<VinculoDialog, Vinculo>);
  private readonly service = inject(EstruturaService);
  protected readonly papeis = Object.entries(ROTULOS_TIPO_VINCULO) as [TipoVinculo, string][];
  protected readonly pessoa = signal<PessoaResumo | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    tipo: ['OWNER' as TipoVinculo],
    dataInicio: [hojeIso(), Validators.required],
    responsavelFinanceiro: [!this.data.temFinanceiro],
  });

  salvar(): void {
    const pessoa = this.pessoa();
    this.form.markAllAsTouched();
    if (!pessoa || this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    this.erro.set(null);
    const v = this.form.getRawValue();
    this.service
      .adicionarVinculo(this.data.idCondominio, { idUnidade: this.data.idUnidade, idPessoa: pessoa.id, ...v })
      .subscribe({
        next: (o) => this.ref.close(o),
        error: (err: unknown) => {
          this.salvando.set(false);
          this.erro.set(paraProblema(err).detail);
        },
      });
  }
}

/** Encerrar vínculo (o histórico é mantido). */
@Component({
  selector: 'tc-encerrar-vinculo-dialog',
  imports: [FecharDialog, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MensagemErroCampo],
  template: `
    <h2 mat-dialog-title>Encerrar vínculo <tc-fechar-dialog /></h2>
    <form [formGroup]="form" (ngSubmit)="salvar()" novalidate>
      <mat-dialog-content>
        <p>{{ data.vinculo.nomePessoa }} — {{ data.vinculo.rotuloUnidade }}</p>
        @if (erro(); as mensagem) {
          <p class="tc-alert" role="alert">{{ mensagem }}</p>
        }
        <mat-form-field class="full">
          <mat-label>Data de encerramento</mat-label>
          <input matInput type="date" formControlName="dataFim" required />
          <mat-error><tc-erro-campo [controle]="form.controls.dataFim" /></mat-error>
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Motivo</mat-label>
          <input matInput formControlName="motivo" maxlength="255" placeholder="Ex.: venda do imóvel, fim do contrato de locação" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="salvando()">Encerrar</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `.full { width: 100%; }`,
})
export class EncerrarVinculoDialog {
  protected readonly data = inject<{ idCondominio: string; vinculo: Vinculo }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<EncerrarVinculoDialog, Vinculo>);
  private readonly service = inject(EstruturaService);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    dataFim: [hojeIso(), Validators.required],
    motivo: [''],
  });

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando()) return;
    this.salvando.set(true);
    const v = this.form.getRawValue();
    this.service.encerrarVinculo(this.data.idCondominio, this.data.vinculo.id, v.dataFim, v.motivo.trim() || null).subscribe({
      next: (o) => this.ref.close(o),
      error: (err: unknown) => {
        this.salvando.set(false);
        this.erro.set(paraProblema(err).detail);
      },
    });
  }
}

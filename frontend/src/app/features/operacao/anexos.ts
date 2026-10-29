import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { Documento, OperacaoApi } from './operacao-api';

/** Anexos de um registro (lançamento, ocorrência, contrato, assembleia). Download sempre autenticado. */
@Component({
  selector: 'tc-anexos',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <section class="tc-card">
      <div class="row">
        <h3 class="tc-section-title">Anexos</h3>
        @if (podeEditar()) {
          <label class="tc-file-button is-text" mat-button>
            <input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.txt" (change)="enviarArquivo($event)" />
            <mat-icon>attach_file</mat-icon> Anexar
          </label>
        }
      </div>
      @if (ocupado()) { <mat-progress-bar mode="indeterminate" /> }
      @for (d of documentos(); track d.id) {
        <div class="doc">
          <mat-icon class="ftype" aria-hidden="true">{{ d.tipoConteudo.includes('pdf') ? 'picture_as_pdf' : d.tipoConteudo.startsWith('image') ? 'image' : 'description' }}</mat-icon>
          <span class="name">{{ d.titulo }} <small class="tc-muted">({{ tamanho(d) }})</small></span>
          <button mat-icon-button (click)="baixar(d)" [attr.aria-label]="'Baixar ' + d.titulo"><mat-icon>download</mat-icon></button>
        </div>
      } @empty {
        <p class="tc-muted small">Nenhum anexo (notas fiscais, comprovantes, fotos).</p>
      }
    </section>
  `,
  styles: `
    .row { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
    .row h3 { margin: 0; }
    .doc { display: flex; align-items: center; gap: 10px; padding: 6px 0; border-bottom: 1px solid var(--tc-border); }
    .doc:last-of-type { border-bottom: 0; }
    .ftype { color: var(--mat-sys-primary); flex: 0 0 auto; }
    .name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .small { font: var(--mat-sys-body-small); margin: 4px 0 0; }
  `,
})
export class Anexos {
  readonly idCondominio = input.required<string>();
  readonly tipoEntidade = input.required<string>();
  readonly idEntidade = input.required<string>();
  readonly categoria = input('OTHER');
  readonly podeEditar = input(false);
  private readonly api = inject(OperacaoApi);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly ocupado = signal(false);
  private readonly recarga = signal(0);

  protected readonly documentos = toSignal(
    combineLatest([toObservable(this.idCondominio), toObservable(this.idEntidade), toObservable(this.recarga)]).pipe(
      switchMap(([c, id]) => this.api.documentos(c, { tipoEntidade: this.tipoEntidade(), idEntidade: id }).pipe(
        map((p) => p.conteudo), catchError(() => of([] as Documento[])))),
    ),
    { initialValue: [] as Documento[] },
  );

  enviarArquivo(evento: Event): void {
    const campoArquivo = evento.target as HTMLInputElement;
    const arquivo = campoArquivo.files?.[0];
    campoArquivo.value = '';
    if (!arquivo) return;
    this.ocupado.set(true);
    this.api.enviarArquivo(this.idCondominio(), arquivo, { categoria: this.categoria(), titulo: arquivo.name, tipoEntidade: this.tipoEntidade(), idEntidade: this.idEntidade() })
      .subscribe({
        next: () => { this.ocupado.set(false); this.recarga.update((t) => t + 1); this.notificacao.sucesso('Arquivo anexado.'); },
        error: (err: unknown) => { this.ocupado.set(false); this.notificacao.erro(paraProblema(err).detail); },
      });
  }

  baixar(d: Documento): void {
    this.api.baixar(this.idCondominio(), d.id).subscribe({ error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  tamanho(d: Documento): string {
    return d.tamanhoBytes > 1024 * 1024 ? (d.tamanhoBytes / 1024 / 1024).toFixed(1) + ' MB' : Math.ceil(d.tamanhoBytes / 1024) + ' KB';
  }
}

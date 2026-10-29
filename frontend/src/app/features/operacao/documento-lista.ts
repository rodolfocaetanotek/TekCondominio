import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { catchError, combineLatest, filter, map, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { pedirConfirmacao } from '../../comum/ui/confirmacao-dialog';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Documento, OperacaoApi } from './operacao-api';

@Component({
  selector: 'tc-documento-lista',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, CabecalhoPagina,
    EstadoVazio, CondominioObrigatorio, RotuloPipe],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Arquivo" titulo="Documentos" [subtitulo]="contexto.selecionado()?.nome" />
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        @if (podeEditar()) {
          <section class="tc-card upload">
            <h3 class="tc-section-title">Enviar documento</h3>
            <div class="row">
              <mat-form-field><mat-label>Título</mat-label><input matInput [formControl]="titulo" maxlength="200" /></mat-form-field>
              <mat-form-field><mat-label>Categoria</mat-label><mat-select [formControl]="categoria">
                @for (o of categorias; track o.valor) { <mat-option [value]="o.valor">{{ o.rotulo }}</mat-option> }</mat-select></mat-form-field>
              <mat-form-field><mat-label>Visibilidade</mat-label><mat-select [formControl]="visibilidade">
                <mat-option value="INTERNAL">Interno (equipe)</mat-option><mat-option value="RESIDENTS">Moradores (portal)</mat-option></mat-select></mat-form-field>
              <label class="tc-file-button file"><input type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.txt" (change)="escolher($event)" />
                <mat-icon>upload_file</mat-icon> {{ arquivo()?.name ?? 'Escolher arquivo' }}</label>
              <button mat-flat-button (click)="enviarArquivo()" [disabled]="!arquivo() || ocupado()">Enviar</button>
            </div>
            <p class="tc-muted small">PDF, PNG, JPG, DOCX, XLSX, CSV ou TXT até 10 MB. O conteúdo é verificado e o arquivo só é acessível a usuários autorizados.</p>
          </section>
        }
        <div class="filters">
          <mat-form-field><mat-label>Categoria</mat-label><mat-select [formControl]="filtroCategoria">
            <mat-option value="">Todas</mat-option>
            @for (o of categorias; track o.valor) { <mat-option [value]="o.valor">{{ o.rotulo }}</mat-option> }</mat-select></mat-form-field>
        </div>
        <section class="tc-table-card">
          @for (d of documentos(); track d.id) {
            <div class="doc">
              <span class="tc-ico" aria-hidden="true"><mat-icon>{{ d.tipoConteudo.includes('pdf') ? 'picture_as_pdf' : d.tipoConteudo.startsWith('image') ? 'image' : 'description' }}</mat-icon></span>
              <div class="who"><strong>{{ d.titulo }}</strong>
                <span class="meta tc-muted">{{ d.categoria | rotulo: 'docCategory' }} · {{ d.visibilidade === 'RESIDENTS' ? 'visível aos moradores' : 'interno' }}
                  · {{ d.nomeArquivo }} · {{ d.criadoEm | date: 'dd/MM/yyyy' }} · {{ d.nomeCriadoPor }}{{ d.tipoEntidade ? ' · anexo' : '' }}</span></div>
              <div class="acts">
              <button mat-icon-button (click)="baixar(d)" [attr.aria-label]="'Baixar ' + d.titulo"><mat-icon>download</mat-icon></button>
              @if (podeEditar() && !d.tipoEntidade) { <button mat-icon-button (click)="arquivar(d)" [attr.aria-label]="'Arquivar ' + d.titulo"><mat-icon>archive</mat-icon></button> }
              </div>
            </div>
          } @empty { <tc-estado-vazio icone="folder_open" titulo="Nenhum documento" /> }
        </section>
      }
    </div>
  `,
  styles: `
    .upload { margin-bottom: 16px; } .upload h3 { margin-top: 0; }
    .row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
    .row > mat-form-field { flex: 1 1 200px; min-width: 0; }
    /* Seletor com aparÃªncia de campo (fica na linha dos campos do formulÃ¡rio). */
    .file { min-height: 44px; padding: 8px 14px; background: var(--tc-field-bg); color: var(--tc-text); font-weight: inherit; }
    .file mat-icon { color: var(--mat-sys-primary); }
    .file:hover { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .small { font: var(--mat-sys-body-small); margin: 8px 0 0; }
    .doc { display: flex; align-items: center; gap: 12px; padding: 12px 20px; transition: background-color 0.12s; }
    .doc + .doc { border-top: 1px solid var(--tc-border); }
    .doc:hover { background: color-mix(in srgb, var(--mat-sys-primary) 3%, var(--tc-surface)); }
    .who { flex: 1; display: grid; gap: 2px; min-width: 0; }
    .who strong, .meta { overflow-wrap: anywhere; }
    .meta { font: var(--mat-sys-body-small); }
    .acts { display: flex; align-items: center; flex: 0 0 auto; }
    @media (max-width: 600px) {
      .row > *, .row > button { flex: 1 1 100%; }
      .file { justify-content: center; }
      .doc { padding: 12px 14px; align-items: flex-start; }
    }
  `,
})
export class DocumentoLista {
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly categorias = opcoes('docCategory');
  protected readonly titulo = new FormControl('', { nonNullable: true });
  protected readonly categoria = new FormControl('OTHER', { nonNullable: true });
  protected readonly visibilidade = new FormControl('INTERNAL', { nonNullable: true });
  protected readonly filtroCategoria = new FormControl('', { nonNullable: true });
  protected readonly arquivo = signal<File | null>(null);
  protected readonly ocupado = signal(false);
  private readonly recarga = signal(0);
  protected readonly podeEditar = computed(() => this.contexto.pode('DOCUMENT_EDIT'));
  private readonly valorCategoria = toSignal(this.filtroCategoria.valueChanges, { initialValue: '' });
  protected readonly documentos = toSignal(combineLatest([toObservable(this.contexto.id), toObservable(this.valorCategoria), toObservable(this.recarga)]).pipe(
    filter(([c]) => !!c),
    switchMap(([c, categoria]) => this.api.documentos(c!, categoria ? { categoria } : {}).pipe(map((p) => p.conteudo), catchError(() => of([] as Documento[]))))),
    { initialValue: [] as Documento[] });

  escolher(evento: Event): void {
    const f = (evento.target as HTMLInputElement).files?.[0] ?? null;
    this.arquivo.set(f);
    if (f && !this.titulo.value) this.titulo.setValue(f.name.replace(/\.[^.]+$/, ''));
  }

  enviarArquivo(): void {
    const f = this.arquivo();
    if (!f) return;
    this.ocupado.set(true);
    this.api.enviarArquivo(this.contexto.id()!, f, { titulo: this.titulo.value || f.name, categoria: this.categoria.value, visibilidade: this.visibilidade.value })
      .subscribe({
        next: () => { this.ocupado.set(false); this.arquivo.set(null); this.titulo.reset(); this.notificacao.sucesso('Documento enviado.'); this.recarga.update((t) => t + 1); },
        error: (err: unknown) => { this.ocupado.set(false); this.notificacao.erro(paraProblema(err).detail); },
      });
  }

  baixar(d: Documento): void {
    this.api.baixar(this.contexto.id()!, d.id).subscribe({ error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  arquivar(d: Documento): void {
    pedirConfirmacao(this.dialog, 'Arquivar documento', `Arquivar "${d.titulo}"? Ele sai da lista, mas é preservado no histórico.`, 'Arquivar')
      .pipe(switchMap(() => this.api.arquivarDocumento(this.contexto.id()!, d.id)))
      .subscribe({ next: () => this.recarga.update((t) => t + 1), error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }
}

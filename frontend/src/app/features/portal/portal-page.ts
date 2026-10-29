import { PortalReservas } from '../reservas/portal-reservas';
import { PortalVisitantes } from '../portaria/portal-visitantes';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { opcoes } from '../../comum/rotulos';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { abrirFormulario } from '../../comum/ui/formulario-dialog';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { Comunicado, DetalheOcorrencia } from '../operacao/operacao-api';
import { PortalApi } from './portal-api';

/** Portal do morador: somente dados das unidades do próprio morador (o backend aplica a restrição). */
@Component({
  selector: 'tc-portal-page',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, MatTabsModule, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatInputModule, MatExpansionModule, MatProgressBarModule, CabecalhoPagina, EstadoVazio, DataIsoPipe, RotuloPipe, PortalReservas, PortalVisitantes],
  template: `
    <div class="tc-page narrow tc-comfortable">
      @if (erro(); as mensagem) {
        <section class="tc-card"><tc-estado-vazio icone="home" titulo="Portal indisponível" [descricao]="mensagem" /></section>
      } @else if (perfil(); as m) {
        <tc-cabecalho-pagina sobretitulo="Portal do morador" [titulo]="'Olá, ' + m.nome.split(' ')[0]" [subtitulo]="condominio()?.nome">
          @if (m.condominios.length > 1) {
            <mat-form-field class="condo" subscriptSizing="dynamic"><mat-label>Condomínio</mat-label>
              <mat-select [value]="idCondominio()" (valueChange)="idCondominio.set($event)">
                @for (c of m.condominios; track c.id) { <mat-option [value]="c.id">{{ c.nome }}</mat-option> }</mat-select></mat-form-field>
          }
        </tc-cabecalho-pagina>
        <section class="units">
          @for (u of condominio()?.unidades ?? []; track u.idUnidade) {
            <div class="tc-card unit"><span class="tc-ico lg" aria-hidden="true"><mat-icon>home</mat-icon></span><div><strong>Unidade {{ u.rotulo }}</strong>
              <span class="tc-muted">{{ u.tipo === 'OWNER' ? 'Proprietário(a)' : u.tipo === 'TENANT' ? 'Inquilino(a)' : 'Morador(a)' }}{{ u.responsavelFinanceiro ? ' · responsável financeiro' : '' }}</span></div></div>
          }
        </section>
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms">
          <mat-tab label="Agenda">
            <section class="tc-card mt">
              @for (e of calendario() ?? []; track $index) {
                <div class="ev"><span class="date">{{ e.data | dataIso }}</span><mat-icon class="ev-ico" aria-hidden="true">{{ e.tipo === 'ASSEMBLY' ? 'groups' : 'receipt' }}</mat-icon><span class="ev-title">{{ e.titulo }}</span></div>
              } @empty { <p class="tc-muted">Nenhum evento próximo.</p> }
            </section>
          </mat-tab>
          <mat-tab label="Cobranças">
            <ng-template matTabContent>
              @for (u of encargos() ?? []; track u.idUnidade) {
                <section class="tc-card mt">
                  <h3 class="tc-section-title first">Unidade {{ u.rotuloUnidade }} · em aberto <span class="money" [class.negative]="u.totalAberto > 0">{{ u.totalAberto | currency: 'BRL' }}</span></h3>
                  @for (l of u.linhas; track l.idLancamento) {
                    <div class="charge">
                      <div class="who"><strong>{{ l.descricao }}</strong><br /><small class="tc-muted">vencimento {{ l.dataVencimento | dataIso }}{{ l.diasAtraso > 0 ? ' · ' + l.diasAtraso + ' dias em atraso' : '' }}</small></div>
                      <div class="right"><span class="money">{{ (l.valorAberto > 0 ? l.totalAtualizado : l.valor) | currency: 'BRL' }}</span>
                        <span class="tc-status" [class.is-success]="l.situacao === 'PAID'"
                          [class.is-danger]="l.valorAberto > 0 && l.diasAtraso > 0" [class.is-pending]="l.valorAberto > 0 && l.diasAtraso <= 0"
                          [class.is-inactive]="l.situacao === 'CANCELLED' || l.situacao === 'RENEGOTIATED'">{{ l.situacao | rotulo: 'entryStatus' }}</span></div>
                    </div>
                  }
                  <p class="tc-muted small note">Valores em atraso incluem multa e juros calculados até hoje. Para 2ª via ou negociação, fale com a administradora.</p>
                </section>
              } @empty {
                <section class="tc-card mt"><tc-estado-vazio icone="receipt_long" titulo="Sem cobranças para exibir"
                  descricao="As cobranças aparecem para o proprietário ou responsável financeiro da unidade." /></section>
              }
            </ng-template>
          </mat-tab>
          <mat-tab label="Comunicados">
            <ng-template matTabContent>
              @for (a of comunicados() ?? []; track a.id) {
                <article class="tc-card mt ann" [class.unread]="!a.lido" (click)="ler(a)">
                  <h3 class="tc-section-title first">@if (a.fixado) { <mat-icon inline class="pin">push_pin</mat-icon> } {{ a.titulo }}</h3>
                  <p class="pre">{{ a.texto }}</p><small class="tc-muted">{{ a.publicadoEm | date: 'dd/MM/yyyy HH:mm' }}</small>
                </article>
              } @empty { <section class="tc-card mt"><tc-estado-vazio icone="campaign" titulo="Nenhum comunicado" /></section> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Documentos">
            <ng-template matTabContent>
              <section class="tc-card mt">
                @for (d of documentos() ?? []; track d.id) {
                  <div class="doc"><span class="tc-ico sm" aria-hidden="true"><mat-icon>description</mat-icon></span><span class="doc-name">{{ d.titulo }} <small class="tc-muted">({{ d.categoria | rotulo: 'docCategory' }})</small></span>
                    <button mat-icon-button (click)="baixar(d.id)" [attr.aria-label]="'Baixar ' + d.titulo"><mat-icon>download</mat-icon></button></div>
                } @empty { <p class="tc-muted">Nenhum documento disponível.</p> }
              </section>
            </ng-template>
          </mat-tab>
          <mat-tab label="Ocorrências">
            <ng-template matTabContent>
              <div class="tc-actions mt"><button mat-flat-button class="primary" (click)="novaOcorrencia()"><mat-icon>add</mat-icon> Abrir solicitação</button></div>
              <mat-accordion class="tc-accordion-cards mt">
                @for (o of ocorrencias() ?? []; track o.id) {
                  <mat-expansion-panel (opened)="abrirOcorrencia(o.id)">
                    <mat-expansion-panel-header><mat-panel-title>Nº {{ o.numero }} · {{ o.titulo }}</mat-panel-title>
                      <mat-panel-description><span class="tc-status" [class.is-success]="o.situacao === 'RESOLVED'"
                        [class.is-pending]="o.situacao === 'OPEN' || o.situacao === 'WAITING'" [class.is-info]="o.situacao === 'IN_PROGRESS'"
                        [class.is-inactive]="o.situacao === 'CANCELLED'">{{ o.situacao | rotulo: 'occurrenceStatus' }}</span></mat-panel-description></mat-expansion-panel-header>
                    @if (detalhe()?.resumo?.id === o.id) {
                      @let d = detalhe()!;
                      <p class="pre">{{ d.descricao }}</p>
                      @if (d.solucao) { <p class="resolution"><strong>Solução:</strong> {{ d.solucao }}</p> }
                      @for (i of d.interacoes; track i.id) { <p class="msg"><strong>{{ i.nomeAutor }}</strong> <small class="tc-muted">{{ i.criadoEm | date: 'dd/MM HH:mm' }}</small><br />{{ i.mensagem }}</p> }
                      @if (o.situacao !== 'RESOLVED' && o.situacao !== 'CANCELLED') {
                        <mat-form-field class="full"><mat-label>Mensagem</mat-label><textarea matInput rows="2" [formControl]="mensagem" maxlength="2000"></textarea></mat-form-field>
                        <div class="tc-actions"><button mat-flat-button class="primary" (click)="comentar(o.id)" [disabled]="mensagem.invalid">Enviar</button></div>
                      }
                    } @else { <mat-progress-bar mode="indeterminate" /> }
                  </mat-expansion-panel>
                } @empty { <section class="tc-card"><tc-estado-vazio icone="build" titulo="Nenhuma solicitação" /></section> }
              </mat-accordion>
            </ng-template>
          </mat-tab>
          <mat-tab label="Reservas">
            <ng-template matTabContent>
              @if (condominio(); as c) { <tc-portal-reservas [idCondominio]="c.id" [unidades]="c.unidades" /> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Visitantes">
            <ng-template matTabContent>
              @if (condominio(); as c) { <tc-portal-visitantes [idCondominio]="c.id" [unidades]="c.unidades" /> }
            </ng-template>
          </mat-tab>
          <mat-tab label="Assembleias">
            <ng-template matTabContent>
              <mat-accordion class="tc-accordion-cards mt">
                @for (a of assembleias() ?? []; track a.id) {
                  <mat-expansion-panel>
                    <mat-expansion-panel-header><mat-panel-title>{{ a.titulo }}</mat-panel-title>
                      <mat-panel-description><span>{{ a.primeiraConvocacaoEm | date: 'dd/MM/yyyy HH:mm' }}</span><span class="tc-status" [class.is-success]="a.situacao === 'CLOSED'"
                        [class.is-pending]="a.situacao === 'CALLED'" [class.is-info]="a.situacao === 'IN_PROGRESS'"
                        [class.is-inactive]="a.situacao === 'CANCELLED' || a.situacao === 'DRAFT'">{{ a.situacao | rotulo: 'assemblyStatus' }}</span></mat-panel-description></mat-expansion-panel-header>
                    <p>Local: {{ a.local }}</p>
                    @if (a.ata) { <h4 class="tc-section-label">Ata</h4><p class="pre">{{ a.ata }}</p> } @else { <p class="tc-muted">A ata será publicada após o encerramento.</p> }
                  </mat-expansion-panel>
                } @empty { <section class="tc-card"><tc-estado-vazio icone="groups" titulo="Nenhuma assembleia" /></section> }
              </mat-accordion>
            </ng-template>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 900px; }
    .mt { display: block; margin-top: 16px; }
    .condo { min-width: 240px; }
    .units { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
    .unit { display: flex; gap: 12px; align-items: center; padding: 14px 16px; flex: 1 1 240px; min-width: 0; }
    .unit div { display: grid; gap: 2px; min-width: 0; }
    .unit .tc-muted { font: var(--mat-sys-body-small); }
    .tc-section-title.first { margin-top: 0; }
    .ev { display: flex; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--tc-border); }
    .ev:last-child { border-bottom: 0; }
    .date { flex: 0 0 92px; font-variant-numeric: tabular-nums; font-weight: 600; }
    .ev-ico { color: var(--mat-sys-primary); flex: 0 0 auto; }
    .ev-title { min-width: 0; overflow-wrap: anywhere; }
    .charge { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--tc-border); }
    .who { min-width: 0; line-height: 1.5; overflow-wrap: anywhere; }
    .right { display: grid; justify-items: end; gap: 4px; flex: 0 0 auto; }
    .right .money { font-weight: 650; font-size: 15px; }
    .note { margin: 12px 0 0; }
    .small { font: var(--mat-sys-body-small); }
    .ann { display: grid; gap: 4px; }
    .ann p { margin: 0 0 4px; }
    .unread { border-left: 4px solid var(--mat-sys-primary); }
    .pin { color: var(--tc-accent); vertical-align: -2px; }
    .pre { white-space: pre-wrap; overflow-wrap: anywhere; }
    .doc { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--tc-border); }
    .doc:last-child { border-bottom: 0; }
    .doc-name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    h4.tc-section-label { margin: 16px 0 6px; }
    .resolution { background: var(--tc-success-container); border-left: 3px solid var(--tc-success); padding: 10px 12px; border-radius: var(--tc-radius-sm); white-space: pre-wrap; }
    .msg { border-left: 3px solid var(--mat-sys-primary); padding: 4px 0 4px 12px; white-space: pre-wrap; overflow-wrap: anywhere; }
    .full { width: 100%; margin-top: 8px; }
    @media (max-width: 600px) {
      .condo { min-width: 0; width: 100%; }
      .unit { flex-basis: 100%; }
      .date { flex-basis: 84px; }
      .tc-actions .primary { flex: 1 1 100%; min-height: 48px; }
    }
  `,
})
export class PortalPage {
  private readonly api = inject(PortalApi);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly erro = signal<string | null>(null);
  protected readonly idCondominio = signal<string | null>(null);
  private readonly recarga = signal(0);
  protected readonly detalhe = signal<DetalheOcorrencia | null>(null);
  protected readonly mensagem = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  protected readonly perfil = toSignal(this.api.perfil().pipe(catchError((err: unknown) => { this.erro.set(paraProblema(err).detail); return of(null); })));
  protected readonly condominio = computed(() => {
    const m = this.perfil();
    if (!m) return null;
    return m.condominios.find((c) => c.id === this.idCondominio()) ?? m.condominios[0] ?? null;
  });
  private readonly origem = combineLatest([toObservable(this.condominio), toObservable(this.recarga)]).pipe(filter(([c]) => !!c));
  protected readonly calendario = toSignal(this.origem.pipe(switchMap(([c]) => this.api.calendario(c!.id).pipe(catchError(() => of([]))))));
  protected readonly encargos = toSignal(this.origem.pipe(switchMap(([c]) => this.api.encargos(c!.id).pipe(catchError(() => of([]))))));
  protected readonly comunicados = toSignal(this.origem.pipe(switchMap(([c]) => this.api.comunicados(c!.id).pipe(catchError(() => of([] as Comunicado[]))))));
  protected readonly documentos = toSignal(this.origem.pipe(switchMap(([c]) => this.api.documentos(c!.id).pipe(catchError(() => of([]))))));
  protected readonly ocorrencias = toSignal(this.origem.pipe(switchMap(([c]) => this.api.ocorrencias(c!.id).pipe(catchError(() => of([]))))));
  protected readonly assembleias = toSignal(this.origem.pipe(switchMap(([c]) => this.api.assembleias(c!.id).pipe(catchError(() => of([]))))));

  private c(): string {
    return this.condominio()!.id;
  }

  ler(a: Comunicado): void {
    if (!a.lido) this.api.ler(this.c(), a.id).subscribe(() => this.recarga.update((t) => t + 1));
  }

  baixar(id: string): void {
    this.api.baixar(this.c(), id).subscribe({ error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail) });
  }

  abrirOcorrencia(id: string): void {
    this.detalhe.set(null);
    this.api.ocorrencia(this.c(), id).subscribe((d) => this.detalhe.set(d));
  }

  novaOcorrencia(): void {
    const unidades = this.condominio()?.unidades ?? [];
    abrirFormulario(this.dialog, {
      titulo: 'Abrir solicitação ou ocorrência',
      campos: [
        { chave: 'titulo', rotulo: 'Assunto', obrigatorio: true, tamanhoMaximo: 150 },
        { chave: 'descricao', rotulo: 'Descreva', tipo: 'textarea', obrigatorio: true, tamanhoMaximo: 4000 },
        { chave: 'categoria', rotulo: 'Categoria', tipo: 'select', obrigatorio: true, valor: 'REQUEST', colunas: 6, opcoes: opcoes('occurrenceCategory') },
        { chave: 'idUnidade', rotulo: 'Relacionada à unidade', tipo: 'select', valor: unidades[0]?.idUnidade ?? null, colunas: 6,
          opcoes: [{ valor: null, rotulo: 'Área comum' }, ...unidades.map((u) => ({ valor: u.idUnidade, rotulo: u.rotulo }))] },
      ],
      rotuloEnviar: 'Enviar',
      enviar: (v) => this.api.abrirOcorrencia(this.c(), v),
    }).pipe(filter((r) => !!r)).subscribe(() => { this.notificacao.sucesso('Solicitação enviada à administração.'); this.recarga.update((t) => t + 1); });
  }

  comentar(id: string): void {
    this.api.comentar(this.c(), id, this.mensagem.value.trim()).subscribe({
      next: (d) => { this.detalhe.set(d); this.mensagem.reset(); },
      error: (err: unknown) => this.notificacao.erro(paraProblema(err).detail),
    });
  }
}

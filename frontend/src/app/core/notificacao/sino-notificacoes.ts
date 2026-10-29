import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { catchError, of, startWith, switchMap, timer, combineLatest } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';
import { CaixaEntrada, OperacaoApi } from '../../features/operacao/operacao-api';

/** Sino de notificações (atualiza a cada 60 s). */
@Component({
  selector: 'tc-sino-notificacoes',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatMenuModule, MatBadgeModule],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Notificações: ' + (caixaEntrada()?.naoLidas ?? 0) + ' não lidas'">
      <mat-icon [matBadge]="caixaEntrada()?.naoLidas || null" matBadgeColor="warn" matBadgeSize="small" aria-hidden="false">notifications</mat-icon>
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="tc-notifications">
      <div class="head" (click)="$event.stopPropagation()">
        <strong>Notificações</strong>
        @if ((caixaEntrada()?.naoLidas ?? 0) > 0) { <button mat-button (click)="marcarTodasLidas()">Marcar todas como lidas</button> }
      </div>
      @for (n of caixaEntrada()?.itens ?? []; track n.id) {
        <button mat-menu-item class="item" [class.unread]="!n.lida" (click)="abrir(n.id, n.link)">
          <span class="t">{{ n.titulo }}</span>
          <span class="b">{{ n.texto }}</span>
          <span class="d">{{ n.criadoEm | date: 'dd/MM HH:mm' }}</span>
        </button>
      } @empty { <p class="empty">Nenhuma notificação.</p> }
    </mat-menu>
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 6px 12px 10px 16px; min-width: min(340px, 90vw);
      border-bottom: 1px solid var(--tc-border); }
    .head strong { font-size: 15px; }
    .item { height: auto !important; min-height: 56px; line-height: 1.3; padding-block: 8px; white-space: normal; }
    .item .t { display: block; font-weight: 600; } .item .b, .item .d { display: block; font-size: 12px; opacity: .8; }
    .item.unread { background: color-mix(in srgb, var(--tc-accent) 6%, transparent); }
    .item.unread .t::before { content: '● '; color: var(--tc-accent); }
    .empty { padding: 20px 16px; margin: 0; color: var(--tc-text-muted); text-align: center; }
  `,
})
export class SinoNotificacoes {
  private readonly api = inject(OperacaoApi);
  private readonly router = inject(Router);
  private readonly recarga = signal(0);
  protected readonly caixaEntrada = toSignal(combineLatest([timer(0, 60_000), toObservable(this.recarga)]).pipe(
    switchMap(() => this.api.caixaEntrada().pipe(catchError(() => of(null as CaixaEntrada | null)))), startWith(null)));

  abrir(id: string, link?: string): void {
    this.api.marcarNotificacaoLida(id).subscribe(() => this.recarga.update((t) => t + 1));
    if (link) void this.router.navigateByUrl(link.startsWith('/portal/') ? '/portal' : link);
  }

  marcarTodasLidas(): void {
    this.api.marcarTodasNotificacoesLidas().subscribe(() => this.recarga.update((t) => t + 1));
  }
}

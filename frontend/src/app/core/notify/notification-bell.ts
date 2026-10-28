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
import { Inbox, OpsApi } from '../../features/ops/ops-api';

/** Sino de notificações (atualiza a cada 60 s). */
@Component({
  selector: 'tc-notification-bell',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatMenuModule, MatBadgeModule],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Notificações: ' + (inbox()?.unread ?? 0) + ' não lidas'">
      <mat-icon [matBadge]="inbox()?.unread || null" matBadgeColor="warn" matBadgeSize="small" aria-hidden="false">notifications</mat-icon>
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="tc-notifications">
      <div class="head" (click)="$event.stopPropagation()">
        <strong>Notificações</strong>
        @if ((inbox()?.unread ?? 0) > 0) { <button mat-button (click)="readAll()">Marcar todas como lidas</button> }
      </div>
      @for (n of inbox()?.items ?? []; track n.id) {
        <button mat-menu-item class="item" [class.unread]="!n.read" (click)="open(n.id, n.link)">
          <span class="t">{{ n.title }}</span>
          <span class="b">{{ n.body }}</span>
          <span class="d">{{ n.createdAt | date: 'dd/MM HH:mm' }}</span>
        </button>
      } @empty { <p class="empty">Nenhuma notificação.</p> }
    </mat-menu>
  `,
  styles: `
    .head { display: flex; justify-content: space-between; align-items: center; padding: 4px 12px 8px; min-width: 320px; }
    .item { height: auto !important; min-height: 56px; line-height: 1.3; padding-block: 8px; white-space: normal; }
    .item .t { display: block; font-weight: 600; } .item .b, .item .d { display: block; font-size: 12px; opacity: .8; }
    .item.unread .t::before { content: '● '; color: var(--mat-sys-primary); }
    .empty { padding: 12px; margin: 0; opacity: .7; }
  `,
})
export class NotificationBell {
  private readonly api = inject(OpsApi);
  private readonly router = inject(Router);
  private readonly tick = signal(0);
  protected readonly inbox = toSignal(combineLatest([timer(0, 60_000), toObservable(this.tick)]).pipe(
    switchMap(() => this.api.inbox().pipe(catchError(() => of(null as Inbox | null)))), startWith(null)));

  open(id: string, link?: string): void {
    this.api.readNotification(id).subscribe(() => this.tick.update((t) => t + 1));
    if (link) void this.router.navigateByUrl(link.startsWith('/portal/') ? '/portal' : link);
  }

  readAll(): void {
    this.api.readAllNotifications().subscribe(() => this.tick.update((t) => t + 1));
  }
}

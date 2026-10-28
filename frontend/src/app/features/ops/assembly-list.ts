import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, filter, of, switchMap } from 'rxjs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { NotificationService } from '../../core/notify/notification.service';
import { LabelPipe } from '../../shared/label.pipe';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { openAssemblyForm } from './assembly-form';
import { AssemblySummary, OpsApi } from './ops-api';

@Component({
  selector: 'tc-assembly-list',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, PageHeader, EmptyState, CondominiumRequired, LabelPipe],
  template: `
    <div class="tc-page narrow">
      <tc-page-header eyebrow="Governança" title="Assembleias" [subtitle]="context.selected()?.name">
        @if (canEdit()) { <button mat-flat-button (click)="create()"><mat-icon>groups</mat-icon> Nova assembleia</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <section class="tc-table-card">
          @for (a of list() ?? []; track a.id) {
            <a class="row" [routerLink]="['/assembleias', a.id]">
              <mat-icon aria-hidden="true">event</mat-icon>
              <div class="who"><strong>{{ a.title }}</strong>
                <span class="tc-muted">{{ a.kind | label: 'assemblyKind' }} · {{ a.scheduledAt | date: 'dd/MM/yyyy HH:mm' }} · {{ a.location }}</span></div>
              <span class="tc-status" [class.is-active]="a.status === 'CLOSED'" [class.is-pending]="a.status === 'CALLED' || a.status === 'IN_PROGRESS'"
                [class.is-inactive]="a.status === 'CANCELLED' || a.status === 'DRAFT'">{{ a.status | label: 'assemblyStatus' }}</span>
            </a>
          } @empty { <tc-empty-state icon="groups" title="Nenhuma assembleia" /> }
        </section>
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .row { display: flex; align-items: center; gap: 12px; padding: 12px 20px; border-top: 1px solid var(--mat-sys-outline-variant); color: inherit; text-decoration: none; }
    .row:hover { background: var(--mat-sys-surface-container); }
    .row mat-icon { color: var(--mat-sys-primary); }
    .who { flex: 1; display: grid; }
  `,
})
export class AssemblyList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('ASSEMBLY_EDIT') && this.context.selected()?.status === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.assemblies(c!).pipe(catchError(() => of([] as AssemblySummary[]))))));

  create(): void {
    openAssemblyForm(this.dialog, this.api, this.context.id()!).pipe(filter((r) => !!r)).subscribe((a) => {
      this.notify.success('Assembleia criada como rascunho.');
      void this.router.navigate(['/assembleias', a!.id]);
    });
  }
}

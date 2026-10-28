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
      <tc-page-header eyebrow="Governança" title="Assembleias" [subtitle]="context.selected()?.nome">
        @if (canEdit()) { <button mat-flat-button (click)="create()"><mat-icon>groups</mat-icon> Nova assembleia</button> }
      </tc-page-header>
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <section class="tc-table-card">
          @for (a of list() ?? []; track a.id) {
            <a class="row" [routerLink]="['/assembleias', a.id]">
              <span class="tc-ico" aria-hidden="true"><mat-icon>event</mat-icon></span>
              <div class="who"><strong>{{ a.title }}</strong>
                <span class="tc-muted">{{ a.kind | label: 'assemblyKind' }} · {{ a.scheduledAt | date: 'dd/MM/yyyy HH:mm' }} · {{ a.location }}</span></div>
              <span class="tc-status" [class.is-success]="a.status === 'CLOSED'" [class.is-pending]="a.status === 'CALLED'" [class.is-info]="a.status === 'IN_PROGRESS'"
                [class.is-inactive]="a.status === 'CANCELLED' || a.status === 'DRAFT'">{{ a.status | label: 'assemblyStatus' }}</span>
              <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
            </a>
          } @empty { <tc-empty-state icon="groups" title="Nenhuma assembleia" /> }
        </section>
      }
    </div>
  `,
  styles: `
    .narrow { max-width: 1000px; }
    .row { display: flex; align-items: center; gap: 8px 14px; flex-wrap: wrap; padding: 14px 20px; color: inherit; text-decoration: none; transition: background-color 0.12s; }
    .row + .row { border-top: 1px solid var(--tc-border); }
    .row:hover, .row:focus-visible { background: color-mix(in srgb, var(--mat-sys-primary) 5%, var(--tc-surface)); }
    .row:focus-visible { outline: none; box-shadow: inset 3px 0 0 var(--mat-sys-primary); }
    .row:hover strong { color: var(--mat-sys-primary); }
    .who { flex: 1 1 220px; display: grid; gap: 2px; min-width: 0; }
    .who strong { overflow-wrap: anywhere; }
    .who .tc-muted { font: var(--mat-sys-body-small); }
    .chev { color: var(--tc-text-muted); }
    @media (max-width: 600px) {
      .row { padding: 12px 14px; }
      .chev { display: none; }
      .tc-status { margin-left: 52px; }
    }
  `,
})
export class AssemblyList {
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  private readonly tick = signal(0);
  protected readonly canEdit = computed(() => this.context.can('ASSEMBLY_EDIT') && this.context.selected()?.situacao === 'ACTIVE');
  protected readonly list = toSignal(combineLatest([toObservable(this.context.id), toObservable(this.tick)]).pipe(
    filter(([c]) => !!c), switchMap(([c]) => this.api.assemblies(c!).pipe(catchError(() => of([] as AssemblySummary[]))))));

  create(): void {
    openAssemblyForm(this.dialog, this.api, this.context.id()!).pipe(filter((r) => !!r)).subscribe((a) => {
      this.notify.success('Assembleia criada como rascunho.');
      void this.router.navigate(['/assembleias', a!.id]);
    });
  }
}

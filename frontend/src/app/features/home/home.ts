import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CondominiumContext } from '../../core/context/condominium-context';
import { LabelPipe } from '../../shared/label.pipe';
import { todayIso } from '../../shared/ui/dates';
import { IsoDatePipe } from '../../shared/ui/iso-date.pipe';
import { PageHeader } from '../../shared/ui/page-header';
import { OpsApi } from '../ops/ops-api';
import { MonthlyChart } from './monthly-chart';

/** Painel gerencial: todos os números vêm do backend, calculados sobre os dados gravados. */
@Component({
  selector: 'tc-home',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, MatIconModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatProgressBarModule, PageHeader, MonthlyChart, IsoDatePipe, LabelPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly auth = inject(AuthService);
  protected readonly context = inject(CondominiumContext);
  private readonly api = inject(OpsApi);

  protected readonly scope = new FormControl('', { nonNullable: true });
  protected readonly from = new FormControl(todayIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly to = new FormControl(todayIso(), { nonNullable: true });
  protected readonly firstName = computed(() => this.auth.user()?.nome.split(' ')[0] ?? '');

  protected readonly data = toSignal(
    combineLatest([
      this.scope.valueChanges.pipe(startWith('')),
      this.from.valueChanges.pipe(startWith(this.from.value)),
      this.to.valueChanges.pipe(startWith(this.to.value)),
      toObservable(this.context.options),
    ]).pipe(
      debounceTime(150),
      switchMap(([idCondominio, de, ate]) => this.api.dashboard({ ...(idCondominio ? { idCondominio } : {}), de, ate })
        .pipe(catchError(() => of(null)))),
    ),
    { initialValue: undefined },
  );

  protected readonly result = computed(() => {
    const d = this.data();
    return d ? d.receitas - d.despesas : 0;
  });
}

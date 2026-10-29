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
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { RotuloPipe } from '../../comum/rotulo.pipe';
import { hojeIso } from '../../comum/ui/datas';
import { DataIsoPipe } from '../../comum/ui/data-iso.pipe';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { OperacaoApi } from '../operacao/operacao-api';
import { GraficoMensal } from './grafico-mensal';

/** Painel gerencial: todos os números vêm do backend, calculados sobre os dados gravados. */
@Component({
  selector: 'tc-painel',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, MatIconModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatProgressBarModule, CabecalhoPagina, GraficoMensal, DataIsoPipe, RotuloPipe],
  templateUrl: './painel.html',
  styleUrl: './painel.scss',
})
export class PainelPage {
  protected readonly auth = inject(AuthService);
  protected readonly contexto = inject(CondominioContexto);
  private readonly api = inject(OperacaoApi);

  protected readonly escopo = new FormControl('', { nonNullable: true });
  protected readonly de = new FormControl(hojeIso().slice(0, 8) + '01', { nonNullable: true });
  protected readonly ate = new FormControl(hojeIso(), { nonNullable: true });
  protected readonly primeiroNome = computed(() => this.auth.usuario()?.nome.split(' ')[0] ?? '');

  protected readonly data = toSignal(
    combineLatest([
      this.escopo.valueChanges.pipe(startWith('')),
      this.de.valueChanges.pipe(startWith(this.de.value)),
      this.ate.valueChanges.pipe(startWith(this.ate.value)),
      toObservable(this.contexto.opcoes),
    ]).pipe(
      debounceTime(150),
      switchMap(([idCondominio, de, ate]) => this.api.painel({ ...(idCondominio ? { idCondominio } : {}), de, ate })
        .pipe(catchError(() => of(null)))),
    ),
    { initialValue: undefined },
  );

  protected readonly resultado = computed(() => {
    const d = this.data();
    return d ? d.receitas - d.despesas : 0;
  });
}

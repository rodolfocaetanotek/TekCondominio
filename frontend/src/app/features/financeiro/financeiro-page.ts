import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { CondominioObrigatorio } from '../../comum/ui/condominio-obrigatorio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { ContasBancariasTab } from './contas-bancarias-tab';
import { OrcamentoTab } from './orcamento-tab';
import { FechamentoTab } from './fechamento-tab';
import { IndicadoresTab } from './indicadores-tab';
import { ConciliacaoTab } from './conciliacao-tab';
import { LancamentoLista } from './lancamento-lista';
import { ParametrosTab } from './parametros-tab';
import { TransferenciaTab } from './transferencia-tab';

@Component({
  selector: 'tc-financeiro-page',
  imports: [MatTabsModule, CabecalhoPagina, CondominioObrigatorio, LancamentoLista, ContasBancariasTab, ConciliacaoTab, TransferenciaTab, OrcamentoTab, FechamentoTab, IndicadoresTab, ParametrosTab],
  template: `
    <div class="tc-page">
      <tc-cabecalho-pagina sobretitulo="Financeiro" titulo="Gestão financeira" [subtitulo]="contexto.selecionado()?.nome" />
      @if (!contexto.id()) {
        <tc-condominio-obrigatorio />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms" preserveContent="false">
          <mat-tab label="Contas a pagar"><ng-template matTabContent><tc-lancamento-lista natureza="EXPENSE" /></ng-template></mat-tab>
          <mat-tab label="Contas a receber"><ng-template matTabContent><tc-lancamento-lista natureza="INCOME" /></ng-template></mat-tab>
          <mat-tab label="Contas e extrato"><ng-template matTabContent><tc-contas-bancarias-tab /></ng-template></mat-tab>
          <mat-tab label="Conciliação (OFX)"><ng-template matTabContent><tc-conciliacao-tab /></ng-template></mat-tab>
          <mat-tab label="Transferências"><ng-template matTabContent><tc-transferencia-tab /></ng-template></mat-tab>
          <mat-tab label="Orçamento"><ng-template matTabContent><tc-orcamento-tab /></ng-template></mat-tab>
          <mat-tab label="Indicadores"><ng-template matTabContent><tc-indicadores-tab /></ng-template></mat-tab>
          <mat-tab label="Fechamento mensal"><ng-template matTabContent><tc-fechamento-tab /></ng-template></mat-tab>
          <mat-tab label="Configurações"><ng-template matTabContent><tc-parametros-tab /></ng-template></mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
})
export class FinanceiroPage {
  protected readonly contexto = inject(CondominioContexto);
}

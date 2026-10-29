import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { CondominiumRequired } from '../../comum/ui/condominio-obrigatorio';
import { PageHeader } from '../../comum/ui/cabecalho-pagina';
import { BankTab } from './contas-bancarias-tab';
import { BudgetTab } from './orcamento-tab';
import { ClosingTab } from './fechamento-tab';
import { InsightsTab } from './indicadores-tab';
import { ReconciliationTab } from './conciliacao-tab';
import { EntryList } from './lancamento-lista';
import { SettingsTab } from './parametros-tab';
import { TransferTab } from './transferencia-tab';

@Component({
  selector: 'tc-finance-page',
  imports: [MatTabsModule, PageHeader, CondominiumRequired, EntryList, BankTab, ReconciliationTab, TransferTab, BudgetTab, ClosingTab, InsightsTab, SettingsTab],
  template: `
    <div class="tc-page">
      <tc-page-header eyebrow="Financeiro" title="Gestão financeira" [subtitle]="context.selected()?.nome" />
      @if (!context.id()) {
        <tc-condominium-required />
      } @else {
        <mat-tab-group mat-stretch-tabs="false" animationDuration="0ms" preserveContent="false">
          <mat-tab label="Contas a pagar"><ng-template matTabContent><tc-entry-list kind="EXPENSE" /></ng-template></mat-tab>
          <mat-tab label="Contas a receber"><ng-template matTabContent><tc-entry-list kind="INCOME" /></ng-template></mat-tab>
          <mat-tab label="Contas e extrato"><ng-template matTabContent><tc-bank-tab /></ng-template></mat-tab>
          <mat-tab label="Conciliação (OFX)"><ng-template matTabContent><tc-reconciliation-tab /></ng-template></mat-tab>
          <mat-tab label="Transferências"><ng-template matTabContent><tc-transfer-tab /></ng-template></mat-tab>
          <mat-tab label="Orçamento"><ng-template matTabContent><tc-budget-tab /></ng-template></mat-tab>
          <mat-tab label="Indicadores"><ng-template matTabContent><tc-insights-tab /></ng-template></mat-tab>
          <mat-tab label="Fechamento mensal"><ng-template matTabContent><tc-closing-tab /></ng-template></mat-tab>
          <mat-tab label="Configurações"><ng-template matTabContent><tc-settings-tab /></ng-template></mat-tab>
        </mat-tab-group>
      }
    </div>
  `,
})
export class FinancePage {
  protected readonly context = inject(CondominiumContext);
}

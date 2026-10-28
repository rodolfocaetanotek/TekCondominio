import { Component, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { CondominiumContext } from '../../core/context/condominium-context';
import { CondominiumRequired } from '../../shared/ui/condominium-required';
import { PageHeader } from '../../shared/ui/page-header';
import { BankTab } from './bank-tab';
import { BudgetTab } from './budget-tab';
import { ClosingTab } from './closing-tab';
import { InsightsTab } from './insights-tab';
import { ReconciliationTab } from './reconciliation-tab';
import { EntryList } from './entry-list';
import { SettingsTab } from './settings-tab';
import { TransferTab } from './transfer-tab';

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

import { Component, inject } from '@angular/core';
import { CondominiumContext } from '../../core/contexto/condominio-contexto';
import { EmptyState } from './estado-vazio';

/** Exibido nas telas por condomínio quando nenhum está selecionado/acessível. */
@Component({
  selector: 'tc-condominium-required',
  imports: [EmptyState],
  template: `
    <section class="tc-card">
      @if (context.loaded() && context.options().length === 0) {
        <tc-empty-state icon="apartment" title="Nenhum condomínio disponível"
          description="Cadastre um condomínio ou peça acesso a um condomínio para usar esta área." />
      } @else {
        <tc-empty-state icon="touch_app" title="Selecione um condomínio"
          description="Escolha o condomínio na barra superior para continuar." />
      }
    </section>
  `,
})
export class CondominiumRequired {
  protected readonly context = inject(CondominiumContext);
}

import { Component, inject } from '@angular/core';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { EstadoVazio } from './estado-vazio';

/** Exibido nas telas por condomínio quando nenhum está selecionado/acessível. */
@Component({
  selector: 'tc-condominio-obrigatorio',
  imports: [EstadoVazio],
  template: `
    <section class="tc-card">
      @if (contexto.carregado() && contexto.opcoes().length === 0) {
        <tc-estado-vazio icone="apartment" titulo="Nenhum condomínio disponível"
          descricao="Cadastre um condomínio ou peça acesso a um condomínio para usar esta área." />
      } @else {
        <tc-estado-vazio icone="touch_app" titulo="Selecione um condomínio"
          descricao="Escolha o condomínio na barra superior para continuar." />
      }
    </section>
  `,
})
export class CondominioObrigatorio {
  protected readonly contexto = inject(CondominioContexto);
}

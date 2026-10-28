package br.com.tekcondo.acesso.autorizacao;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcessoesUsuarioTest {

    private final UUID condoA = UUID.randomUUID();
    private final UUID condoB = UUID.randomUUID();

    @Test
    void concessaoDaAdministradoraValeParaTodoCondominio() {
        ConcessoesUsuario concessoes = new ConcessoesUsuario(List.of(new ConcessaoPermissao(null, Permissao.CONDOMINIUM_VIEW)));

        assertThat(concessoes.tem(Permissao.CONDOMINIUM_VIEW, condoA)).isTrue();
        assertThat(concessoes.tem(Permissao.CONDOMINIUM_VIEW, condoB)).isTrue();
        assertThat(concessoes.escopo(Permissao.CONDOMINIUM_VIEW).administradoraInteira()).isTrue();
        assertThat(concessoes.tem(Permissao.CONDOMINIUM_EDIT, condoA)).isFalse();
    }

    @Test
    void concessaoDeCondominioFicaRestritaAoCondominio() {
        ConcessoesUsuario concessoes = new ConcessoesUsuario(List.of(
                new ConcessaoPermissao(condoA, Permissao.CONDOMINIUM_VIEW),
                new ConcessaoPermissao(condoA, Permissao.CONDOMINIUM_EDIT),
                new ConcessaoPermissao(condoB, Permissao.CONDOMINIUM_VIEW)));

        assertThat(concessoes.tem(Permissao.CONDOMINIUM_EDIT, condoA)).isTrue();
        assertThat(concessoes.tem(Permissao.CONDOMINIUM_EDIT, condoB)).isFalse();
        assertThat(concessoes.tem(Permissao.CONDOMINIUM_VIEW, null)).isFalse();
        assertThat(concessoes.temNaAdministradora(Permissao.CONDOMINIUM_VIEW)).isFalse();

        EscopoAcesso escopo = concessoes.escopo(Permissao.CONDOMINIUM_VIEW);
        assertThat(escopo.administradoraInteira()).isFalse();
        assertThat(escopo.idsCondominio()).containsExactlyInAnyOrder(condoA, condoB);
        assertThat(concessoes.escopo(Permissao.CONDOMINIUM_EDIT).idsCondominio()).containsExactly(condoA);
    }

    @Test
    void semConcessoesEscopoVazio() {
        ConcessoesUsuario concessoes = new ConcessoesUsuario(List.of());
        assertThat(concessoes.escopo(Permissao.CONDOMINIUM_VIEW).isVazio()).isTrue();
    }
}

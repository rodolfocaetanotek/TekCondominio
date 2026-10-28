package br.com.tekcondo.condominium.service;

import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.stereotype.Service;

/**
 * Porta de entrada única dos módulos que operam dentro de um condomínio: garante, nesta ordem,
 * (1) que o condomínio pertence à administradora do usuário — senão 404 — e (2) que o usuário tem a permissão
 * nesse condomínio — senão 403. Nenhum identificador vindo do cliente é usado sem passar por aqui.
 */
@Service
public class CondominiumAccess {

    private final CondominiumRepository repository;
    private final AcessoService accessService;
    private final UsuarioAtualProvider currentUserProvider;

    public CondominiumAccess(CondominiumRepository repository, AcessoService accessService,
                             UsuarioAtualProvider currentUserProvider) {
        this.repository = repository;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
    }

    public Condominium require(UUID condominiumId, Permissao permission) {
        UsuarioAtual user = currentUserProvider.exigirMembroAdministradora();
        Condominium condominium = repository.findByIdAndOrganizationId(condominiumId, user.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
        accessService.exigir(permission, condominiumId);
        return condominium;
    }

    /** Para operações de escrita que não podem ocorrer em condomínio inativo (premissa P07). */
    public Condominium requireActive(UUID condominiumId, Permissao permission) {
        Condominium condominium = require(condominiumId, permission);
        if (condominium.getStatus() != CondominiumStatus.ACTIVE) {
            throw ApiException.regraNegocio("CONDOMINIUM_INACTIVE",
                    "O condomínio está inativo. Reative-o para registrar novas operações.");
        }
        return condominium;
    }

    public UsuarioAtual user() {
        return currentUserProvider.exigirMembroAdministradora();
    }

    public UUID organizationId() {
        return currentUserProvider.exigirMembroAdministradora().idAdministradora();
    }
}

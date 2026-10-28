package br.com.tekcondo.condominium.service;

import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.identity.access.AccessService;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUser;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;

/**
 * Porta de entrada única dos módulos que operam dentro de um condomínio: garante, nesta ordem,
 * (1) que o condomínio pertence à administradora do usuário — senão 404 — e (2) que o usuário tem a permissão
 * nesse condomínio — senão 403. Nenhum identificador vindo do cliente é usado sem passar por aqui.
 */
@Service
public class CondominiumAccess {

    private final CondominiumRepository repository;
    private final AccessService accessService;
    private final CurrentUserProvider currentUserProvider;

    public CondominiumAccess(CondominiumRepository repository, AccessService accessService,
                             CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.accessService = accessService;
        this.currentUserProvider = currentUserProvider;
    }

    public Condominium require(UUID condominiumId, Permission permission) {
        CurrentUser user = currentUserProvider.requireOrganizationMember();
        Condominium condominium = repository.findByIdAndOrganizationId(condominiumId, user.organizationId())
                .orElseThrow(() -> ApiException.notFound("Condomínio"));
        accessService.require(permission, condominiumId);
        return condominium;
    }

    /** Para operações de escrita que não podem ocorrer em condomínio inativo (premissa P07). */
    public Condominium requireActive(UUID condominiumId, Permission permission) {
        Condominium condominium = require(condominiumId, permission);
        if (condominium.getStatus() != CondominiumStatus.ACTIVE) {
            throw ApiException.businessRule("CONDOMINIUM_INACTIVE",
                    "O condomínio está inativo. Reative-o para registrar novas operações.");
        }
        return condominium;
    }

    public CurrentUser user() {
        return currentUserProvider.requireOrganizationMember();
    }

    public UUID organizationId() {
        return currentUserProvider.requireOrganizationMember().organizationId();
    }
}

package br.com.tekcondo.condominium.repository;

import java.util.Locale;
import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.identity.access.AccessScope;
import org.springframework.data.jpa.domain.Specification;

public final class CondominiumSpecifications {

    private CondominiumSpecifications() {
    }

    public static Specification<Condominium> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    /** Restringe aos condomínios que o usuário pode ver (quando não tem acesso à organização inteira). */
    public static Specification<Condominium> withinScope(AccessScope scope) {
        if (scope.organizationWide()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> root.get("id").in(scope.condominiumIds());
    }

    /** Busca por nome, razão social ou CNPJ (com ou sem máscara). */
    public static Specification<Condominium> matching(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        String like = "%" + escapeLike(term) + "%";
        String cnpjDigits = term.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like, '\\'),
                cb.like(cb.lower(root.get("legalName")), like, '\\'),
                cnpjDigits.isEmpty() ? cb.disjunction()
                        : cb.like(root.get("cnpj"), "%" + escapeLike(cnpjDigits) + "%", '\\'));
    }

    public static Specification<Condominium> withStatus(CondominiumStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

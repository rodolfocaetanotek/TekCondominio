package br.com.tekcondo.condominio.repository;

import java.util.Locale;
import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.acesso.autorizacao.EscopoAcesso;
import org.springframework.data.jpa.domain.Specification;

public final class CondominioSpecifications {

    private CondominioSpecifications() {
    }

    public static Specification<Condominio> naAdministradora(UUID idAdministradora) {
        return (root, query, cb) -> cb.equal(root.get("idAdministradora"), idAdministradora);
    }

    /** Restringe aos condomínios que o usuário pode ver (quando não tem acesso à administradora inteira). */
    public static Specification<Condominio> noEscopo(EscopoAcesso escopo) {
        if (escopo.administradoraInteira()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> root.get("id").in(escopo.idsCondominio());
    }

    /** Busca por nome, razão social ou CNPJ (com ou sem máscara). */
    public static Specification<Condominio> comBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        String termo = busca.trim().toLowerCase(Locale.ROOT);
        String like = "%" + escaparLike(termo) + "%";
        String digitosCnpj = termo.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nome")), like, '\\'),
                cb.like(cb.lower(root.get("razaoSocial")), like, '\\'),
                digitosCnpj.isEmpty() ? cb.disjunction()
                        : cb.like(root.get("cnpj"), "%" + escaparLike(digitosCnpj) + "%", '\\'));
    }

    public static Specification<Condominio> comSituacao(SituacaoCondominio situacao) {
        return situacao == null ? null : (root, query, cb) -> cb.equal(root.get("situacao"), situacao);
    }

    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

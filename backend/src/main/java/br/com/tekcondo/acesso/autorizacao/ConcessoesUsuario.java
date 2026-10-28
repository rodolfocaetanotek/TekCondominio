package br.com.tekcondo.acesso.autorizacao;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Permissões efetivas do usuário: as válidas para toda a organização e as restritas a cada condomínio. */
public final class ConcessoesUsuario {

    private final Set<Permissao> administradoraInteira = EnumSet.noneOf(Permissao.class);
    private final Map<UUID, Set<Permissao>> porCondominio = new HashMap<>();

    public ConcessoesUsuario(Collection<ConcessaoPermissao> concessoes) {
        for (ConcessaoPermissao concessao : concessoes) {
            if (concessao.idCondominio() == null) {
                administradoraInteira.add(concessao.permissao());
            } else {
                porCondominio.computeIfAbsent(concessao.idCondominio(), k -> EnumSet.noneOf(Permissao.class))
                        .add(concessao.permissao());
            }
        }
    }

    public boolean temNaAdministradora(Permissao permissao) {
        return administradoraInteira.contains(permissao);
    }

    public boolean tem(Permissao permissao, UUID idCondominio) {
        if (administradoraInteira.contains(permissao)) {
            return true;
        }
        return idCondominio != null && porCondominio.getOrDefault(idCondominio, Set.of()).contains(permissao);
    }

    public EscopoAcesso escopo(Permissao permissao) {
        if (administradoraInteira.contains(permissao)) {
            return new EscopoAcesso(true, Set.of());
        }
        Set<UUID> ids = porCondominio.entrySet().stream()
                .filter(e -> e.getValue().contains(permissao))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        return new EscopoAcesso(false, ids);
    }

    public Set<Permissao> administradoraInteira() {
        return Set.copyOf(administradoraInteira);
    }

    public Map<UUID, Set<Permissao>> porCondominio() {
        return Map.copyOf(porCondominio);
    }
}

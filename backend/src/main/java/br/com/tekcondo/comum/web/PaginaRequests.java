package br.com.tekcondo.comum.web;

import java.util.Map;

import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Monta {@link Pageable} a partir de parâmetros HTTP, aceitando apenas campos de ordenação em lista branca. */
public final class PaginaRequests {

    public static final int TAMANHO_MAXIMO = 100;

    private PaginaRequests() {
    }

    /**
     * @param ordem            formato {@code campo,asc|desc}
     * @param ordensPermitidas mapa nome-público → propriedade da entidade
     */
    public static Pageable of(int pagina, int tamanho, String ordem, Map<String, String> ordensPermitidas, Sort ordemPadrao) {
        if (pagina < 0) {
            throw ApiException.requisicaoInvalida("INVALID_PAGE", "O número da página não pode ser negativo.");
        }
        int tamanhoLimitado = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        Sort resolvida = ordemPadrao;
        if (ordem != null && !ordem.isBlank()) {
            String[] partes = ordem.split(",");
            String propriedade = ordensPermitidas.get(partes[0].trim());
            if (propriedade == null) {
                throw ApiException.requisicaoInvalida("INVALID_SORT", "Campo de ordenação não permitido: " + partes[0].trim());
            }
            Sort.Direction direcao = partes.length > 1 && "desc".equalsIgnoreCase(partes[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            resolvida = Sort.by(direcao, propriedade).and(ordemPadrao);
        }
        return PageRequest.of(pagina, tamanhoLimitado, resolvida);
    }
}

package br.com.tekcondo.shared.web;

import java.util.Map;

import br.com.tekcondo.shared.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Monta {@link Pageable} a partir de parâmetros HTTP, aceitando apenas campos de ordenação em lista branca. */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param sort         formato {@code campo,asc|desc}
     * @param allowedSorts mapa nome-público → propriedade da entidade
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSorts, Sort defaultSort) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "O número da página não pode ser negativo.");
        }
        int boundedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Sort resolved = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = allowedSorts.get(parts[0].trim());
            if (property == null) {
                throw ApiException.badRequest("INVALID_SORT", "Campo de ordenação não permitido: " + parts[0].trim());
            }
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            resolved = Sort.by(direction, property).and(defaultSort);
        }
        return PageRequest.of(page, boundedSize, resolved);
    }
}

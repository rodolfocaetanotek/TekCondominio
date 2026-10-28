package br.com.tekcondo.comum.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Envelope estável de paginação (não expõe a estrutura interna do Spring Data). */
public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> of(Page<E> pagina, Function<E, T> mapeador) {
        return new PaginaResponse<>(pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}

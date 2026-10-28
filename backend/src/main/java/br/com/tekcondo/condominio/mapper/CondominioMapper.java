package br.com.tekcondo.condominio.mapper;

import java.util.Locale;

import br.com.tekcondo.condominio.dto.CondominioResponse;
import br.com.tekcondo.condominio.dto.CondominioResumo;
import br.com.tekcondo.condominio.dto.EnderecoDto;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.Endereco;
import br.com.tekcondo.comum.validation.DocumentosBrasileiros;
import org.springframework.stereotype.Component;

/** Conversão entidade ↔ DTO e normalização de entradas (máscaras removidas, textos aparados). */
@Component
public class CondominioMapper {

    public CondominioResponse toResponse(Condominio c) {
        Endereco e = c.getEndereco();
        EnderecoDto endereco = e == null ? null
                : new EnderecoDto(e.cep(), e.logradouro(), e.numero(), e.complemento(), e.bairro(), e.cidade(), e.uf());
        return new CondominioResponse(c.getId(), c.getNome(), c.getRazaoSocial(), c.getCnpj(), c.getTipo(), endereco,
                c.getEmail(), c.getTelefone(), c.getSituacao(), c.getInativadoEm(), c.getMotivoInativacao(),
                c.getCriadoEm(), c.getAtualizadoEm(), c.getVersao());
    }

    public CondominioResumo toResumo(Condominio c) {
        Endereco e = c.getEndereco();
        return new CondominioResumo(c.getId(), c.getNome(), c.getCnpj(), c.getTipo(),
                e == null ? null : e.cidade(), e == null ? null : e.uf(), c.getSituacao());
    }

    public Endereco toEndereco(EnderecoDto dto) {
        if (dto == null) {
            return null;
        }
        Endereco endereco = new Endereco(DocumentosBrasileiros.somenteDigitos(dto.cep()), aparar(dto.logradouro()),
                aparar(dto.numero()), aparar(dto.complemento()), aparar(dto.bairro()), aparar(dto.cidade()),
                dto.uf() == null || dto.uf().isBlank() ? null : dto.uf().trim().toUpperCase(Locale.ROOT));
        boolean vazio = endereco.cep() == null && endereco.logradouro() == null && endereco.numero() == null
                && endereco.complemento() == null && endereco.bairro() == null && endereco.cidade() == null
                && endereco.uf() == null;
        return vazio ? null : endereco;
    }

    public static String aparar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

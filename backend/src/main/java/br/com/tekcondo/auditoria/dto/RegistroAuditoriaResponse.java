package br.com.tekcondo.auditoria.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.entity.RegistroAuditoria;

public record RegistroAuditoriaResponse(UUID id, Instant ocorridoEm, UUID idUsuario, String emailUsuario, String acao,
                                        String tipoEntidade, String idEntidade, Map<String, Object> detalhes, String enderecoIp) {

    public static RegistroAuditoriaResponse de(RegistroAuditoria registro) {
        return new RegistroAuditoriaResponse(registro.getId(), registro.getOcorridoEm(), registro.getIdUsuario(), registro.getEmailUsuario(),
                registro.getAcao(), registro.getTipoEntidade(), registro.getIdEntidade(), registro.getDetalhes(), registro.getEnderecoIp());
    }
}

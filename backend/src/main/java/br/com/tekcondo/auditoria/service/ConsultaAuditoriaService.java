package br.com.tekcondo.auditoria.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.tekcondo.auditoria.dto.RegistroAuditoriaResponse;
import br.com.tekcondo.auditoria.entity.RegistroAuditoria;
import br.com.tekcondo.auditoria.repository.RegistroAuditoriaRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import br.com.tekcondo.comum.web.PaginaResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaAuditoriaService {

    private final RegistroAuditoriaRepository repository;
    private final AcessoService acessoService;
    private final UsuarioAtualProvider usuarioAtualProvider;

    public ConsultaAuditoriaService(RegistroAuditoriaRepository repository, AcessoService acessoService,
                             UsuarioAtualProvider usuarioAtualProvider) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.usuarioAtualProvider = usuarioAtualProvider;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<RegistroAuditoriaResponse> listar(String acao, String tipoEntidade, String idEntidade, Instant de,
                                               Instant ate, Pageable pageable) {
        var idAdministradora = usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
        acessoService.exigirNaAdministradora(Permissao.AUDIT_VIEW);
        Specification<RegistroAuditoria> spec = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.equal(root.get("idAdministradora"), idAdministradora));
            if (acao != null && !acao.isBlank()) {
                predicados.add(cb.equal(root.get("acao"), acao.trim()));
            }
            if (tipoEntidade != null && !tipoEntidade.isBlank()) {
                predicados.add(cb.equal(root.get("tipoEntidade"), tipoEntidade.trim()));
            }
            if (idEntidade != null && !idEntidade.isBlank()) {
                predicados.add(cb.equal(root.get("idEntidade"), idEntidade.trim()));
            }
            if (de != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("ocorridoEm"), de));
            }
            if (ate != null) {
                predicados.add(cb.lessThan(root.get("ocorridoEm"), ate));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(repository.findAll(spec, pageable), RegistroAuditoriaResponse::de);
    }
}

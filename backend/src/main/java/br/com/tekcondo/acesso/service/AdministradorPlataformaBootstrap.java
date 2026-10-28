package br.com.tekcondo.acesso.service;

import java.util.Map;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.comum.config.TekCondoProperties;
import br.com.tekcondo.comum.validation.SenhaForte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o administrador da plataforma na primeira execução, a partir de variáveis de ambiente
 * (TEKCONDO_BOOTSTRAP_ADMIN_EMAIL / TEKCONDO_BOOTSTRAP_ADMIN_PASSWORD). Nenhuma credencial fica no código.
 */
@Component
public class AdministradorPlataformaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorPlataformaBootstrap.class);

    private final TekCondoProperties props;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public AdministradorPlataformaBootstrap(TekCondoProperties props, UsuarioRepository usuarioRepository,
                                  PasswordEncoder passwordEncoder, AuditoriaService auditoriaService) {
        this.props = props;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TekCondoProperties.Bootstrap cfg = props.bootstrap();
        if (cfg == null || isVazio(cfg.adminEmail()) || isVazio(cfg.adminPassword())) {
            return;
        }
        if (usuarioRepository.existePorEmail(cfg.adminEmail())) {
            log.debug("Administrador da plataforma já existe; bootstrap ignorado.");
            return;
        }
        if (!SenhaForte.Validator.isForte(cfg.adminPassword())) {
            throw new IllegalStateException("TEKCONDO_BOOTSTRAP_ADMIN_PASSWORD não atende à política de senha "
                    + "(mín. 10 caracteres, letras e números).");
        }
        String nome = isVazio(cfg.adminName()) ? "Administrador da Plataforma" : cfg.adminName();
        Usuario administrador = usuarioRepository.save(Usuario.administradorPlataforma(nome, cfg.adminEmail(),
                passwordEncoder.encode(cfg.adminPassword())));
        auditoriaService.registrarComo(null, null, "system", AcaoAuditoria.PLATFORM_ADMIN_BOOTSTRAPPED, "User", administrador.getId(),
                Map.of("email", administrador.getEmail()));
        log.info("Administrador da plataforma criado: {}", administrador.getEmail());
    }

    private static boolean isVazio(String s) {
        return s == null || s.isBlank();
    }
}

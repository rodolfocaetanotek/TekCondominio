package br.com.tekcondo.identity.service;

import java.util.Map;

import br.com.tekcondo.audit.service.AuditAction;
import br.com.tekcondo.audit.service.AuditService;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.shared.config.TekCondoProperties;
import br.com.tekcondo.shared.validation.StrongPassword;
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
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final TekCondoProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PlatformAdminBootstrap(TekCondoProperties props, UserRepository userRepository,
                                  PasswordEncoder passwordEncoder, AuditService auditService) {
        this.props = props;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TekCondoProperties.Bootstrap cfg = props.bootstrap();
        if (cfg == null || isBlank(cfg.adminEmail()) || isBlank(cfg.adminPassword())) {
            return;
        }
        if (userRepository.existsByEmail(cfg.adminEmail())) {
            log.debug("Administrador da plataforma já existe; bootstrap ignorado.");
            return;
        }
        if (!StrongPassword.Validator.isStrong(cfg.adminPassword())) {
            throw new IllegalStateException("TEKCONDO_BOOTSTRAP_ADMIN_PASSWORD não atende à política de senha "
                    + "(mín. 10 caracteres, letras e números).");
        }
        String name = isBlank(cfg.adminName()) ? "Administrador da Plataforma" : cfg.adminName();
        User admin = userRepository.save(User.platformAdmin(name, cfg.adminEmail(),
                passwordEncoder.encode(cfg.adminPassword())));
        auditService.recordAs(null, null, "system", AuditAction.PLATFORM_ADMIN_BOOTSTRAPPED, "User", admin.getId(),
                Map.of("email", admin.getEmail()));
        log.info("Administrador da plataforma criado: {}", admin.getEmail());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

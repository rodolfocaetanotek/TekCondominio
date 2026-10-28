package br.com.tekcondo.support;

import java.util.UUID;

import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumType;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
import br.com.tekcondo.identity.entity.Role;
import br.com.tekcondo.identity.entity.User;
import br.com.tekcondo.identity.entity.UserRole;
import br.com.tekcondo.identity.repository.RoleRepository;
import br.com.tekcondo.identity.repository.UserRepository;
import br.com.tekcondo.identity.repository.UserRoleRepository;
import br.com.tekcondo.organization.entity.Organization;
import br.com.tekcondo.organization.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Fábrica de dados de teste: cria cenários diretamente pelos repositórios e autentica pela API real. */

public class TestData {

    public static final String PASSWORD = "SenhaForte123";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final CondominiumRepository condominiumRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final MockMvc mvc;

    public TestData(OrganizationRepository organizationRepository, UserRepository userRepository,
                    RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                    CondominiumRepository condominiumRepository, PasswordEncoder passwordEncoder,
                    ObjectMapper objectMapper, MockMvc mvc) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.condominiumRepository = condominiumRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.mvc = mvc;
    }

    public Organization organization() {
        return organizationRepository.save(new Organization("Administradora " + shortId(), Cnpjs.random(), null, null));
    }

    public User user(Organization org) {
        return userRepository.save(User.organizationMember(org.getId(), "Usuário " + shortId(), uniqueEmail(),
                passwordEncoder.encode(PASSWORD)));
    }

    public User platformAdmin() {
        return userRepository.save(User.platformAdmin("Admin " + shortId(), uniqueEmail(), passwordEncoder.encode(PASSWORD)));
    }

    /** Atribui papel do sistema; condominiumId nulo = toda a organização. */
    public void grant(User user, String roleCode, UUID condominiumId) {
        Role role = roleRepository.findSystemRole(roleCode).orElseThrow();
        userRoleRepository.save(new UserRole(user.getId(), role, user.getOrganizationId(), condominiumId, null));
    }

    public User orgAdmin(Organization org) {
        User user = user(org);
        grant(user, Role.ORG_ADMIN, null);
        return user;
    }

    public Condominium condominium(Organization org, String name) {
        Condominium c = new Condominium(org.getId(), null);
        c.updateDetails(name, null, null, CondominiumType.RESIDENTIAL, null, null, null, null);
        return condominiumRepository.save(c);
    }

    /** Autentica pela API e devolve o access token. */
    public String login(User user) {
        try {
            MvcResult result = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginBody(user.getEmail(), PASSWORD))))
                    .andReturn();
            if (result.getResponse().getStatus() != 200) {
                throw new IllegalStateException("Login falhou: " + result.getResponse().getContentAsString());
            }
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            return body.get("accessToken").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String uniqueEmail() {
        return "u" + shortId() + "@teste.tekcondo";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record LoginBody(String email, String password) {
    }
}

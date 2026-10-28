package br.com.tekcondo.support;

import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.TipoCondominio;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.acesso.entity.Papel;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.entity.PapelUsuario;
import br.com.tekcondo.acesso.repository.PapelRepository;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.administradora.entity.Administradora;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Fábrica de dados de teste: cria cenários diretamente pelos repositórios e autentica pela API real. */

public class TestData {

    public static final String SENHA = "SenhaForte123";

    private final AdministradoraRepository administradoraRepository;
    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final PapelUsuarioRepository papelUsuarioRepository;
    private final CondominioRepository condominioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final MockMvc mvc;

    public TestData(AdministradoraRepository administradoraRepository, UsuarioRepository usuarioRepository,
                    PapelRepository papelRepository, PapelUsuarioRepository papelUsuarioRepository,
                    CondominioRepository condominioRepository, PasswordEncoder passwordEncoder,
                    ObjectMapper objectMapper, MockMvc mvc) {
        this.administradoraRepository = administradoraRepository;
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.condominioRepository = condominioRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.mvc = mvc;
    }

    public Administradora administradora() {
        return administradoraRepository.save(new Administradora("Administradora " + idCurto(), Cnpjs.random(), null, null));
    }

    public Usuario usuario(Administradora administradora) {
        return usuarioRepository.save(Usuario.membroAdministradora(administradora.getId(), "Usuário " + idCurto(), emailUnico(),
                passwordEncoder.encode(SENHA)));
    }

    public Usuario administradorPlataforma() {
        return usuarioRepository.save(Usuario.administradorPlataforma("Admin " + idCurto(), emailUnico(), passwordEncoder.encode(SENHA)));
    }

    /** Atribui papel do sistema; idCondominio nulo = toda a administradora. */
    public void conceder(Usuario usuario, String codigoPapel, UUID idCondominio) {
        Papel papel = papelRepository.buscarPapelSistema(codigoPapel).orElseThrow();
        papelUsuarioRepository.save(new PapelUsuario(usuario.getId(), papel, usuario.getIdAdministradora(), idCondominio, null));
    }

    public Usuario adminAdministradora(Administradora administradora) {
        Usuario usuario = usuario(administradora);
        conceder(usuario, Papel.ORG_ADMIN, null);
        return usuario;
    }

    public Condominio condominio(Administradora administradora, String nome) {
        Condominio c = new Condominio(administradora.getId(), null);
        c.atualizarDados(nome, null, null, TipoCondominio.RESIDENTIAL, null, null, null, null);
        return condominioRepository.save(c);
    }

    /** Autentica pela API e devolve o access token. */
    public String login(Usuario usuario) {
        try {
            MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginBody(usuario.getEmail(), SENHA))))
                    .andReturn();
            if (resultado.getResponse().getStatus() != 200) {
                throw new IllegalStateException("Login falhou: " + resultado.getResponse().getContentAsString());
            }
            JsonNode corpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
            return corpo.get("accessToken").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String emailUnico() {
        return "u" + idCurto() + "@teste.tekcondo";
    }

    private static String idCurto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record LoginBody(String email, String senha) {
    }
}

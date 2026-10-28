package br.com.tekcondo.acesso.service;

import java.util.Map;
import java.util.Set;

import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.ConcessoesUsuario;
import br.com.tekcondo.acesso.dto.PerfilResponse;
import br.com.tekcondo.acesso.entity.Usuario;
import br.com.tekcondo.acesso.repository.UsuarioRepository;
import br.com.tekcondo.administradora.repository.AdministradoraRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilService {

    private final UsuarioAtualProvider usuarioAtualProvider;
    private final UsuarioRepository usuarioRepository;
    private final AdministradoraRepository administradoraRepository;
    private final AcessoService acessoService;

    public PerfilService(UsuarioAtualProvider usuarioAtualProvider, UsuarioRepository usuarioRepository,
                          AdministradoraRepository administradoraRepository, AcessoService acessoService) {
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.usuarioRepository = usuarioRepository;
        this.administradoraRepository = administradoraRepository;
        this.acessoService = acessoService;
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil() {
        UsuarioAtual atual = usuarioAtualProvider.obter();
        Usuario usuario = usuarioRepository.findById(atual.idUsuario())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        if (usuario.getIdAdministradora() == null) {
            return new PerfilResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.isAdministradorPlataforma(), null, null,
                    Set.of(), Map.of());
        }
        String nomeAdministradora = administradoraRepository.findById(usuario.getIdAdministradora()).map(o -> o.getNome()).orElse(null);
        ConcessoesUsuario concessoes = acessoService.concessoesAtuais();
        return new PerfilResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.isAdministradorPlataforma(),
                usuario.getIdAdministradora(), nomeAdministradora, concessoes.administradoraInteira(), concessoes.porCondominio());
    }
}

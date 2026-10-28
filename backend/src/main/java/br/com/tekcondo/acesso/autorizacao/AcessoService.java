package br.com.tekcondo.acesso.autorizacao;

import java.util.UUID;

import br.com.tekcondo.acesso.repository.PapelUsuarioRepository;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Ponto único de autorização de negócio. As permissões são lidas do banco (não do token), garantindo que
 * revogações e inativações tenham efeito imediato; o resultado é memorizado durante a requisição.
 */
@Service
public class AcessoService {

    private static final String ATRIBUTO_CONCESSOES = AcessoService.class.getName() + ".concessoes";

    private final PapelUsuarioRepository papelUsuarioRepository;
    private final UsuarioAtualProvider usuarioAtualProvider;

    public AcessoService(PapelUsuarioRepository papelUsuarioRepository, UsuarioAtualProvider usuarioAtualProvider) {
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.usuarioAtualProvider = usuarioAtualProvider;
    }

    public ConcessoesUsuario concessoesAtuais() {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getAttribute(ATRIBUTO_CONCESSOES, RequestAttributes.SCOPE_REQUEST) instanceof ConcessoesUsuario emCache) {
            return emCache;
        }
        ConcessoesUsuario concessoes = new ConcessoesUsuario(papelUsuarioRepository.buscarConcessoes(usuario.idUsuario(), usuario.idAdministradora()));
        if (attrs != null) {
            attrs.setAttribute(ATRIBUTO_CONCESSOES, concessoes, RequestAttributes.SCOPE_REQUEST);
        }
        return concessoes;
    }

    /** Exige a permissão no condomínio informado (ou na organização inteira). */
    public void exigir(Permissao permissao, UUID idCondominio) {
        if (!concessoesAtuais().tem(permissao, idCondominio)) {
            throw new AccessDeniedException("Permissão negada: " + permissao);
        }
    }

    /** Exige a permissão válida para toda a organização (ex.: cadastrar novo condomínio). */
    public void exigirNaAdministradora(Permissao permissao) {
        if (!concessoesAtuais().temNaAdministradora(permissao)) {
            throw new AccessDeniedException("Permissão negada: " + permissao);
        }
    }

    /** Escopo para listagens; lança 403 se o usuário não tiver a permissão em nenhum condomínio. */
    public EscopoAcesso exigirEscopo(Permissao permissao) {
        EscopoAcesso escopo = concessoesAtuais().escopo(permissao);
        if (escopo.isVazio()) {
            throw new AccessDeniedException("Permissão negada: " + permissao);
        }
        return escopo;
    }
}

package br.com.tekcondo.condominio.service;

import java.util.UUID;

import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.springframework.stereotype.Service;

/**
 * Porta de entrada única dos módulos que operam dentro de um condomínio: garante, nesta ordem,
 * (1) que o condomínio pertence à administradora do usuário — senão 404 — e (2) que o usuário tem a permissão
 * nesse condomínio — senão 403. Nenhum identificador vindo do cliente é usado sem passar por aqui.
 */
@Service
public class AcessoCondominio {

    private final CondominioRepository repository;
    private final AcessoService acessoService;
    private final UsuarioAtualProvider usuarioAtualProvider;

    public AcessoCondominio(CondominioRepository repository, AcessoService acessoService,
                            UsuarioAtualProvider usuarioAtualProvider) {
        this.repository = repository;
        this.acessoService = acessoService;
        this.usuarioAtualProvider = usuarioAtualProvider;
    }

    public Condominio exigir(UUID idCondominio, Permissao permissao) {
        UsuarioAtual usuario = usuarioAtualProvider.exigirMembroAdministradora();
        Condominio condominio = repository.findByIdAndIdAdministradora(idCondominio, usuario.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Condomínio"));
        acessoService.exigir(permissao, idCondominio);
        return condominio;
    }

    /** Para operações de escrita que não podem ocorrer em condomínio inativo (premissa P07). */
    public Condominio exigirAtivo(UUID idCondominio, Permissao permissao) {
        Condominio condominio = exigir(idCondominio, permissao);
        if (condominio.getSituacao() != SituacaoCondominio.ACTIVE) {
            throw ApiException.regraNegocio("CONDOMINIUM_INACTIVE",
                    "O condomínio está inativo. Reative-o para registrar novas operações.");
        }
        return condominio;
    }

    public UsuarioAtual usuario() {
        return usuarioAtualProvider.exigirMembroAdministradora();
    }

    public UUID idAdministradora() {
        return usuarioAtualProvider.exigirMembroAdministradora().idAdministradora();
    }
}

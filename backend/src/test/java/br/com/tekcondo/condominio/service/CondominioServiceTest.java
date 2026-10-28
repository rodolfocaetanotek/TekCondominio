package br.com.tekcondo.condominio.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.dto.CondominioRequest;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.entity.SituacaoCondominio;
import br.com.tekcondo.condominio.entity.TipoCondominio;
import br.com.tekcondo.condominio.mapper.CondominioMapper;
import br.com.tekcondo.condominio.repository.CondominioRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.security.UsuarioAtual;
import br.com.tekcondo.comum.security.UsuarioAtualProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CondominioServiceTest {

    @Mock
    CondominioRepository repository;
    @Mock
    AcessoService acessoService;
    @Mock
    UsuarioAtualProvider usuarioAtualProvider;
    @Mock
    AuditoriaService auditoriaService;

    CondominioService service;

    final UUID idAdministradora = UUID.randomUUID();
    final UsuarioAtual usuario = new UsuarioAtual(UUID.randomUUID(), idAdministradora, "u@x.com", "U", false);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
        service = new CondominioService(repository, new CondominioMapper(), acessoService, usuarioAtualProvider,
                auditoriaService, clock);
        when(usuarioAtualProvider.exigirMembroAdministradora()).thenReturn(usuario);
    }

    private static CondominioRequest request(String cnpj, Long versao) {
        return new CondominioRequest("Residencial Aurora", null, cnpj, TipoCondominio.RESIDENTIAL, null, null, null,
                versao);
    }

    private Condominio existente(UUID id) {
        Condominio c = new Condominio(idAdministradora, usuario.idUsuario());
        c.atualizarDados("Residencial Aurora", null, null, TipoCondominio.RESIDENTIAL, null, null, null, null);
        when(repository.findByIdAndIdAdministradora(id, idAdministradora)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void cadastroExigePermissaoNaAdministradoraInteira() {
        doThrow(new AccessDeniedException("x")).when(acessoService)
                .exigirNaAdministradora(Permissao.CONDOMINIUM_CREATE);

        assertThatThrownBy(() -> service.cadastrar(request(null, null))).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cadastroRejeitaCnpjDuplicadoNaMesmaAdministradora() {
        when(repository.existsByIdAdministradoraAndCnpj(idAdministradora, "11222333000181")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request("11.222.333/0001-81", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCodigo()).isEqualTo("CONDOMINIUM_CNPJ_TAKEN");
                });
    }

    @Test
    void cadastroNormalizaEAudita() {
        var response = service.cadastrar(request("11.222.333/0001-81", null));

        assertThat(response.cnpj()).isEqualTo("11222333000181");
        assertThat(response.situacao()).isEqualTo(SituacaoCondominio.ACTIVE);
        verify(repository).save(any(Condominio.class));
        verify(auditoriaService).registrar(any(), any(), any(), any());
    }

    @Test
    void atualizacaoComVersaoObsoletaERejeitada() {
        UUID id = UUID.randomUUID();
        existente(id); // versão nula (não persistida) ≠ 7

        assertThatThrownBy(() -> service.atualizar(id, request(null, 7L)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("STALE_VERSION"));
    }

    @Test
    void atualizacaoExigeVersao() {
        UUID id = UUID.randomUUID();
        existente(id);

        assertThatThrownBy(() -> service.atualizar(id, request(null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("VERSION_REQUIRED"));
    }

    @Test
    void condominioDeOutraAdministradoraNaoEEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndIdAdministradora(id, idAdministradora)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void naoInativaDuasVezes() {
        UUID id = UUID.randomUUID();
        Condominio c = existente(id);
        c.inativar("Encerramento do contrato", Instant.now(), usuario.idUsuario());

        assertThatThrownBy(() -> service.inativar(id, "Outra justificativa"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CONDOMINIUM_ALREADY_INACTIVE"));
    }

    @Test
    void inativacaoRegistraMotivoEData() {
        UUID id = UUID.randomUUID();
        existente(id);

        var response = service.inativar(id, "  Encerramento do contrato  ");

        assertThat(response.situacao()).isEqualTo(SituacaoCondominio.INACTIVE);
        assertThat(response.motivoInativacao()).isEqualTo("Encerramento do contrato");
        assertThat(response.inativadoEm()).isEqualTo(Instant.parse("2026-10-27T12:00:00Z"));
    }
}

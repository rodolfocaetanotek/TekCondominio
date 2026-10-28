package br.com.tekcondo.condominium.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominium.dto.CondominiumRequest;
import br.com.tekcondo.condominium.entity.Condominium;
import br.com.tekcondo.condominium.entity.CondominiumStatus;
import br.com.tekcondo.condominium.entity.CondominiumType;
import br.com.tekcondo.condominium.mapper.CondominiumMapper;
import br.com.tekcondo.condominium.repository.CondominiumRepository;
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
class CondominiumServiceTest {

    @Mock
    CondominiumRepository repository;
    @Mock
    AcessoService accessService;
    @Mock
    UsuarioAtualProvider currentUserProvider;
    @Mock
    AuditoriaService auditService;

    CondominiumService service;

    final UUID orgId = UUID.randomUUID();
    final UsuarioAtual user = new UsuarioAtual(UUID.randomUUID(), orgId, "u@x.com", "U", false);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
        service = new CondominiumService(repository, new CondominiumMapper(), accessService, currentUserProvider,
                auditService, clock);
        when(currentUserProvider.exigirMembroAdministradora()).thenReturn(user);
    }

    private static CondominiumRequest request(String cnpj, Long version) {
        return new CondominiumRequest("Residencial Aurora", null, cnpj, CondominiumType.RESIDENTIAL, null, null, null,
                version);
    }

    private Condominium existing(UUID id) {
        Condominium c = new Condominium(orgId, user.idUsuario());
        c.updateDetails("Residencial Aurora", null, null, CondominiumType.RESIDENTIAL, null, null, null, null);
        when(repository.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void createRequiresOrganizationWidePermission() {
        doThrow(new AccessDeniedException("x")).when(accessService)
                .exigirNaAdministradora(Permissao.CONDOMINIUM_CREATE);

        assertThatThrownBy(() -> service.create(request(null, null))).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCnpjInSameOrganization() {
        when(repository.existsByOrganizationIdAndCnpj(orgId, "11222333000181")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("11.222.333/0001-81", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCodigo()).isEqualTo("CONDOMINIUM_CNPJ_TAKEN");
                });
    }

    @Test
    void createNormalizesAndAudits() {
        var response = service.create(request("11.222.333/0001-81", null));

        assertThat(response.cnpj()).isEqualTo("11222333000181");
        assertThat(response.status()).isEqualTo(CondominiumStatus.ACTIVE);
        verify(repository).save(any(Condominium.class));
        verify(auditService).registrar(any(), any(), any(), any());
    }

    @Test
    void updateWithStaleVersionIsRejected() {
        UUID id = UUID.randomUUID();
        existing(id); // versão nula (não persistida) ≠ 7

        assertThatThrownBy(() -> service.update(id, request(null, 7L)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("STALE_VERSION"));
    }

    @Test
    void updateRequiresVersion() {
        UUID id = UUID.randomUUID();
        existing(id);

        assertThatThrownBy(() -> service.update(id, request(null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCodigo()).isEqualTo("VERSION_REQUIRED"));
    }

    @Test
    void condominiumFromAnotherOrganizationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void cannotInactivateTwice() {
        UUID id = UUID.randomUUID();
        Condominium c = existing(id);
        c.inactivate("Encerramento do contrato", Instant.now(), user.idUsuario());

        assertThatThrownBy(() -> service.inactivate(id, "Outra justificativa"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CONDOMINIUM_ALREADY_INACTIVE"));
    }

    @Test
    void inactivationRecordsReasonAndDate() {
        UUID id = UUID.randomUUID();
        existing(id);

        var response = service.inactivate(id, "  Encerramento do contrato  ");

        assertThat(response.status()).isEqualTo(CondominiumStatus.INACTIVE);
        assertThat(response.inactivationReason()).isEqualTo("Encerramento do contrato");
        assertThat(response.inactivatedAt()).isEqualTo(Instant.parse("2026-10-27T12:00:00Z"));
    }
}

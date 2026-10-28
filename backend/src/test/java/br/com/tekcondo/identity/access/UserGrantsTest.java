package br.com.tekcondo.identity.access;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserGrantsTest {

    private final UUID condoA = UUID.randomUUID();
    private final UUID condoB = UUID.randomUUID();

    @Test
    void organizationWideGrantAppliesToEveryCondominium() {
        UserGrants grants = new UserGrants(List.of(new PermissionGrant(null, Permission.CONDOMINIUM_VIEW)));

        assertThat(grants.has(Permission.CONDOMINIUM_VIEW, condoA)).isTrue();
        assertThat(grants.has(Permission.CONDOMINIUM_VIEW, condoB)).isTrue();
        assertThat(grants.scope(Permission.CONDOMINIUM_VIEW).organizationWide()).isTrue();
        assertThat(grants.has(Permission.CONDOMINIUM_EDIT, condoA)).isFalse();
    }

    @Test
    void condominiumGrantIsRestrictedToThatCondominium() {
        UserGrants grants = new UserGrants(List.of(
                new PermissionGrant(condoA, Permission.CONDOMINIUM_VIEW),
                new PermissionGrant(condoA, Permission.CONDOMINIUM_EDIT),
                new PermissionGrant(condoB, Permission.CONDOMINIUM_VIEW)));

        assertThat(grants.has(Permission.CONDOMINIUM_EDIT, condoA)).isTrue();
        assertThat(grants.has(Permission.CONDOMINIUM_EDIT, condoB)).isFalse();
        assertThat(grants.has(Permission.CONDOMINIUM_VIEW, null)).isFalse();
        assertThat(grants.hasOrganizationWide(Permission.CONDOMINIUM_VIEW)).isFalse();

        AccessScope scope = grants.scope(Permission.CONDOMINIUM_VIEW);
        assertThat(scope.organizationWide()).isFalse();
        assertThat(scope.condominiumIds()).containsExactlyInAnyOrder(condoA, condoB);
        assertThat(grants.scope(Permission.CONDOMINIUM_EDIT).condominiumIds()).containsExactly(condoA);
    }

    @Test
    void noGrantsMeansEmptyScope() {
        UserGrants grants = new UserGrants(List.of());
        assertThat(grants.scope(Permission.CONDOMINIUM_VIEW).isEmpty()).isTrue();
    }
}

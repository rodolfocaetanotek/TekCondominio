package br.com.tekcondo.organization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrganizationUser(UUID id, String name, String email, boolean active, boolean pendingInvite,
                               Instant lastLoginAt, List<String> roles) {
}

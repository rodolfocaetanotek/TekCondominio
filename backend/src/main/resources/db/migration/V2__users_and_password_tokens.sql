-- =====================================================================
-- V2 — Gestão de usuários: escopo dos papéis e tokens de senha (convite / redefinição)
-- =====================================================================

-- Escopo do papel: ORGANIZATION (vale para toda a administradora) ou CONDOMINIUM (exige condomínio).
alter table role add column scope varchar(20) not null default 'CONDOMINIUM';
update role set scope = 'ORGANIZATION' where code in ('ORG_ADMIN', 'ORG_STAFF');
alter table role add constraint ck_role_scope check (scope in ('ORGANIZATION', 'CONDOMINIUM'));

-- Funcionário da administradora passa a consultar usuários (sem gerenciá-los).
insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000002', 'USER_VIEW');

-- Tokens de uso único para definir/redefinir senha. Apenas o hash SHA-256 é armazenado.
create table password_token (
    id          uuid primary key,
    user_id     uuid         not null references app_user (id),
    purpose     varchar(20)  not null,
    token_hash  varchar(64)  not null,
    expires_at  timestamptz  not null,
    used_at     timestamptz,
    created_at  timestamptz  not null,
    constraint ux_password_token_hash unique (token_hash),
    constraint ck_password_token_purpose check (purpose in ('INVITE', 'RESET'))
);
create index ix_password_token_user on password_token (user_id, created_at desc);

create index ix_user_role_org on user_role (organization_id);
create index ix_app_user_org_name on app_user (organization_id, lower(name));

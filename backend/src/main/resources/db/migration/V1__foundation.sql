-- =====================================================================
-- V1 — Fundação: organizações, identidade/permissões, condomínios, auditoria
-- =====================================================================

create table organization (
    id          uuid primary key,
    name        varchar(150) not null,
    cnpj        varchar(14)  not null,
    email       varchar(254),
    phone       varchar(20),
    active      boolean      not null default true,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint ux_organization_cnpj unique (cnpj)
);

create table app_user (
    id                     uuid primary key,
    organization_id        uuid references organization (id),
    name                   varchar(150) not null,
    email                  varchar(254) not null,
    password_hash          varchar(100) not null,
    platform_admin         boolean      not null default false,
    active                 boolean      not null default true,
    failed_login_attempts  int          not null default 0,
    locked_until           timestamptz,
    last_login_at          timestamptz,
    created_at             timestamptz  not null,
    updated_at             timestamptz  not null,
    version                bigint       not null default 0,
    constraint ck_app_user_org check (platform_admin or organization_id is not null),
    constraint ux_app_user_id_org unique (id, organization_id)
);
create unique index ux_app_user_email on app_user (lower(email));
create index ix_app_user_org on app_user (organization_id);

create table refresh_token (
    id          uuid primary key,
    user_id     uuid         not null references app_user (id),
    token_hash  varchar(64)  not null,
    expires_at  timestamptz  not null,
    revoked_at  timestamptz,
    created_at  timestamptz  not null,
    constraint ux_refresh_token_hash unique (token_hash)
);
create index ix_refresh_token_user on refresh_token (user_id);

-- Catálogo de permissões (módulo + ação). Mantido em sincronia com o enum Permission.
create table permission (
    code        varchar(60)  primary key,
    module      varchar(40)  not null,
    action      varchar(20)  not null,
    description varchar(200) not null
);

-- Papéis: organization_id nulo = papel padrão do sistema, disponível a todas as organizações.
create table role (
    id               uuid primary key,
    organization_id  uuid references organization (id),
    code             varchar(40)  not null,
    name             varchar(80)  not null,
    description      varchar(255),
    system_role      boolean      not null default false,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0
);
create unique index ux_role_org_code on role (coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

create table role_permission (
    role_id          uuid        not null references role (id) on delete cascade,
    permission_code  varchar(60) not null references permission (code),
    primary key (role_id, permission_code)
);

create table condominium (
    id                   uuid primary key,
    organization_id      uuid         not null references organization (id),
    name                 varchar(150) not null,
    legal_name           varchar(200),
    cnpj                 varchar(14),
    type                 varchar(20)  not null,
    zip_code             varchar(8),
    street               varchar(200),
    number               varchar(20),
    complement           varchar(100),
    district             varchar(100),
    city                 varchar(100),
    state                varchar(2),
    email                varchar(254),
    phone                varchar(20),
    status               varchar(20)  not null default 'ACTIVE',
    inactivated_at       timestamptz,
    inactivation_reason  varchar(500),
    created_at           timestamptz  not null,
    created_by           uuid,
    updated_at           timestamptz  not null,
    updated_by           uuid,
    version              bigint       not null default 0,
    constraint ux_condominium_id_org unique (id, organization_id),
    constraint ck_condominium_type check (type in ('RESIDENTIAL', 'COMMERCIAL', 'MIXED')),
    constraint ck_condominium_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_condominium_state check (state is null or state ~ '^[A-Z]{2}$')
);
create unique index ux_condominium_org_cnpj on condominium (organization_id, cnpj) where cnpj is not null;
create index ix_condominium_org_name on condominium (organization_id, lower(name));

-- Atribuição de papel a usuário. condominium_id nulo = vale para toda a organização.
-- FKs compostas garantem que usuário e condomínio pertencem à mesma organização da atribuição.
create table user_role (
    id               uuid primary key,
    user_id          uuid        not null,
    role_id          uuid        not null references role (id),
    organization_id  uuid        not null references organization (id),
    condominium_id   uuid,
    created_at       timestamptz not null,
    created_by       uuid,
    constraint fk_user_role_user foreign key (user_id, organization_id) references app_user (id, organization_id),
    constraint fk_user_role_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id)
);
create unique index ux_user_role_assignment
    on user_role (user_id, role_id, coalesce(condominium_id, '00000000-0000-0000-0000-000000000000'::uuid));
create index ix_user_role_user on user_role (user_id);

-- Trilha de auditoria: somente inserção. Nunca armazenar senhas, tokens ou segredos em details.
create table audit_log (
    id               uuid primary key,
    organization_id  uuid references organization (id),
    user_id          uuid,
    user_email       varchar(254),
    action           varchar(60)  not null,
    entity_type      varchar(60),
    entity_id        varchar(64),
    details          jsonb,
    ip_address       varchar(45),
    occurred_at      timestamptz  not null
);
create index ix_audit_log_org_time on audit_log (organization_id, occurred_at desc);
create index ix_audit_log_entity on audit_log (entity_type, entity_id);

-- ---------------------------------------------------------------------
-- Dados de referência: permissões e papéis padrão
-- ---------------------------------------------------------------------
insert into permission (code, module, action, description) values
    ('CONDOMINIUM_VIEW',       'CONDOMINIUM', 'VIEW',       'Consultar condomínios'),
    ('CONDOMINIUM_CREATE',     'CONDOMINIUM', 'CREATE',     'Cadastrar condomínios'),
    ('CONDOMINIUM_EDIT',       'CONDOMINIUM', 'EDIT',       'Editar condomínios'),
    ('CONDOMINIUM_INACTIVATE', 'CONDOMINIUM', 'CANCEL',     'Ativar e inativar condomínios'),
    ('USER_VIEW',              'USER',        'VIEW',       'Consultar usuários'),
    ('USER_MANAGE',            'USER',        'EDIT',       'Cadastrar e editar usuários e papéis'),
    ('AUDIT_VIEW',             'AUDIT',       'VIEW',       'Consultar trilha de auditoria');

insert into role (id, organization_id, code, name, description, system_role, created_at, updated_at) values
    ('10000000-0000-0000-0000-000000000001', null, 'ORG_ADMIN',    'Administrador da administradora', 'Acesso total à organização', true, now(), now()),
    ('10000000-0000-0000-0000-000000000002', null, 'ORG_STAFF',    'Funcionário da administradora',   'Operação do dia a dia',      true, now(), now()),
    ('10000000-0000-0000-0000-000000000003', null, 'SYNDIC',       'Síndico',                         null, true, now(), now()),
    ('10000000-0000-0000-0000-000000000004', null, 'SUBSYNDIC',    'Subsíndico',                      null, true, now(), now()),
    ('10000000-0000-0000-0000-000000000005', null, 'COUNCIL',      'Conselheiro',                     null, true, now(), now()),
    ('10000000-0000-0000-0000-000000000006', null, 'CONDO_STAFF',  'Funcionário do condomínio',       null, true, now(), now()),
    ('10000000-0000-0000-0000-000000000007', null, 'OWNER',        'Proprietário',                    null, true, now(), now()),
    ('10000000-0000-0000-0000-000000000008', null, 'RESIDENT',     'Morador',                         null, true, now(), now());

insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000001', code from permission;

insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000002', 'CONDOMINIUM_VIEW'),
    ('10000000-0000-0000-0000-000000000002', 'CONDOMINIUM_CREATE'),
    ('10000000-0000-0000-0000-000000000002', 'CONDOMINIUM_EDIT'),
    ('10000000-0000-0000-0000-000000000003', 'CONDOMINIUM_VIEW'),
    ('10000000-0000-0000-0000-000000000003', 'CONDOMINIUM_EDIT'),
    ('10000000-0000-0000-0000-000000000004', 'CONDOMINIUM_VIEW'),
    ('10000000-0000-0000-0000-000000000005', 'CONDOMINIUM_VIEW'),
    ('10000000-0000-0000-0000-000000000006', 'CONDOMINIUM_VIEW');
-- OWNER e RESIDENT recebem permissões do portal do morador em migrações futuras.

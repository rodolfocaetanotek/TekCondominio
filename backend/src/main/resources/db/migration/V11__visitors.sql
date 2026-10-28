-- =====================================================================
-- V11 — Portaria: pré-autorização de visitantes/prestadores/entregas pelo morador e registro de entrada e saída.
-- LGPD: documento exibido mascarado; registros antigos são anonimizados por rotina diária (retenção configurável).
-- =====================================================================

insert into permission (code, module, action, description) values
    ('ACCESS_VIEW', 'ACCESS', 'VIEW', 'Consultar registros da portaria'),
    ('ACCESS_EDIT', 'ACCESS', 'EDIT', 'Registrar entrada e saída de visitantes e prestadores');

insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000001', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000001', 'ACCESS_EDIT'),
    ('10000000-0000-0000-0000-000000000002', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000002', 'ACCESS_EDIT'),
    ('10000000-0000-0000-0000-000000000003', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000003', 'ACCESS_EDIT'),
    ('10000000-0000-0000-0000-000000000004', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000005', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000006', 'ACCESS_VIEW'),
    ('10000000-0000-0000-0000-000000000006', 'ACCESS_EDIT');

create table visitor_pass (
    id                  uuid primary key,
    organization_id     uuid          not null,
    condominium_id      uuid          not null,
    unit_id             uuid          not null,
    kind                varchar(16)   not null,
    name                varchar(150)  not null,
    document            varchar(20),
    company             varchar(120),
    vehicle_plate       varchar(10),
    valid_from          date          not null,
    valid_until         date          not null,
    access_code         varchar(6),
    status              varchar(10)   not null,
    by_resident         boolean       not null,
    authorized_by       uuid,
    authorized_by_name  varchar(150)  not null,
    entered_at          timestamptz,
    entered_by_name     varchar(150),
    left_at             timestamptz,
    left_by_name        varchar(150),
    notes               varchar(500),
    anonymized_at       timestamptz,
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    version             bigint        not null default 0,
    constraint fk_visitor_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint fk_visitor_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ck_visitor_kind check (kind in ('VISITOR', 'SERVICE_PROVIDER', 'DELIVERY')),
    constraint ck_visitor_status check (status in ('EXPECTED', 'INSIDE', 'LEFT', 'CANCELLED')),
    constraint ck_visitor_period check (valid_until >= valid_from and valid_until - valid_from <= 366)
);
create index ix_visitor_condominium_period on visitor_pass (condominium_id, valid_from, valid_until);
create index ix_visitor_status on visitor_pass (condominium_id, status);
-- Código de acesso único entre as autorizações ainda não utilizadas do condomínio.
create unique index ux_visitor_code on visitor_pass (condominium_id, access_code) where status = 'EXPECTED' and access_code is not null;

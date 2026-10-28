-- =====================================================================
-- V10 — Reservas de áreas comuns (salão de festas, churrasqueira, quadra...).
-- Horários são locais do condomínio, gravados em minutos desde a meia-noite (sem fuso). Sobreposição de reservas ativas na mesma área é impedida pelo
-- próprio banco (restrição de exclusão), além da verificação no serviço.
-- =====================================================================

create extension if not exists btree_gist;

insert into permission (code, module, action, description) values
    ('RESERVATION_VIEW', 'RESERVATION', 'VIEW', 'Consultar áreas comuns e reservas'),
    ('RESERVATION_EDIT', 'RESERVATION', 'EDIT', 'Cadastrar áreas comuns e aprovar/cancelar reservas');

insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000001', 'RESERVATION_VIEW'),
    ('10000000-0000-0000-0000-000000000001', 'RESERVATION_EDIT'),
    ('10000000-0000-0000-0000-000000000002', 'RESERVATION_VIEW'),
    ('10000000-0000-0000-0000-000000000002', 'RESERVATION_EDIT'),
    ('10000000-0000-0000-0000-000000000003', 'RESERVATION_VIEW'),
    ('10000000-0000-0000-0000-000000000003', 'RESERVATION_EDIT'),
    ('10000000-0000-0000-0000-000000000004', 'RESERVATION_VIEW'),
    ('10000000-0000-0000-0000-000000000004', 'RESERVATION_EDIT'),
    ('10000000-0000-0000-0000-000000000005', 'RESERVATION_VIEW'),
    ('10000000-0000-0000-0000-000000000006', 'RESERVATION_VIEW');

create table common_area (
    id                    uuid primary key,
    organization_id       uuid           not null,
    condominium_id        uuid           not null,
    name                  varchar(80)    not null,
    description           varchar(1000),
    rules                 varchar(2000),
    capacity              int,
    fee                   numeric(15, 2) not null default 0,
    requires_approval     boolean        not null default false,
    block_delinquent      boolean        not null default false,
    open_minute           int            not null default 480,
    close_minute          int            not null default 1320,
    max_duration_minutes  int            not null default 720,
    min_advance_hours     int            not null default 24,
    max_advance_days      int            not null default 90,
    max_per_unit_month    int,
    active                boolean        not null default true,
    created_at            timestamptz    not null,
    updated_at            timestamptz    not null,
    version               bigint         not null default 0,
    constraint fk_common_area_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_common_area_id_condominium unique (id, condominium_id),
    constraint ck_common_area check (fee >= 0 and (capacity is null or capacity > 0) and open_minute >= 0 and close_minute <= 1440 and close_minute > open_minute
        and max_duration_minutes between 15 and 1440 and min_advance_hours between 0 and 720
        and max_advance_days between 1 and 730 and (max_per_unit_month is null or max_per_unit_month > 0))
);
create unique index ux_common_area_name on common_area (condominium_id, lower(name));

create table reservation (
    id                 uuid primary key,
    organization_id    uuid           not null,
    condominium_id     uuid           not null,
    area_id            uuid           not null,
    unit_id            uuid           not null,
    reservation_date   date           not null,
    start_minute       int            not null,
    end_minute         int            not null,
    guests             int,
    notes              varchar(500),
    status             varchar(10)    not null,
    fee                numeric(15, 2) not null default 0,
    entry_id           uuid,
    requested_by       uuid,
    requester_name     varchar(150)   not null,
    by_resident        boolean        not null,
    decision_note      varchar(255),
    decided_by         uuid,
    decided_at         timestamptz,
    cancel_reason      varchar(255),
    cancelled_at       timestamptz,
    created_at         timestamptz    not null,
    updated_at         timestamptz    not null,
    version            bigint         not null default 0,
    constraint fk_reservation_area foreign key (area_id, condominium_id) references common_area (id, condominium_id),
    constraint fk_reservation_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint fk_reservation_entry foreign key (entry_id, condominium_id) references financial_entry (id, condominium_id),
    constraint ck_reservation_status check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    constraint ck_reservation_time check (start_minute >= 0 and end_minute <= 1440 and end_minute > start_minute and (guests is null or guests >= 0) and fee >= 0),
    constraint ex_reservation_overlap exclude using gist (
        area_id with =, reservation_date with =, int4range(start_minute, end_minute) with &&
    ) where (status in ('PENDING', 'CONFIRMED'))
);
create index ix_reservation_condominium_date on reservation (condominium_id, reservation_date);
create index ix_reservation_unit on reservation (unit_id, reservation_date);

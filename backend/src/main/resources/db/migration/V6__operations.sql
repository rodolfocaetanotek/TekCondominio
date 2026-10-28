-- =====================================================================
-- V6 — Administração e comunicação: documentos, ocorrências, comunicados, notificações, assembleias e contratos
-- =====================================================================

-- ---------- Documentos ----------
-- O arquivo fica no armazenamento (storage_key aleatório, nunca exposto); o acesso é sempre via API autenticada.
create table document (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    category         varchar(20)   not null,
    title            varchar(200)  not null,
    description      varchar(500),
    file_name        varchar(255)  not null,
    content_type     varchar(100)  not null,
    size_bytes       bigint        not null,
    sha256           varchar(64)   not null,
    storage_key      varchar(100)  not null,
    visibility       varchar(12)   not null default 'INTERNAL',
    entity_type      varchar(40),
    entity_id        uuid,
    unit_id          uuid,
    archived_at      timestamptz,
    archived_by      uuid,
    created_at       timestamptz   not null,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_document_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_document_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint ux_document_storage unique (storage_key),
    constraint ck_document_category check (category in ('CONVENTION', 'INTERNAL_RULES', 'MINUTES', 'CONTRACT', 'INVOICE',
        'RECEIPT', 'FINANCIAL_STATEMENT', 'UNIT', 'OCCURRENCE', 'OTHER')),
    constraint ck_document_visibility check (visibility in ('INTERNAL', 'RESIDENTS')),
    constraint ck_document_size check (size_bytes > 0)
);
create index ix_document_condominium on document (condominium_id, created_at desc);
create index ix_document_entity on document (entity_type, entity_id);

-- ---------- Ocorrências ----------
create table occurrence (
    id                uuid primary key,
    organization_id   uuid           not null,
    condominium_id    uuid           not null,
    number            int            not null,
    title             varchar(150)   not null,
    description       varchar(4000)  not null,
    category          varchar(16)    not null,
    priority          varchar(10)    not null,
    status            varchar(12)    not null,
    unit_id           uuid,
    reporter_user_id  uuid,
    reporter_name     varchar(150)   not null,
    assignee_name     varchar(150),
    due_date          date,
    resolution        varchar(2000),
    resolved_at       timestamptz,
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,
    version           bigint         not null default 0,
    constraint fk_occurrence_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_occurrence_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint ux_occurrence_number unique (condominium_id, number),
    constraint ck_occurrence_category check (category in ('MAINTENANCE', 'NOISE', 'SECURITY', 'CLEANING', 'COMPLAINT',
        'REQUEST', 'SUGGESTION', 'OTHER')),
    constraint ck_occurrence_priority check (priority in ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    constraint ck_occurrence_status check (status in ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CANCELLED'))
);
create index ix_occurrence_condominium on occurrence (condominium_id, status, created_at desc);

create table occurrence_interaction (
    id              uuid primary key,
    occurrence_id   uuid          not null references occurrence (id),
    author_user_id  uuid,
    author_name     varchar(150)  not null,
    message         varchar(2000) not null,
    status_change   varchar(12),
    internal        boolean       not null default false,
    created_at      timestamptz   not null
);
create index ix_interaction_occurrence on occurrence_interaction (occurrence_id, created_at);

-- ---------- Comunicados e notificações ----------
create table announcement (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    title            varchar(150)  not null,
    body             varchar(8000) not null,
    audience         varchar(10)   not null default 'ALL',
    building_id      uuid,
    unit_id          uuid,
    pinned           boolean       not null default false,
    send_email       boolean       not null default false,
    expires_on       date,
    published_at     timestamptz   not null,
    archived_at      timestamptz,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_announcement_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_announcement_building foreign key (building_id, condominium_id) references building (id, condominium_id),
    constraint fk_announcement_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint ck_announcement_audience check (audience in ('ALL', 'BUILDING', 'UNIT'))
);
create index ix_announcement_condominium on announcement (condominium_id, published_at desc);

create table announcement_read (
    announcement_id  uuid        not null references announcement (id),
    user_id          uuid        not null references app_user (id),
    read_at          timestamptz not null,
    primary key (announcement_id, user_id)
);

create table notification (
    id               uuid primary key,
    organization_id  uuid          not null,
    user_id          uuid          not null references app_user (id),
    title            varchar(150)  not null,
    body             varchar(1000),
    link             varchar(255),
    dedup_key        varchar(150),
    read_at          timestamptz,
    created_at       timestamptz   not null
);
create index ix_notification_user on notification (user_id, created_at desc);
create unique index ux_notification_dedup on notification (user_id, dedup_key) where dedup_key is not null;

-- ---------- Assembleias ----------
create table assembly (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    title            varchar(200)  not null,
    kind             varchar(14)   not null,
    scheduled_at     timestamptz   not null,
    second_call_at   timestamptz,
    location         varchar(255)  not null,
    vote_weight      varchar(16)   not null default 'UNIT',
    status           varchar(12)   not null default 'DRAFT',
    minutes          text,
    called_at        timestamptz,
    closed_at        timestamptz,
    cancel_reason    varchar(255),
    created_at       timestamptz   not null,
    created_by       uuid,
    updated_at       timestamptz   not null,
    version          bigint        not null default 0,
    constraint fk_assembly_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ck_assembly_kind check (kind in ('ORDINARY', 'EXTRAORDINARY')),
    constraint ck_assembly_weight check (vote_weight in ('UNIT', 'IDEAL_FRACTION')),
    constraint ck_assembly_status check (status in ('DRAFT', 'CALLED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED'))
);
create index ix_assembly_condominium on assembly (condominium_id, scheduled_at desc);

create table assembly_agenda_item (
    id             uuid primary key,
    assembly_id    uuid          not null references assembly (id),
    position       int           not null,
    title          varchar(200)  not null,
    description    varchar(2000),
    voting         boolean       not null default true,
    deliberation   varchar(2000),
    responsible    varchar(150),
    deadline       date,
    constraint ux_agenda_position unique (assembly_id, position)
);

create table assembly_attendance (
    id             uuid primary key,
    assembly_id    uuid          not null references assembly (id),
    unit_id        uuid          not null references unit (id),
    attendee_name  varchar(150)  not null,
    by_proxy       boolean       not null default false,
    proxy_name     varchar(150),
    registered_at  timestamptz   not null,
    registered_by  uuid,
    constraint ux_attendance unique (assembly_id, unit_id)
);

-- Voto por unidade, com o peso fotografado no momento do voto (1 ou fração ideal).
create table assembly_vote (
    id              uuid primary key,
    assembly_id     uuid          not null references assembly (id),
    agenda_item_id  uuid          not null references assembly_agenda_item (id),
    unit_id         uuid          not null references unit (id),
    choice          varchar(8)    not null,
    weight          numeric(9, 6) not null,
    registered_at   timestamptz   not null,
    registered_by   uuid,
    constraint ux_vote unique (agenda_item_id, unit_id),
    constraint ck_vote_choice check (choice in ('YES', 'NO', 'ABSTAIN'))
);

-- ---------- Contratos ----------
create table contract (
    id                  uuid primary key,
    organization_id     uuid           not null,
    condominium_id      uuid           not null,
    supplier_id         uuid           not null,
    title               varchar(200)   not null,
    description         varchar(2000),
    amount              numeric(15, 2) not null,
    periodicity         varchar(10)    not null,
    start_date          date           not null,
    end_date            date,
    readjustment_index  varchar(20),
    readjustment_month  int,
    notice_days         int            not null default 30,
    status              varchar(12)    not null default 'ACTIVE',
    terminated_on       date,
    termination_reason  varchar(255),
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null,
    version             bigint         not null default 0,
    constraint fk_contract_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_contract_supplier foreign key (supplier_id, organization_id) references supplier (id, organization_id),
    constraint ck_contract check (amount >= 0 and (end_date is null or end_date >= start_date)
        and (readjustment_month is null or readjustment_month between 1 and 12) and notice_days between 0 and 365),
    constraint ck_contract_periodicity check (periodicity in ('MONTHLY', 'YEARLY', 'ONE_TIME', 'OTHER')),
    constraint ck_contract_status check (status in ('ACTIVE', 'TERMINATED'))
);
create index ix_contract_condominium on contract (condominium_id, status, end_date);

create table contract_history (
    id               uuid primary key,
    contract_id      uuid          not null references contract (id),
    change           varchar(1000) not null,
    created_at       timestamptz   not null,
    created_by_name  varchar(150)
);

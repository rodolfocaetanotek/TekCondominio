-- =====================================================================
-- V5 — Cobrança: rateios (geração de cobranças por unidade), acordos de parcelamento e histórico de cobrança.
-- As cobranças em si são títulos a receber (financial_entry, kind = INCOME, unit_id preenchido).
-- =====================================================================

create table allocation (
    id                uuid primary key,
    organization_id   uuid           not null,
    condominium_id    uuid           not null,
    competence        date           not null,
    description       varchar(200)   not null,
    chart_account_id  uuid           not null references chart_account (id),
    method            varchar(16)    not null,
    base_amount       numeric(15, 2) not null,
    due_date          date           not null,
    units_count       int            not null,
    generated_amount  numeric(15, 2) not null,
    status            varchar(12)    not null default 'GENERATED',
    cancel_reason     varchar(255),
    cancelled_at      timestamptz,
    cancelled_by      uuid,
    created_at        timestamptz    not null,
    created_by        uuid,
    constraint fk_allocation_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ck_allocation_method check (method in ('IDEAL_FRACTION', 'EQUAL', 'FIXED', 'CONSUMPTION')),
    constraint ck_allocation_status check (status in ('GENERATED', 'CANCELLED')),
    constraint ck_allocation_competence check (extract(day from competence) = 1),
    constraint ck_allocation_amount check (base_amount > 0 and generated_amount > 0)
);
-- Impede gerar duas vezes a mesma cobrança (conta) na mesma competência.
create unique index ux_allocation_competence on allocation (condominium_id, competence, chart_account_id) where status = 'GENERATED';

-- Uma cobrança por unidade em cada rateio.
create unique index ux_entry_allocation_unit on financial_entry (allocation_id, unit_id)
    where allocation_id is not null and status <> 'CANCELLED';

create table agreement (
    id                uuid primary key,
    organization_id   uuid           not null,
    condominium_id    uuid           not null,
    unit_id           uuid           not null,
    person_id         uuid,
    original_amount   numeric(15, 2) not null,
    charges_amount    numeric(15, 2) not null,
    discount          numeric(15, 2) not null default 0,
    total_amount      numeric(15, 2) not null,
    installments      int            not null,
    first_due_date    date           not null,
    status            varchar(12)    not null default 'ACTIVE',
    notes             varchar(500),
    cancel_reason     varchar(255),
    cancelled_at      timestamptz,
    cancelled_by      uuid,
    created_at        timestamptz    not null,
    created_by        uuid,
    version           bigint         not null default 0,
    constraint fk_agreement_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint ck_agreement check (installments between 1 and 120 and total_amount > 0 and discount >= 0),
    constraint ck_agreement_status check (status in ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);
create index ix_agreement_condominium on agreement (condominium_id, created_at desc);

create table agreement_original_entry (
    agreement_id  uuid not null references agreement (id),
    entry_id      uuid not null references financial_entry (id),
    primary key (agreement_id, entry_id)
);

create table collection_note (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    unit_id          uuid          not null,
    entry_id         uuid,
    contact_type     varchar(12)   not null,
    contact_date     date          not null,
    note             varchar(1000) not null,
    created_at       timestamptz   not null,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_collection_note_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint ck_collection_note_type check (contact_type in ('PHONE', 'EMAIL', 'WHATSAPP', 'LETTER', 'IN_PERSON', 'LEGAL', 'OTHER'))
);
create index ix_collection_note_unit on collection_note (unit_id, contact_date desc);

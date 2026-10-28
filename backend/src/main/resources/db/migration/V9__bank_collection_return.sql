-- =====================================================================
-- V9 — Cobrança bancária: número de cobrança dos títulos e processamento do arquivo de retorno (CNAB 240).
-- O número de cobrança é informado ao banco no registro do boleto ("seu número"/nosso número) e volta no retorno,
-- permitindo a baixa automática. Um título nunca é baixado duas vezes pelo mesmo movimento de retorno.
-- =====================================================================

create sequence financial_entry_collection_seq start with 1000;
alter table financial_entry add column collection_number bigint not null default nextval('financial_entry_collection_seq');
alter sequence financial_entry_collection_seq owned by financial_entry.collection_number;
create unique index ux_entry_collection_number on financial_entry (collection_number);

create table bank_return_file (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    bank_account_id  uuid          not null,
    file_name        varchar(200)  not null,
    file_hash        varchar(64)   not null,
    bank_code        varchar(3)    not null,
    generated_on     date,
    items_total      int           not null,
    settled          int           not null,
    pending          int           not null,
    created_at       timestamptz   not null,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_return_file_account foreign key (bank_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint fk_return_file_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_return_file_hash unique (bank_account_id, file_hash)
);

create table bank_return_item (
    id               uuid primary key,
    organization_id  uuid           not null,
    condominium_id   uuid           not null,
    bank_account_id  uuid           not null,
    file_id          uuid           not null references bank_return_file (id),
    sequence         int            not null,
    movement_code    varchar(2)     not null,
    our_number       varchar(20),
    your_number      varchar(15),
    due_date         date,
    face_value       numeric(15, 2) not null,
    fees             numeric(15, 2) not null,
    discount         numeric(15, 2) not null,
    paid_amount      numeric(15, 2) not null,
    bank_fee         numeric(15, 2) not null,
    occurrence_date  date,
    credit_date      date,
    status           varchar(12)    not null,
    entry_id         uuid,
    payment_id       uuid references entry_payment (id),
    message          varchar(255),
    resolved_at      timestamptz,
    resolved_by      uuid,
    constraint fk_return_item_account foreign key (bank_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint fk_return_item_entry foreign key (entry_id, condominium_id) references financial_entry (id, condominium_id),
    constraint ck_return_item_status check (status in ('SETTLED', 'PENDING', 'INFO', 'IGNORED')),
    constraint ck_return_item_settled check ((status = 'SETTLED') = (payment_id is not null))
);
create index ix_return_item_account on bank_return_item (bank_account_id, status);
-- Proteção contra baixa em duplicidade: o mesmo movimento de liquidação só gera uma baixa.
create unique index ux_return_item_settlement on bank_return_item (bank_account_id, our_number, your_number, movement_code, occurrence_date)
    where status = 'SETTLED';

-- =====================================================================
-- V8 — Importação de extrato bancário (OFX) e conciliação por linha do extrato.
-- O mesmo arquivo não é importado duas vezes (hash) e a mesma transação do banco (FITID) não se repete na conta,
-- mesmo que dois extratos se sobreponham.
-- =====================================================================

create table bank_statement_import (
    id               uuid primary key,
    organization_id  uuid          not null,
    condominium_id   uuid          not null,
    bank_account_id  uuid          not null,
    file_name        varchar(200)  not null,
    file_hash        varchar(64)   not null,
    period_start     date,
    period_end       date,
    lines_total      int           not null,
    lines_new        int           not null,
    auto_matched     int           not null,
    created_at       timestamptz   not null,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_statement_import_account foreign key (bank_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint fk_statement_import_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_statement_import_hash unique (bank_account_id, file_hash)
);

create table bank_statement_line (
    id               uuid primary key,
    organization_id  uuid           not null,
    condominium_id   uuid           not null,
    bank_account_id  uuid           not null,
    import_id        uuid           not null references bank_statement_import (id),
    fit_id           varchar(100)   not null,
    posted_on        date           not null,
    amount           numeric(15, 2) not null,
    trn_type         varchar(20),
    memo             varchar(255),
    status           varchar(10)    not null default 'UNMATCHED',
    payment_id       uuid,
    transfer_id      uuid,
    note             varchar(255),
    resolved_at      timestamptz,
    resolved_by      uuid,
    version          bigint         not null default 0,
    constraint fk_statement_line_account foreign key (bank_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint fk_statement_line_payment foreign key (payment_id) references entry_payment (id),
    constraint fk_statement_line_transfer foreign key (transfer_id) references bank_transfer (id),
    constraint ck_statement_line_status check (status in ('UNMATCHED', 'MATCHED', 'IGNORED')),
    constraint ck_statement_line_amount check (amount <> 0),
    constraint ck_statement_line_match check ((status = 'MATCHED') = (payment_id is not null or transfer_id is not null)
        and not (payment_id is not null and transfer_id is not null)),
    constraint ux_statement_line_fitid unique (bank_account_id, fit_id)
);
create index ix_statement_line_account on bank_statement_line (bank_account_id, status, posted_on);
-- Cada movimentação do sistema concilia com no máximo uma linha de extrato (por conta, no caso de transferências).
create unique index ux_statement_line_payment on bank_statement_line (payment_id) where payment_id is not null;
create unique index ux_statement_line_transfer on bank_statement_line (bank_account_id, transfer_id) where transfer_id is not null;

-- =====================================================================
-- V7 — Fechamento mensal (bloqueio de período).
-- Após o fechamento até uma data, nenhuma baixa, estorno, transferência, conciliação ou lançamento com
-- competência no período fechado pode ser registrado ou alterado. A reabertura exige justificativa e fica na trilha.
-- =====================================================================

insert into permission (code, module, action, description) values
    ('FINANCE_CLOSE', 'FINANCE', 'CLOSE', 'Fechar e reabrir períodos financeiros');

-- Somente o administrador da administradora fecha/reabre períodos.
insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000001', 'FINANCE_CLOSE');

-- Data até a qual (inclusive) o período está fechado. Nulo = nada fechado.
alter table condominium add column books_closed_until date;

create table period_closing (
    id               uuid primary key,
    organization_id  uuid         not null,
    condominium_id   uuid         not null,
    action           varchar(8)   not null,
    closed_until     date,
    previous_until   date,
    notes            varchar(500),
    snapshot         jsonb,
    created_at       timestamptz  not null,
    created_by       uuid,
    created_by_name  varchar(150),
    constraint fk_period_closing_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ck_period_closing_action check (action in ('CLOSE', 'REOPEN')),
    constraint ck_period_closing_close check (action <> 'CLOSE' or closed_until is not null)
);
create index ix_period_closing_condominium on period_closing (condominium_id, created_at desc);

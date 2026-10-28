-- =====================================================================
-- V4 — Financeiro: parâmetros de cobrança, fornecedores, plano de contas, centros de custo, contas bancárias,
--      lançamentos (a pagar / a receber), baixas, estornos, transferências, conciliação e orçamento.
-- Valores monetários em numeric(15,2). Nada é excluído fisicamente: cancelamento e estorno com justificativa.
-- =====================================================================

-- Parâmetros financeiros do condomínio (premissas P01/P02 — configuráveis)
alter table condominium add column fine_percent numeric(5, 2) not null default 2.00;
alter table condominium add column interest_monthly_percent numeric(5, 2) not null default 1.00;
alter table condominium add column billing_due_day int not null default 10;
alter table condominium add column require_payable_approval boolean not null default true;
alter table condominium add constraint ck_condominium_fine check (fine_percent between 0 and 2);
alter table condominium add constraint ck_condominium_interest check (interest_monthly_percent between 0 and 10);
alter table condominium add constraint ck_condominium_due_day check (billing_due_day between 1 and 28);

-- ---------- Fornecedores (da administradora; atendem vários condomínios) ----------
create table supplier (
    id               uuid primary key,
    organization_id  uuid         not null references organization (id),
    name             varchar(150) not null,
    document         varchar(14),
    category         varchar(80),
    email            varchar(254),
    phone            varchar(20),
    contact_name     varchar(120),
    notes            varchar(500),
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint ux_supplier_id_org unique (id, organization_id)
);
create unique index ux_supplier_document on supplier (organization_id, document) where document is not null;
create index ix_supplier_org_name on supplier (organization_id, lower(name));

-- ---------- Plano de contas ----------
-- organization_id nulo = plano padrão do sistema (compartilhado). Contas próprias podem ser criadas por administradora.
create table chart_account (
    id               uuid primary key,
    organization_id  uuid references organization (id),
    code             varchar(20)  not null,
    name             varchar(120) not null,
    nature           varchar(10)  not null,
    analytic         boolean      not null default true,
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint ck_chart_account_nature check (nature in ('INCOME', 'EXPENSE'))
);
create unique index ux_chart_account_code on chart_account (coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

insert into chart_account (id, organization_id, code, name, nature, analytic, created_at, updated_at) values
    ('20000000-0000-0000-0000-000000000001', null, '1',    'RECEITAS',                              'INCOME',  false, now(), now()),
    ('20000000-0000-0000-0000-000000000101', null, '1.01', 'Taxa condominial ordinária',             'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000102', null, '1.02', 'Fundo de reserva',                       'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000103', null, '1.03', 'Taxa extra / chamada de capital',        'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000104', null, '1.04', 'Multas e juros recebidos',               'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000105', null, '1.05', 'Rendimentos financeiros',                'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000106', null, '1.06', 'Locação de áreas comuns',                'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000107', null, '1.07', 'Acordos de inadimplência',               'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000199', null, '1.99', 'Outras receitas',                        'INCOME',  true,  now(), now()),
    ('20000000-0000-0000-0000-000000000002', null, '2',    'DESPESAS',                               'EXPENSE', false, now(), now()),
    ('20000000-0000-0000-0000-000000000201', null, '2.01', 'Pessoal e encargos',                     'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000202', null, '2.02', 'Água e esgoto',                          'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000203', null, '2.03', 'Energia elétrica',                       'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000204', null, '2.04', 'Gás',                                    'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000205', null, '2.05', 'Manutenção e conservação',               'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000206', null, '2.06', 'Limpeza e materiais',                    'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000207', null, '2.07', 'Segurança e portaria',                   'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000208', null, '2.08', 'Elevadores',                             'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000209', null, '2.09', 'Seguros',                                'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000210', null, '2.10', 'Taxa de administração',                  'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000211', null, '2.11', 'Tarifas bancárias',                      'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000212', null, '2.12', 'Impostos e taxas',                       'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000213', null, '2.13', 'Jardinagem e paisagismo',                'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000214', null, '2.14', 'Obras e benfeitorias',                   'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000215', null, '2.15', 'Despesas jurídicas e cobrança',          'EXPENSE', true,  now(), now()),
    ('20000000-0000-0000-0000-000000000299', null, '2.99', 'Outras despesas',                        'EXPENSE', true,  now(), now());

-- ---------- Centros de custo ----------
create table cost_center (
    id               uuid primary key,
    organization_id  uuid         not null references organization (id),
    condominium_id   uuid         not null,
    name             varchar(80)  not null,
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint fk_cost_center_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_cost_center_id_condominium unique (id, condominium_id)
);
create unique index ux_cost_center_name on cost_center (condominium_id, lower(name));

-- ---------- Contas bancárias / caixa ----------
create table bank_account (
    id               uuid primary key,
    organization_id  uuid          not null references organization (id),
    condominium_id   uuid          not null,
    name             varchar(80)   not null,
    kind             varchar(12)   not null,
    bank_code        varchar(5),
    agency           varchar(10),
    account_number   varchar(20),
    opening_balance  numeric(15, 2) not null default 0,
    opening_date     date          not null,
    active           boolean       not null default true,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    version          bigint        not null default 0,
    constraint fk_bank_account_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_bank_account_id_condominium unique (id, condominium_id),
    constraint ck_bank_account_kind check (kind in ('CHECKING', 'SAVINGS', 'INVESTMENT', 'CASH'))
);
create unique index ux_bank_account_name on bank_account (condominium_id, lower(name));

-- ---------- Lançamentos (títulos a pagar e a receber) ----------
create table financial_entry (
    id                        uuid primary key,
    organization_id           uuid           not null,
    condominium_id            uuid           not null,
    kind                      varchar(10)    not null,
    description               varchar(200)   not null,
    chart_account_id          uuid           not null references chart_account (id),
    cost_center_id            uuid,
    supplier_id               uuid,
    unit_id                   uuid,
    person_id                 uuid,
    amount                    numeric(15, 2) not null,
    paid_amount               numeric(15, 2) not null default 0,
    due_date                  date           not null,
    competence                date           not null,
    document_number           varchar(40),
    notes                     varchar(500),
    status                    varchar(14)    not null,
    approval                  varchar(14)    not null,
    approved_by               uuid,
    approved_at               timestamptz,
    approval_note             varchar(255),
    origin                    varchar(12)    not null default 'MANUAL',
    recurrence_group          uuid,
    installment               int,
    installments              int,
    allocation_id             uuid,
    agreement_id              uuid,
    fine_percent              numeric(5, 2),
    interest_monthly_percent  numeric(5, 2),
    cancelled_at              timestamptz,
    cancelled_by              uuid,
    cancel_reason             varchar(255),
    created_at                timestamptz    not null,
    created_by                uuid,
    updated_at                timestamptz    not null,
    updated_by                uuid,
    version                   bigint         not null default 0,
    constraint fk_entry_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_entry_cost_center foreign key (cost_center_id, condominium_id) references cost_center (id, condominium_id),
    constraint fk_entry_supplier foreign key (supplier_id, organization_id) references supplier (id, organization_id),
    constraint fk_entry_unit foreign key (unit_id, condominium_id) references unit (id, condominium_id),
    constraint fk_entry_person foreign key (person_id, organization_id) references person (id, organization_id),
    constraint ux_entry_id_condominium unique (id, condominium_id),
    constraint ck_entry_kind check (kind in ('INCOME', 'EXPENSE')),
    constraint ck_entry_amount check (amount > 0 and paid_amount >= 0 and paid_amount <= amount),
    constraint ck_entry_status check (status in ('OPEN', 'PARTIAL', 'PAID', 'CANCELLED', 'RENEGOTIATED')),
    constraint ck_entry_approval check (approval in ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_entry_origin check (origin in ('MANUAL', 'RECURRENCE', 'ALLOCATION', 'AGREEMENT')),
    constraint ck_entry_competence check (extract(day from competence) = 1)
);
create index ix_entry_condominium_due on financial_entry (condominium_id, due_date);
create index ix_entry_condominium_status on financial_entry (condominium_id, kind, status);
create index ix_entry_unit on financial_entry (unit_id) where unit_id is not null;
-- Evita lançar duas vezes a mesma nota/boleto do mesmo fornecedor.
create unique index ux_entry_supplier_document on financial_entry (condominium_id, supplier_id, document_number)
    where supplier_id is not null and document_number is not null and status <> 'CANCELLED';

-- ---------- Baixas (pagamentos/recebimentos) ----------
create table entry_payment (
    id               uuid primary key,
    organization_id  uuid           not null,
    condominium_id   uuid           not null,
    entry_id         uuid           not null,
    bank_account_id  uuid           not null,
    paid_on          date           not null,
    principal        numeric(15, 2) not null,
    fine             numeric(15, 2) not null default 0,
    interest         numeric(15, 2) not null default 0,
    discount         numeric(15, 2) not null default 0,
    total            numeric(15, 2) not null,
    method           varchar(12)    not null,
    notes            varchar(255),
    reconciled_at    timestamptz,
    reconciled_by    uuid,
    reversed_at      timestamptz,
    reversed_by      uuid,
    reversal_reason  varchar(255),
    created_at       timestamptz    not null,
    created_by       uuid,
    constraint fk_payment_entry foreign key (entry_id, condominium_id) references financial_entry (id, condominium_id),
    constraint fk_payment_bank_account foreign key (bank_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint ck_payment_values check (principal > 0 and fine >= 0 and interest >= 0 and discount >= 0
        and total = principal + fine + interest - discount and total >= 0),
    constraint ck_payment_method check (method in ('PIX', 'BOLETO', 'TRANSFER', 'CASH', 'CHECK', 'DEBIT', 'CARD', 'OTHER'))
);
create index ix_payment_entry on entry_payment (entry_id);
create index ix_payment_account_date on entry_payment (bank_account_id, paid_on);

-- ---------- Transferências entre contas ----------
create table bank_transfer (
    id               uuid primary key,
    organization_id  uuid           not null,
    condominium_id   uuid           not null,
    from_account_id  uuid           not null,
    to_account_id    uuid           not null,
    amount           numeric(15, 2) not null,
    transfer_date    date           not null,
    description      varchar(200),
    reconciled_at    timestamptz,
    reconciled_by    uuid,
    reversed_at      timestamptz,
    reversed_by      uuid,
    reversal_reason  varchar(255),
    created_at       timestamptz    not null,
    created_by       uuid,
    constraint fk_transfer_from foreign key (from_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint fk_transfer_to foreign key (to_account_id, condominium_id) references bank_account (id, condominium_id),
    constraint ck_transfer check (amount > 0 and from_account_id <> to_account_id)
);
create index ix_transfer_condominium_date on bank_transfer (condominium_id, transfer_date);

-- ---------- Orçamento anual (previsto por conta e mês) ----------
create table budget_line (
    id                uuid primary key,
    organization_id   uuid           not null,
    condominium_id    uuid           not null,
    year              int            not null,
    month             int            not null,
    chart_account_id  uuid           not null references chart_account (id),
    amount            numeric(15, 2) not null,
    updated_at        timestamptz    not null,
    updated_by        uuid,
    constraint fk_budget_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ck_budget check (month between 1 and 12 and year between 2000 and 2100 and amount >= 0)
);
create unique index ux_budget_line on budget_line (condominium_id, year, chart_account_id, month);

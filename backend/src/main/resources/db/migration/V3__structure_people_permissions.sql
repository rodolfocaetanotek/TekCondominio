-- =====================================================================
-- V3 — Catálogo completo de permissões, estrutura (blocos/unidades) e pessoas/ocupações
-- =====================================================================

-- ---------- Permissões de todos os módulos ----------
insert into permission (code, module, action, description) values
    ('STRUCTURE_VIEW',     'STRUCTURE',    'VIEW',    'Consultar blocos e unidades'),
    ('STRUCTURE_EDIT',     'STRUCTURE',    'EDIT',    'Cadastrar e editar blocos e unidades'),
    ('PEOPLE_VIEW',        'PEOPLE',       'VIEW',    'Consultar pessoas e ocupações'),
    ('PEOPLE_EDIT',        'PEOPLE',       'EDIT',    'Cadastrar pessoas e ocupações'),
    ('FINANCE_VIEW',       'FINANCE',      'VIEW',    'Consultar financeiro'),
    ('FINANCE_EDIT',       'FINANCE',      'EDIT',    'Lançar receitas, despesas e baixas'),
    ('FINANCE_APPROVE',    'FINANCE',      'APPROVE', 'Aprovar pagamentos'),
    ('FINANCE_CANCEL',     'FINANCE',      'CANCEL',  'Cancelar lançamentos e estornar baixas'),
    ('BILLING_VIEW',       'BILLING',      'VIEW',    'Consultar cobranças e inadimplência'),
    ('BILLING_EDIT',       'BILLING',      'EDIT',    'Gerar cobranças, rateios e acordos'),
    ('REPORT_VIEW',        'REPORT',       'VIEW',    'Consultar relatórios'),
    ('REPORT_EXPORT',      'REPORT',       'EXPORT',  'Exportar relatórios'),
    ('SUPPLIER_VIEW',      'SUPPLIER',     'VIEW',    'Consultar fornecedores e contratos'),
    ('SUPPLIER_EDIT',      'SUPPLIER',     'EDIT',    'Cadastrar fornecedores e contratos'),
    ('OCCURRENCE_VIEW',    'OCCURRENCE',   'VIEW',    'Consultar ocorrências'),
    ('OCCURRENCE_EDIT',    'OCCURRENCE',   'EDIT',    'Registrar e tratar ocorrências'),
    ('ANNOUNCEMENT_VIEW',  'ANNOUNCEMENT', 'VIEW',    'Consultar comunicados'),
    ('ANNOUNCEMENT_EDIT',  'ANNOUNCEMENT', 'EDIT',    'Publicar comunicados'),
    ('ASSEMBLY_VIEW',      'ASSEMBLY',     'VIEW',    'Consultar assembleias'),
    ('ASSEMBLY_EDIT',      'ASSEMBLY',     'EDIT',    'Gerenciar assembleias, votações e atas'),
    ('DOCUMENT_VIEW',      'DOCUMENT',     'VIEW',    'Consultar documentos'),
    ('DOCUMENT_EDIT',      'DOCUMENT',     'EDIT',    'Enviar e arquivar documentos'),
    ('PORTAL_ACCESS',      'PORTAL',       'VIEW',    'Acessar o portal do morador');

-- Administrador: todas as permissões (exceto as do portal, que são do morador).
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000001', code from permission
where code <> 'PORTAL_ACCESS'
  and code not in (select permission_code from role_permission where role_id = '10000000-0000-0000-0000-000000000001');

-- Funcionário da administradora: operação completa, sem aprovação/cancelamento financeiro nem gestão de acesso.
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000002', code from permission
where code in ('STRUCTURE_VIEW', 'STRUCTURE_EDIT', 'PEOPLE_VIEW', 'PEOPLE_EDIT', 'FINANCE_VIEW', 'FINANCE_EDIT',
               'BILLING_VIEW', 'BILLING_EDIT', 'REPORT_VIEW', 'REPORT_EXPORT', 'SUPPLIER_VIEW', 'SUPPLIER_EDIT',
               'OCCURRENCE_VIEW', 'OCCURRENCE_EDIT', 'ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_EDIT', 'ASSEMBLY_VIEW',
               'ASSEMBLY_EDIT', 'DOCUMENT_VIEW', 'DOCUMENT_EDIT');

-- Síndico: consulta tudo do seu condomínio, aprova pagamentos e conduz a comunicação/assembleias.
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000003', code from permission
where code in ('STRUCTURE_VIEW', 'PEOPLE_VIEW', 'FINANCE_VIEW', 'FINANCE_APPROVE', 'BILLING_VIEW', 'REPORT_VIEW',
               'REPORT_EXPORT', 'SUPPLIER_VIEW', 'OCCURRENCE_VIEW', 'OCCURRENCE_EDIT', 'ANNOUNCEMENT_VIEW',
               'ANNOUNCEMENT_EDIT', 'ASSEMBLY_VIEW', 'ASSEMBLY_EDIT', 'DOCUMENT_VIEW', 'DOCUMENT_EDIT');

-- Subsíndico
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000004', code from permission
where code in ('STRUCTURE_VIEW', 'PEOPLE_VIEW', 'FINANCE_VIEW', 'BILLING_VIEW', 'REPORT_VIEW', 'SUPPLIER_VIEW',
               'OCCURRENCE_VIEW', 'OCCURRENCE_EDIT', 'ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_EDIT', 'ASSEMBLY_VIEW',
               'DOCUMENT_VIEW');

-- Conselheiro: fiscalização (consulta e exportação)
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000005', code from permission
where code in ('STRUCTURE_VIEW', 'FINANCE_VIEW', 'BILLING_VIEW', 'REPORT_VIEW', 'REPORT_EXPORT', 'SUPPLIER_VIEW',
               'OCCURRENCE_VIEW', 'ANNOUNCEMENT_VIEW', 'ASSEMBLY_VIEW', 'DOCUMENT_VIEW');

-- Funcionário do condomínio (portaria/zeladoria)
insert into role_permission (role_id, permission_code)
select '10000000-0000-0000-0000-000000000006', code from permission
where code in ('STRUCTURE_VIEW', 'PEOPLE_VIEW', 'OCCURRENCE_VIEW', 'OCCURRENCE_EDIT', 'ANNOUNCEMENT_VIEW');

-- Proprietário e morador: somente portal
insert into role_permission (role_id, permission_code) values
    ('10000000-0000-0000-0000-000000000007', 'PORTAL_ACCESS'),
    ('10000000-0000-0000-0000-000000000008', 'PORTAL_ACCESS');

-- ---------- Estrutura ----------
create table building (
    id               uuid primary key,
    organization_id  uuid         not null references organization (id),
    condominium_id   uuid         not null,
    name             varchar(80)  not null,
    description      varchar(255),
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint fk_building_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint ux_building_id_condominium unique (id, condominium_id)
);
create unique index ux_building_name on building (condominium_id, lower(name));

create table unit (
    id               uuid primary key,
    organization_id  uuid          not null references organization (id),
    condominium_id   uuid          not null,
    building_id      uuid,
    identifier       varchar(30)   not null,
    type             varchar(20)   not null,
    floor            varchar(10),
    area             numeric(10, 2),
    ideal_fraction   numeric(9, 6) not null default 0,
    notes            varchar(500),
    active           boolean       not null default true,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    version          bigint        not null default 0,
    constraint fk_unit_condominium foreign key (condominium_id, organization_id) references condominium (id, organization_id),
    constraint fk_unit_building foreign key (building_id, condominium_id) references building (id, condominium_id),
    constraint ux_unit_id_org unique (id, organization_id),
    constraint ux_unit_id_condominium unique (id, condominium_id),
    constraint ck_unit_type check (type in ('APARTMENT', 'HOUSE', 'ROOM', 'STORE', 'PARKING', 'OTHER')),
    constraint ck_unit_fraction check (ideal_fraction >= 0 and ideal_fraction <= 100),
    constraint ck_unit_area check (area is null or area > 0)
);
create unique index ux_unit_identifier
    on unit (condominium_id, coalesce(building_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(identifier));

-- ---------- Pessoas ----------
-- Uma pessoa (CPF/CNPJ) é cadastrada uma única vez por administradora e pode ter vários papéis em várias unidades.
create table person (
    id               uuid primary key,
    organization_id  uuid         not null references organization (id),
    name             varchar(150) not null,
    kind             varchar(12)  not null,
    document         varchar(14),
    email            varchar(254),
    phone            varchar(20),
    secondary_phone  varchar(20),
    notes            varchar(500),
    active           boolean      not null default true,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint ux_person_id_org unique (id, organization_id),
    constraint ck_person_kind check (kind in ('INDIVIDUAL', 'COMPANY'))
);
create unique index ux_person_document on person (organization_id, document) where document is not null;
create index ix_person_org_name on person (organization_id, lower(name));

-- Histórico de ocupação: vínculos são encerrados (end_date), nunca sobrescritos.
create table unit_occupancy (
    id                     uuid primary key,
    organization_id        uuid         not null,
    unit_id                uuid         not null,
    person_id              uuid         not null,
    role                   varchar(20)  not null,
    financial_responsible  boolean      not null default false,
    start_date             date         not null,
    end_date               date,
    end_reason             varchar(255),
    created_at             timestamptz  not null,
    created_by             uuid,
    constraint fk_occupancy_unit foreign key (unit_id, organization_id) references unit (id, organization_id),
    constraint fk_occupancy_person foreign key (person_id, organization_id) references person (id, organization_id),
    constraint ck_occupancy_role check (role in ('OWNER', 'TENANT', 'RESIDENT')),
    constraint ck_occupancy_dates check (end_date is null or end_date >= start_date)
);
create index ix_occupancy_unit on unit_occupancy (unit_id);
create index ix_occupancy_person on unit_occupancy (person_id);
-- No máximo um responsável financeiro vigente por unidade.
create unique index ux_occupancy_financial on unit_occupancy (unit_id) where financial_responsible and end_date is null;

-- Vínculo opcional do usuário com o cadastro de pessoa (acesso ao portal do morador).
alter table app_user add column person_id uuid;
alter table app_user add constraint fk_app_user_person foreign key (person_id, organization_id) references person (id, organization_id);

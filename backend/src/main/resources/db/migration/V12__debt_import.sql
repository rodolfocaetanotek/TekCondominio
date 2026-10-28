alter table financial_entry drop constraint ck_entry_origin;
alter table financial_entry add constraint ck_entry_origin
    check (origin in ('MANUAL', 'RECURRENCE', 'ALLOCATION', 'AGREEMENT', 'IMPORT'));

create table daily_account_snapshot (
    snapshot_id uuid primary key,
    account_id uuid not null references competition_account (account_id),
    snapshot_date date not null,
    total_value numeric(18, 4) not null,
    profit_rate numeric(10, 6) not null,
    rank integer not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_daily_account_snapshot_account_date unique (account_id, snapshot_date),
    constraint ck_daily_account_snapshot_total_value check (total_value >= 0),
    constraint ck_daily_account_snapshot_rank check (rank > 0)
);

create index idx_daily_account_snapshot_date on daily_account_snapshot (snapshot_date);

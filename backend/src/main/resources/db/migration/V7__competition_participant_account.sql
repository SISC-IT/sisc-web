create table competition (
    competition_id uuid primary key,
    name varchar(100) not null,
    status varchar(16) not null,
    start_date date not null,
    end_date date not null,
    initial_cash numeric(18, 4) not null,
    fee_rate numeric(10, 6) not null,
    deposit_interest_rate numeric(10, 6) not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint ck_competition_status check (status in ('READY', 'ACTIVE', 'ENDED', 'FINALIZED')),
    constraint ck_competition_period check (start_date <= end_date),
    constraint ck_competition_initial_cash check (initial_cash > 0),
    constraint ck_competition_rates check (fee_rate >= 0 and deposit_interest_rate >= 0)
);

-- 진행 중(ACTIVE) 대회는 최대 1개
create unique index uk_competition_single_active on competition (status) where status = 'ACTIVE';

create table competition_participant (
    participant_id uuid primary key,
    competition_id uuid not null references competition (competition_id),
    user_id uuid not null references users (user_id),
    team_name varchar(100) not null,
    status varchar(16) not null,
    final_rank integer,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_competition_participant_user unique (competition_id, user_id),
    constraint ck_competition_participant_status check (status in ('ACTIVE', 'EXCLUDED')),
    constraint ck_competition_participant_rank check (final_rank > 0)
);

create table competition_account (
    account_id uuid primary key,
    participant_id uuid not null references competition_participant (participant_id),
    cash numeric(18, 4) not null,
    deposit numeric(18, 4) not null,
    version bigint not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_competition_account_participant unique (participant_id),
    constraint ck_competition_account_balance check (cash >= 0 and deposit >= 0)
);

create table holding (
    holding_id uuid primary key,
    account_id uuid not null references competition_account (account_id),
    stock_id uuid not null references competition_stock (stock_id),
    quantity bigint not null,
    avg_buy_price numeric(18, 4) not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_holding_account_stock unique (account_id, stock_id),
    constraint ck_holding_quantity check (quantity > 0),
    constraint ck_holding_avg_buy_price check (avg_buy_price > 0)
);

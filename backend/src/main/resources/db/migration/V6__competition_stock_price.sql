create table competition_stock (
    stock_id uuid primary key,
    ticker varchar(20) not null,
    name varchar(100) not null,
    market varchar(8) not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_competition_stock_ticker unique (ticker),
    constraint ck_competition_stock_market check (market in ('KR', 'US'))
);

create table daily_close_price (
    close_price_id uuid primary key,
    stock_id uuid not null references competition_stock (stock_id),
    price_date date not null,
    available_at timestamp not null,
    close_price numeric(18, 4) not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uk_daily_close_price_stock_date unique (stock_id, price_date),
    constraint ck_daily_close_price_positive check (close_price > 0)
);

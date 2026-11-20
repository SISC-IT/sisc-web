create table trade (
    trade_id uuid primary key,
    account_id uuid not null references competition_account (account_id),
    stock_id uuid not null references competition_stock (stock_id),
    close_price_id uuid not null references daily_close_price (close_price_id),
    side varchar(4) not null,
    quantity bigint not null,
    price numeric(18, 4) not null,
    fee numeric(18, 4) not null,
    cash_after numeric(18, 4) not null,
    idempotency_key uuid not null,
    executed_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    -- 클라이언트가 만든 키라 다른 계좌와 겹칠 수 있어 계좌 안에서만 유일하게 한다
    constraint uk_trade_account_idempotency_key unique (account_id, idempotency_key),
    constraint ck_trade_side check (side in ('BUY', 'SELL')),
    constraint ck_trade_quantity check (quantity > 0),
    constraint ck_trade_price check (price > 0),
    constraint ck_trade_fee check (fee >= 0),
    constraint ck_trade_cash_after check (cash_after >= 0)
);

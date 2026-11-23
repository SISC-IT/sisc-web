create table account_ledger (
    ledger_id uuid primary key,
    account_id uuid not null references competition_account (account_id),
    type varchar(10) not null,
    amount numeric(18, 4) not null,
    cash_after numeric(18, 4) not null,
    deposit_after numeric(18, 4) not null,
    -- 이자가 어느 날짜 몫인지. 입출금 행은 null이다.
    interest_date date,
    executed_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    -- 같은 날짜 이자를 두 번 지급하지 않는다. 입출금 행은 interest_date가 null이라 걸리지 않는다.
    constraint uk_account_ledger_account_interest_date unique (account_id, interest_date),
    constraint ck_account_ledger_type check (type in ('DEPOSIT', 'WITHDRAW', 'INTEREST')),
    constraint ck_account_ledger_amount check (amount > 0),
    constraint ck_account_ledger_interest_date check ((type = 'INTEREST') = (interest_date is not null)),
    constraint ck_account_ledger_cash_after check (cash_after >= 0),
    constraint ck_account_ledger_deposit_after check (deposit_after >= 0)
);

-- 예금 이자를 어느 날짜 밤까지 지급했는지. 이자가 0원이면 원장 행이 없어서 원장만으로는 알 수 없다.
alter table competition add column interest_paid_date date;

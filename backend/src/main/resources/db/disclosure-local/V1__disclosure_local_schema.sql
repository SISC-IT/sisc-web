create table disclosure_company (
    company_id uuid primary key,
    corp_code varchar(8) not null,
    stock_code varchar(6),
    corp_name varchar(200) not null,
    corp_cls varchar(1),
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_company_corp_code unique (corp_code)
);

create table disclosure_filing (
    filing_id uuid primary key,
    company_id uuid not null references disclosure_company(company_id),
    rcept_no varchar(14) not null,
    report_nm text not null,
    flr_nm text,
    rcept_dt date not null,
    rm text,
    raw_payload jsonb not null,
    collected_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_filing_rcept_no unique (rcept_no)
);

create index idx_disclosure_filing_received_date on disclosure_filing (rcept_dt desc);
create index idx_disclosure_filing_company_received_date on disclosure_filing (company_id, rcept_dt desc);

create table disclosure_financial_metric (
    metric_id uuid primary key,
    filing_id uuid not null references disclosure_filing(filing_id) on delete cascade,
    metric_type varchar(40) not null,
    account_id varchar(200),
    account_nm text not null,
    statement_type varchar(3) not null,
    period_type varchar(20) not null,
    amount numeric(30, 4),
    currency varchar(10),
    raw_payload jsonb not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_financial_metric unique
      (filing_id, metric_type, statement_type, period_type, account_id)
);

create index idx_disclosure_metric_filing on disclosure_financial_metric (filing_id);

create table disclosure_company (
    company_id uuid primary key,
    dart_corp_code varchar(8) not null,
    stock_code varchar(6),
    company_name varchar(200) not null,
    dart_corp_cls varchar(1),
    closing_month integer not null default 12 check (closing_month between 1 and 12),
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_company_dart_corp_code unique (dart_corp_code)
);

create table disclosure_filing (
    filing_id uuid primary key,
    company_id uuid not null references disclosure_company(company_id),
    dart_rcept_no varchar(14) not null,
    report_title text not null,
    dart_submitter_name text,
    received_date date not null,
    fiscal_year integer,
    dart_report_code varchar(5),
    dart_remarks text,
    raw_payload jsonb not null,
    collected_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_filing_dart_rcept_no unique (dart_rcept_no)
);

create index idx_disclosure_filing_received_date on disclosure_filing (received_date desc);
create index idx_disclosure_filing_company_received_date on disclosure_filing (company_id, received_date desc);

create table disclosure_financial_metric (
    metric_id uuid primary key,
    filing_id uuid not null references disclosure_filing(filing_id) on delete cascade,
    metric_type varchar(40) not null,
    dart_account_id varchar(200),
    account_name text not null,
    basis varchar(3) not null,
    period_type varchar(20) not null,
    amount numeric(30, 4),
    currency varchar(10),
    raw_payload jsonb not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_financial_metric unique nulls not distinct
        (filing_id, metric_type, basis, period_type, dart_account_id)
);

create index idx_disclosure_metric_filing on disclosure_financial_metric (filing_id);

alter table disclosure_company
    rename column dart_corp_code to corp_code;
alter table disclosure_company
    rename column dart_corp_cls to corporation_class;
alter table disclosure_company
    add column if not exists induty_code varchar(20),
    add column if not exists tracked boolean not null default true,
    add column if not exists track_capex boolean not null default false;

alter table disclosure_filing
    rename column dart_rcept_no to receipt_no;
alter table disclosure_filing
    rename column report_title to title;
alter table disclosure_filing
    rename column dart_submitter_name to submitter_name;
alter table disclosure_filing
    rename column fiscal_year to business_year;
alter table disclosure_filing
    rename column dart_report_code to report_code;
alter table disclosure_filing
    rename column dart_remarks to remarks;

alter table disclosure_financial_metric
    rename column dart_account_id to account_id;

create table disclosure_event (
    event_id uuid primary key,
    company_id uuid not null references disclosure_company(company_id),
    receipt_no varchar(14) not null,
    category varchar(40) not null,
    report_title text not null,
    headline text not null,
    published_date date not null,
    market_class varchar(1),
    metrics_json jsonb not null,
    tags_json jsonb not null default '[]'::jsonb,
    dart_url text not null,
    raw_payload jsonb not null,
    collected_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_event_receipt_category unique (receipt_no, category)
);

create index idx_disclosure_event_published
    on disclosure_event (published_date desc, created_date desc);

create index idx_disclosure_event_category_published
    on disclosure_event (category, published_date desc, created_date desc);

create index idx_disclosure_event_company_published
    on disclosure_event (company_id, published_date desc);

create table disclosure_user_read (
    read_id uuid primary key,
    user_id uuid not null,
    company_id uuid not null references disclosure_company(company_id) on delete cascade,
    last_viewed_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_user_read unique (user_id, company_id)
);

create index idx_disclosure_user_read_user
    on disclosure_user_read (user_id);

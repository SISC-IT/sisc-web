alter table disclosure_company
    add column if not exists induty_code varchar(20);

create table disclosure_card_news (
    card_id uuid primary key,
    company_id uuid not null references disclosure_company(company_id),
    dart_rcept_no varchar(14) not null,
    category varchar(40) not null,
    report_title text not null,
    headline text not null,
    published_date date not null,
    market_cls varchar(1),
    metrics_json jsonb not null,
    tags_json jsonb not null default '[]'::jsonb,
    dart_url text not null,
    raw_payload jsonb not null,
    collected_at timestamp not null,
    created_date timestamp not null,
    updated_date timestamp not null,
    constraint uq_disclosure_card_news_rcept_category unique (dart_rcept_no, category)
);

create index idx_disclosure_card_news_published
    on disclosure_card_news (published_date desc, created_date desc);

create index idx_disclosure_card_news_category_published
    on disclosure_card_news (category, published_date desc, created_date desc);

create index idx_disclosure_card_news_company_published
    on disclosure_card_news (company_id, published_date desc);

create table news_collection_runs (
    run_id uuid primary key,
    provider varchar(64) not null,
    universe_name varchar(128),
    universe_as_of date,
    requested_start_at timestamptz not null,
    requested_end_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    collection_status varchar(32) not null,
    collection_outcome varchar(32) not null,
    failure_reason text,
    query_count integer not null default 0,
    successful_query_count integer not null default 0,
    failed_query_count integer not null default 0,
    relevant_article_count integer,
    errors jsonb not null default '[]'::jsonb,
    metrics jsonb not null default '{}'::jsonb,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint ck_news_collection_run_window
        check (requested_start_at <= requested_end_at),
    constraint ck_news_collection_run_counts
        check (
            query_count >= 0
            and successful_query_count >= 0
            and failed_query_count >= 0
        ),
    constraint ck_news_collection_run_status
        check (collection_status in ('success', 'partial_success', 'failed')),
    constraint ck_news_collection_run_outcome
        check (
            collection_outcome in (
                'results', 'no_results', 'no_relevant_news',
                'partial_results', 'provider_error', 'invalid_config'
            )
        )
);

create table news_articles (
    article_id bigserial primary key,
    provider varchar(64) not null,
    provider_article_id text,
    title text not null,
    provider_url text not null,
    normalized_url text,
    provider_url_hash char(64) not null,
    published_at_raw text not null,
    published_at_utc timestamptz not null,
    first_retrieved_at_utc timestamptz not null,
    last_retrieved_at_utc timestamptz not null,
    source text,
    snippet text,
    resolved_url text,
    url_resolution_status varchar(32) not null default 'not_attempted',
    content_text text,
    content_collection_status varchar(32) not null default 'not_attempted',
    content_failure_reason text,
    fallback_text text,
    fallback_text_source varchar(32) not null,
    exact_identity_key varchar(96) not null,
    exact_identity_method varchar(32) not null,
    syndication_candidate_group_id varchar(96) not null,
    syndication_candidate_group_method varchar(64) not null,
    syndication_candidate_group_confidence varchar(16) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_news_article_retrieval_order
        check (first_retrieved_at_utc <= last_retrieved_at_utc),
    constraint ck_news_article_fallback_source
        check (fallback_text_source in ('provider_snippet', 'title')),
    constraint ck_news_article_syndication_confidence
        check (syndication_candidate_group_confidence in ('low', 'medium', 'high'))
);

create unique index uq_news_articles_provider_article_id
    on news_articles (provider, provider_article_id)
    where provider_article_id is not null;

create unique index uq_news_articles_normalized_url
    on news_articles (normalized_url)
    where normalized_url is not null;

create unique index uq_news_articles_provider_url_hash
    on news_articles (provider, provider_url_hash);

create index idx_news_articles_published_at
    on news_articles (published_at_utc desc);

create index idx_news_articles_syndication_group
    on news_articles (syndication_candidate_group_id, published_at_utc desc);

create table news_article_identities (
    identity_key varchar(96) primary key,
    identity_method varchar(32) not null,
    article_id bigint not null references news_articles(article_id) on delete cascade,
    created_at timestamptz not null default now()
);

create index idx_news_article_identities_article
    on news_article_identities (article_id);

create table news_article_companies (
    article_id bigint not null references news_articles(article_id) on delete cascade,
    company_key varchar(128) not null,
    cik varchar(10),
    tickers varchar(16)[] not null,
    matched_ticker varchar(16),
    company_relevance_score numeric(7, 6) not null,
    company_relevance_reasons text[] not null default '{}',
    company_relevance_version varchar(64) not null,
    first_collection_run_id uuid references news_collection_runs(run_id),
    last_collection_run_id uuid references news_collection_runs(run_id),
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    primary key (article_id, company_key),
    constraint ck_news_article_company_score
        check (company_relevance_score between 0 and 1),
    constraint ck_news_article_company_cik
        check (cik is null or cik ~ '^[0-9]{10}$')
);

create index idx_news_article_companies_company_published
    on news_article_companies (company_key, article_id);

create index idx_news_article_companies_cik
    on news_article_companies (cik, article_id)
    where cik is not null;

create table sec_events (
    event_id varchar(160) primary key,
    company_key varchar(128),
    ticker varchar(16),
    cik varchar(10),
    accession_number varchar(25) not null,
    event_type varchar(64) not null,
    accepted_at timestamptz not null,
    source_updated_at timestamptz not null,
    status varchar(16) not null default 'active',
    event_version bigint not null default 1,
    event_text text,
    exhibit_text text,
    metadata jsonb not null default '{}'::jsonb,
    last_reconciled_at timestamptz,
    next_reconcile_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (accession_number, event_type),
    constraint ck_sec_events_status
        check (status in ('active', 'inactive', 'deleted')),
    constraint ck_sec_events_cik
        check (cik is null or cik ~ '^[0-9]{10}$'),
    constraint ck_sec_events_identity
        check (cik is not null or ticker is not null)
);

create index idx_sec_events_reconcile
    on sec_events (status, next_reconcile_at, source_updated_at);

create index idx_sec_events_cik_accepted
    on sec_events (cik, accepted_at desc)
    where cik is not null;

create table event_news_links (
    event_id varchar(160) not null references sec_events(event_id) on delete cascade,
    article_id bigint not null references news_articles(article_id) on delete cascade,
    relationship varchar(16) not null,
    event_relevance_score numeric(7, 6) not null,
    event_relevance_reasons text[] not null default '{}',
    event_relevance_version varchar(64) not null,
    published_offset_seconds bigint not null,
    source_event_version bigint not null,
    source_event_updated_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (event_id, article_id),
    constraint ck_event_news_relationship
        check (relationship in ('DIRECT', 'CONTEXT', 'UNRELATED')),
    constraint ck_event_news_score
        check (event_relevance_score between 0 and 1)
);

create index idx_event_news_links_article
    on event_news_links (article_id, relationship);

create index idx_event_news_links_event_score
    on event_news_links (event_id, event_relevance_score desc);

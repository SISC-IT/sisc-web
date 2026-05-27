alter table post
  add column if not exists public_visible boolean not null default false,
  add column if not exists public_published_at timestamp;

create index if not exists idx_post_public_visible_published_at
  on post(public_visible, public_published_at desc);

create index if not exists idx_post_media_post_type
  on post_media(post_id, media_type);

create table if not exists public_page (
  public_page_id uuid primary key,
  page_type varchar(30) not null unique,
  title varchar(255) not null,
  content_format varchar(30) not null default 'PLAIN_TEXT',
  content text not null,
  content_json text,
  content_html text,
  content_text text,
  published_at timestamp,
  updated_by uuid references users(user_id) on delete set null,
  created_date timestamp not null,
  updated_date timestamp not null
);

create index if not exists idx_public_page_type on public_page(page_type);

-- Board rich editor content migration.
-- Existing production databases are baselined at version 1, so this migration starts at V2.

alter table post
  add column if not exists content_format varchar(30) not null default 'PLAIN_TEXT',
  add column if not exists content_json text,
  add column if not exists content_html text,
  add column if not exists content_text text;

update post
set content_text = content
where content_text is null;

update post
set content_html = regexp_replace(
    '<p>' || replace(replace(replace(content, '&', '&amp;'), '<', '&lt;'), '>', '&gt;') || '</p>',
    E'\\r?\\n',
    '</p><p>',
    'g'
  )
where content_html is null;

create table if not exists post_media (
  media_id uuid primary key,
  post_id uuid references post(post_id) on delete set null,
  uploaded_by uuid references users(user_id) on delete set null,
  media_type varchar(30) not null,
  saved_filename varchar(255) not null,
  original_filename varchar(255) not null,
  file_path varchar(255) not null,
  public_path varchar(255) not null,
  content_type varchar(255) not null,
  file_size bigint not null,
  width integer,
  height integer,
  sort_order integer,
  created_date timestamp not null,
  updated_date timestamp not null
);

create index if not exists idx_post_media_post_id on post_media(post_id);
create index if not exists idx_post_media_uploaded_by on post_media(uploaded_by);

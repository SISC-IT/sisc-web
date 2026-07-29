-- Make board post likes concurrency-safe.
-- 1) Collapse legacy duplicate likes per (post_id, user_id).
-- 2) Reconcile cached post.like_count from the canonical post_like table.
-- 3) Add a DB-level uniqueness guard so duplicates cannot be reintroduced.

delete from post_like
where ctid in (
  select like_ctid
  from (
    select
      ctid as like_ctid,
      row_number() over (
        partition by post_id, user_id
        order by created_date asc nulls last, post_like_id asc
      ) as row_number
    from post_like
    where post_id is not null
      and user_id is not null
  ) ranked_likes
  where ranked_likes.row_number > 1
);

update post p
set like_count = coalesce(like_totals.like_count, 0)
from (
  select p_inner.post_id, count(pl.post_like_id)::integer as like_count
  from post p_inner
  left join post_like pl on pl.post_id = p_inner.post_id
  group by p_inner.post_id
) like_totals
where p.post_id = like_totals.post_id;

alter table post
  alter column like_count set default 0,
  alter column like_count set not null;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'uk_post_like_post_user'
      and conrelid = 'post_like'::regclass
  ) then
    alter table post_like
      add constraint uk_post_like_post_user unique (post_id, user_id);
  end if;
end $$;

create index if not exists idx_post_like_post_id on post_like(post_id);
create index if not exists idx_post_like_user_id on post_like(user_id);

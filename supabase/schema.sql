-- Kaspi Tracker: cloud side of pairing two phones.
--
-- Each phone signs in anonymously. Its own operations are written only by it;
-- the paired partner may read them. Nobody else can see anything. Pairing,
-- codes and unpairing go through the functions at the bottom — the tables
-- they touch have no direct access at all.
--
-- Apply once in the Supabase SQL editor of a project used only by this app.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------- tables

create table public.profiles (
  user_id uuid primary key references auth.users on delete cascade,
  display_name text not null default '',
  color int not null default 0,
  updated_at timestamptz not null default now()
);

create table public.couples (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null unique references auth.users on delete cascade,
  user_b uuid not null unique references auth.users on delete cascade,
  created_at timestamptz not null default now(),
  check (user_a <> user_b)
);

create table public.pair_codes (
  code text primary key,
  user_id uuid not null unique references auth.users on delete cascade,
  expires_at timestamptz not null
);

-- Failed code entries, for the "10 per hour" limit on guessing.
create table public.pair_attempts (
  user_id uuid not null references auth.users on delete cascade,
  at timestamptz not null default now()
);
create index pair_attempts_recent on public.pair_attempts (user_id, at);

create table public.transactions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  timestamp bigint not null,
  amount double precision,
  type text not null,
  merchant text,
  category text,
  account text,
  balance double precision,
  source text not null,
  shared boolean not null default false,
  excluded_from_stats boolean not null default false,
  -- Deletions are kept as a flag so the partner's phone learns about them.
  deleted boolean not null default false,
  updated_at timestamptz not null default clock_timestamp()
);
-- The partner's phone pulls by (updated_at, id) after its last cursor.
create index transactions_pull on public.transactions (user_id, updated_at, id);

-- clock_timestamp(), not now(): rows written in one request still get
-- distinct, increasing times.
create function public.touch_updated_at() returns trigger
  language plpgsql set search_path = public as $$
begin
  new.updated_at := clock_timestamp();
  return new;
end $$;

create trigger transactions_touch before insert or update on public.transactions
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------- access

-- The other person of the caller's couple, or null.
create function public.partner_of(uid uuid) returns uuid
  language sql stable security definer set search_path = public as $$
  select case when user_a = uid then user_b else user_a end
  from couples where uid in (user_a, user_b)
$$;

alter table public.profiles enable row level security;
alter table public.couples enable row level security;
alter table public.pair_codes enable row level security;
alter table public.pair_attempts enable row level security;
alter table public.transactions enable row level security;

create policy profiles_read on public.profiles for select
  using (user_id = auth.uid() or user_id = public.partner_of(auth.uid()));
create policy profiles_insert on public.profiles for insert
  with check (user_id = auth.uid());
create policy profiles_update on public.profiles for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy couples_read on public.couples for select
  using (auth.uid() in (user_a, user_b));

create policy transactions_read on public.transactions for select
  using (user_id = auth.uid() or user_id = public.partner_of(auth.uid()));
create policy transactions_insert on public.transactions for insert
  with check (user_id = auth.uid());
create policy transactions_update on public.transactions for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());
-- No delete policy: rows are flagged deleted; only unpair() removes them.
-- pair_codes and pair_attempts: no policies — reachable only through the
-- security-definer functions below.

-- ---------------------------------------------------------------- pairing

-- A fresh 8-character code for the caller, valid for 10 minutes. Letters and
-- digits that are easy to tell apart (no 0/O, 1/I/L).
create function public.create_pair_code() returns json
  language plpgsql security definer set search_path = public as $$
declare
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  c text;
  expires timestamptz := now() + interval '10 minutes';
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  if exists (select 1 from couples where auth.uid() in (user_a, user_b)) then
    raise exception 'already_paired';
  end if;
  delete from pair_codes where user_id = auth.uid() or expires_at < now();
  loop
    c := '';
    for i in 1..8 loop
      c := c || substr(
        alphabet,
        1 + (get_byte(extensions.gen_random_bytes(1), 0) % length(alphabet)),
        1
      );
    end loop;
    exit when not exists (select 1 from pair_codes where code = c);
  end loop;
  insert into pair_codes (code, user_id, expires_at) values (c, auth.uid(), expires);
  return json_build_object('code', c, 'expires_at', expires);
end $$;

-- Pairs the caller with the owner of p_code. Returns a status instead of
-- raising, so a failed guess is recorded (an exception would roll it back):
-- ok, code_invalid, code_expired, own_code, already_paired, too_many_attempts.
create function public.redeem_pair_code(p_code text) returns text
  language plpgsql security definer set search_path = public as $$
declare
  owner uuid;
  expires timestamptz;
begin
  if auth.uid() is null then
    return 'code_invalid';
  end if;
  if (select count(*) from pair_attempts
      where user_id = auth.uid() and at > now() - interval '1 hour') >= 10 then
    return 'too_many_attempts';
  end if;
  select user_id, expires_at into owner, expires
  from pair_codes where code = upper(replace(trim(p_code), '-', ''));
  if owner is null then
    insert into pair_attempts (user_id) values (auth.uid());
    return 'code_invalid';
  end if;
  if expires < now() then
    return 'code_expired';
  end if;
  if owner = auth.uid() then
    return 'own_code';
  end if;
  if exists (select 1 from couples
             where owner in (user_a, user_b) or auth.uid() in (user_a, user_b)) then
    return 'already_paired';
  end if;
  insert into couples (user_a, user_b) values (owner, auth.uid());
  delete from pair_codes where user_id = owner;
  return 'ok';
end $$;

-- Ends the caller's couple and removes both people's operations from the
-- cloud. Each phone keeps its own history locally.
create function public.unpair() returns void
  language plpgsql security definer set search_path = public as $$
declare
  a uuid;
  b uuid;
begin
  select user_a, user_b into a, b from couples where auth.uid() in (user_a, user_b);
  if a is null then
    return;
  end if;
  delete from transactions where user_id in (a, b);
  delete from couples where user_a = a;
end $$;

-- Flags the caller's own rows as deleted so the partner's phone drops them.
-- Runs with the caller's rights: RLS still limits it to their own rows.
create function public.mark_deleted(ids uuid[]) returns void
  language sql set search_path = public as $$
  update transactions set deleted = true where id = any(ids) and user_id = auth.uid()
$$;

revoke all on function public.partner_of(uuid) from public, anon;
revoke all on function public.mark_deleted(uuid[]) from public, anon;
grant execute on function public.mark_deleted(uuid[]) to authenticated;
revoke all on function public.create_pair_code() from public, anon;
revoke all on function public.redeem_pair_code(text) from public, anon;
revoke all on function public.unpair() from public, anon;
grant execute on function public.partner_of(uuid) to authenticated;
grant execute on function public.create_pair_code() to authenticated;
grant execute on function public.redeem_pair_code(text) to authenticated;
grant execute on function public.unpair() to authenticated;

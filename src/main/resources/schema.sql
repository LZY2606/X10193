create table if not exists binary_object (
  id integer primary key autoincrement,
  name text not null,
  media_type text not null,
  content blob not null,
  sha256 text not null,
  size_bytes integer not null,
  created_at text not null default (datetime('now'))
);
create table if not exists scheme (
  id integer primary key autoincrement,
  name text not null,
  platform text not null,
  tie_policy text not null,
  allow_copy_relocation integer not null default 1,
  created_at text not null default (datetime('now')),
  result_json text not null
);
create table if not exists scheme_input (
  id integer primary key autoincrement,
  scheme_id integer not null references scheme(id) on delete cascade,
  binary_id integer not null references binary_object(id),
  position integer not null,
  input_kind text not null,
  as_needed integer not null default 0,
  unique(scheme_id, position)
);

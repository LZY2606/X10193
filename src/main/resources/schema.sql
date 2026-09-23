create table if not exists scheme (
  id text primary key,
  label text not null,
  target text not null,
  policy_json text not null,
  input_json text not null,
  result_json text not null,
  created_at text not null
);

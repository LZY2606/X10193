CREATE TABLE IF NOT EXISTS schemes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TEXT NOT NULL,
  policy_name TEXT NOT NULL,
  load_order_seed INTEGER NOT NULL,
  json TEXT NOT NULL
);

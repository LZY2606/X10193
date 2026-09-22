CREATE TABLE IF NOT EXISTS objects (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  kind TEXT NOT NULL,
  data BLOB NOT NULL,
  status TEXT NOT NULL DEFAULT 'OK',
  error TEXT,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS scenarios (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  version INTEGER NOT NULL,
  parent_id INTEGER,
  policy_json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(name, version)
);

CREATE TABLE IF NOT EXISTS scenario_items (
  scenario_id INTEGER NOT NULL,
  seq INTEGER NOT NULL,
  object_id INTEGER NOT NULL,
  PRIMARY KEY (scenario_id, seq)
);

CREATE TABLE IF NOT EXISTS results (
  scenario_id INTEGER PRIMARY KEY,
  json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

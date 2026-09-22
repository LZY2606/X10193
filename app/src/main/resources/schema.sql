PRAGMA journal_mode=WAL;

CREATE TABLE IF NOT EXISTS object_blob (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sha256 TEXT NOT NULL UNIQUE,
  filename TEXT NOT NULL,
  kind TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  parse_ok INTEGER NOT NULL,
  parse_error TEXT,
  content BLOB NOT NULL,
  uploaded_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS scheme (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  version INTEGER NOT NULL,
  family TEXT,
  policy TEXT NOT NULL,
  fingerprint TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  result_json TEXT NOT NULL,
  UNIQUE(family, version)
);

CREATE TABLE IF NOT EXISTS scheme_input (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  scheme_id INTEGER NOT NULL REFERENCES scheme(id) ON DELETE CASCADE,
  ord INTEGER NOT NULL,
  object_id INTEGER NOT NULL REFERENCES object_blob(id),
  as_name TEXT
);

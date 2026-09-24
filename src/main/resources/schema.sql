CREATE TABLE IF NOT EXISTS artifacts (
  id TEXT PRIMARY KEY,
  filename TEXT NOT NULL,
  content_type TEXT,
  sha256 TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  uploaded_at TEXT NOT NULL,
  content BLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS schemes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  policy_json TEXT NOT NULL,
  load_order_json TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scheme_artifacts (
  scheme_id TEXT NOT NULL,
  position INTEGER NOT NULL,
  artifact_id TEXT NOT NULL,
  PRIMARY KEY (scheme_id, position),
  FOREIGN KEY (scheme_id) REFERENCES schemes(id),
  FOREIGN KEY (artifact_id) REFERENCES artifacts(id)
);

CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  scheme_id TEXT NOT NULL,
  step INTEGER NOT NULL,
  type TEXT NOT NULL,
  message TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  FOREIGN KEY (scheme_id) REFERENCES schemes(id)
);

CREATE TABLE IF NOT EXISTS artifacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sha256 TEXT NOT NULL UNIQUE,
    original_name TEXT NOT NULL,
    kind TEXT NOT NULL,
    size INTEGER NOT NULL,
    parse_status TEXT NOT NULL,
    parse_error TEXT,
    data BLOB NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    policy TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    result_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scheme_items (
    scheme_id INTEGER NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    artifact_id INTEGER NOT NULL REFERENCES artifacts(id),
    PRIMARY KEY (scheme_id, position)
);

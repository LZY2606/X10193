CREATE TABLE IF NOT EXISTS binaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    path TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    size INTEGER NOT NULL,
    parse_ok INTEGER NOT NULL,
    parse_error TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS archive_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    binary_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    parse_ok INTEGER NOT NULL,
    parse_error TEXT,
    payload BLOB NOT NULL,
    FOREIGN KEY(binary_id) REFERENCES binaries(id)
);

CREATE TABLE IF NOT EXISTS schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    label TEXT NOT NULL,
    policy TEXT NOT NULL,
    ordered_binary_ids TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scheme_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL UNIQUE,
    result_json TEXT NOT NULL,
    FOREIGN KEY(scheme_id) REFERENCES schemes(id)
);

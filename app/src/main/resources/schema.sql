CREATE TABLE IF NOT EXISTS binaries (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    path TEXT NOT NULL,
    kind TEXT NOT NULL,
    content BLOB NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    policy TEXT NOT NULL,
    created_at TEXT NOT NULL,
    load_order TEXT NOT NULL,
    result_json TEXT NOT NULL
);

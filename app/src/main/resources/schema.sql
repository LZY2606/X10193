CREATE TABLE IF NOT EXISTS schemes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    policy TEXT NOT NULL,
    load_order_json TEXT NOT NULL,
    report_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS binaries (
    id TEXT PRIMARY KEY,
    scheme_id TEXT NOT NULL REFERENCES schemes(id),
    ordinal INTEGER NOT NULL,
    input_name TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    parse_kind TEXT NOT NULL,
    parse_status TEXT NOT NULL,
    error TEXT,
    content BLOB NOT NULL,
    UNIQUE(scheme_id, ordinal)
);

CREATE INDEX IF NOT EXISTS idx_binaries_scheme ON binaries(scheme_id);

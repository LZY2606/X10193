CREATE TABLE IF NOT EXISTS scheme (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    platform TEXT NOT NULL,
    policy TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    report_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scheme_file (
    scheme_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    name TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    content BLOB NOT NULL,
    PRIMARY KEY (scheme_id, position),
    FOREIGN KEY (scheme_id) REFERENCES scheme(id)
);

CREATE TABLE IF NOT EXISTS artifacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    size INTEGER NOT NULL,
    sha256 TEXT NOT NULL,
    content BLOB NOT NULL,
    uploaded_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    policy_id TEXT NOT NULL,
    load_order TEXT NOT NULL,
    result_json TEXT,
    parsed_json TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scheme_artifacts (
    scheme_id INTEGER NOT NULL,
    artifact_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (scheme_id, artifact_id)
);

CREATE INDEX IF NOT EXISTS idx_scheme_artifacts_scheme ON scheme_artifacts(scheme_id);

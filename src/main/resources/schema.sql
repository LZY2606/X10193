CREATE TABLE IF NOT EXISTS artifacts (
    id TEXT PRIMARY KEY,
    filename TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    content_path TEXT NOT NULL,
    kind TEXT NOT NULL,
    parse_errors TEXT NOT NULL,
    object_count INTEGER NOT NULL,
    uploaded_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_payloads (
    artifact_id TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL,
    FOREIGN KEY(artifact_id) REFERENCES artifacts(id)
);

CREATE TABLE IF NOT EXISTS schemes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    policy_json TEXT NOT NULL,
    load_order_json TEXT NOT NULL,
    parent_scheme_id TEXT,
    result_json TEXT,
    created_at INTEGER NOT NULL
);

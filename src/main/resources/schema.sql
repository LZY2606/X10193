CREATE TABLE IF NOT EXISTS scheme (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    policy_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS scheme_object (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL,
    seq INTEGER NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    sha256 TEXT,
    content BLOB,
    corrupt INTEGER NOT NULL DEFAULT 0,
    corrupt_reason TEXT,
    meta TEXT
);
CREATE TABLE IF NOT EXISTS verdict (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL,
    symbol_name TEXT NOT NULL,
    version TEXT,
    status TEXT NOT NULL,
    chosen TEXT,
    chosen_value INTEGER,
    reason TEXT,
    copy_reloc INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS candidate (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    verdict_id INTEGER NOT NULL,
    object TEXT NOT NULL,
    bind TEXT,
    type TEXT,
    visibility TEXT,
    version TEXT,
    defined INTEGER NOT NULL DEFAULT 0,
    common INTEGER NOT NULL DEFAULT 0,
    size INTEGER NOT NULL DEFAULT 0,
    value INTEGER NOT NULL DEFAULT 0,
    eliminated INTEGER NOT NULL DEFAULT 0,
    reason TEXT
);
CREATE TABLE IF NOT EXISTS reloc (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    verdict_id INTEGER NOT NULL,
    object TEXT NOT NULL,
    section TEXT,
    reloc_offset INTEGER NOT NULL DEFAULT 0,
    type INTEGER NOT NULL DEFAULT 0,
    provider TEXT
);
CREATE TABLE IF NOT EXISTS event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL,
    seq INTEGER NOT NULL,
    kind TEXT NOT NULL,
    message TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS dependency (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL,
    from_object TEXT NOT NULL,
    to_object TEXT NOT NULL,
    kind TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_object_scheme ON scheme_object(scheme_id);
CREATE INDEX IF NOT EXISTS idx_verdict_scheme ON verdict(scheme_id);
CREATE INDEX IF NOT EXISTS idx_candidate_verdict ON candidate(verdict_id);
CREATE INDEX IF NOT EXISTS idx_reloc_verdict ON reloc(verdict_id);
CREATE INDEX IF NOT EXISTS idx_event_scheme ON event(scheme_id);
CREATE INDEX IF NOT EXISTS idx_dep_scheme ON dependency(scheme_id);

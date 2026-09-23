CREATE TABLE IF NOT EXISTS blob_store (
    sha256 TEXT PRIMARY KEY,
    bytes BLOB NOT NULL,
    filename TEXT
);

CREATE TABLE IF NOT EXISTS scheme (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    policy TEXT NOT NULL,
    weak_archive_pull INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    parent_id TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS scheme_input (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    role TEXT NOT NULL,
    blob_sha TEXT NOT NULL,
    member_path TEXT
);

CREATE TABLE IF NOT EXISTS object_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    input_id INTEGER,
    key TEXT NOT NULL,
    display TEXT NOT NULL,
    kind TEXT NOT NULL,
    ei_class INTEGER,
    machine INTEGER,
    soname TEXT,
    parse_error TEXT,
    ordinal INTEGER
);

CREATE TABLE IF NOT EXISTS dependency_edge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    from_key TEXT NOT NULL,
    to_key TEXT NOT NULL,
    needed TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS version_node (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    object_key TEXT NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    hash TEXT,
    parent_name TEXT,
    is_default INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS symbol_def (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    object_key TEXT NOT NULL,
    sym_index INTEGER NOT NULL,
    name TEXT NOT NULL,
    version_name TEXT,
    binding TEXT NOT NULL,
    type TEXT NOT NULL,
    visibility TEXT NOT NULL,
    shndx TEXT NOT NULL,
    value INTEGER,
    size INTEGER,
    exported INTEGER NOT NULL,
    defined INTEGER NOT NULL,
    discarded INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS reference (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    object_key TEXT NOT NULL,
    name TEXT NOT NULL,
    required_version TEXT,
    weak INTEGER NOT NULL,
    reloc_type INTEGER
);

CREATE TABLE IF NOT EXISTS verdict (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    object_key TEXT NOT NULL,
    name TEXT NOT NULL,
    required_version TEXT,
    weak INTEGER NOT NULL,
    status TEXT NOT NULL,
    chosen_object TEXT,
    chosen_version TEXT,
    chosen_binding TEXT,
    ordinal_key TEXT,
    note TEXT
);

CREATE TABLE IF NOT EXISTS candidate (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    verdict_id INTEGER NOT NULL,
    object_key TEXT NOT NULL,
    symbol_index INTEGER NOT NULL,
    binding TEXT,
    visibility TEXT,
    version_name TEXT,
    selected INTEGER NOT NULL,
    eliminated_reason TEXT,
    rank INTEGER
);

CREATE TABLE IF NOT EXISTS affected_reloc (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    verdict_id INTEGER,
    object_key TEXT NOT NULL,
    section TEXT,
    offset INTEGER NOT NULL,
    reloc_type INTEGER NOT NULL,
    symbol_name TEXT,
    addend INTEGER,
    role TEXT
);

CREATE TABLE IF NOT EXISTS timeline_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    detail TEXT,
    member TEXT,
    symbol TEXT,
    symbol_table TEXT
);

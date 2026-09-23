CREATE TABLE IF NOT EXISTS objects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    elf_type INTEGER,
    content BLOB,
    parse_error TEXT,
    parsed_json TEXT,
    created TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    policy TEXT NOT NULL,
    result_json TEXT,
    created TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS scheme_items (
    scheme_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    object_id INTEGER NOT NULL
);

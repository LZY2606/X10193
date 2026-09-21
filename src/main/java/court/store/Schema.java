package court.store;

/**
 * SQLite 表结构。旧方案结果以 JSON 快照存储，永不随代码/顺序变化漂移。
 */
public final class Schema {
    public static final String DDL = """
            CREATE TABLE IF NOT EXISTS upload (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,              -- ELF / ARCHIVE / BROKEN
                raw BLOB NOT NULL,
                sha256 TEXT NOT NULL,
                parse_error TEXT,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS scheme (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                version_no INTEGER NOT NULL,
                policy TEXT NOT NULL,
                load_order_json TEXT NOT NULL,  -- 显式上传 id 顺序
                decision_json TEXT NOT NULL,    -- 裁决结果快照
                created_at TEXT NOT NULL,
                UNIQUE(name, version_no)
            );
            CREATE TABLE IF NOT EXISTS scheme_upload (
                scheme_id TEXT NOT NULL,
                upload_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (scheme_id, position)
            );
            """;

    private Schema() {
    }
}

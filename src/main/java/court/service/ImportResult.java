package court.service;

import court.engine.InputEntry;
import court.model.BadObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次导入批次的解析结果。每个上传对象独立 try/catch：损坏对象只隔离自身。
 */
public final class ImportResult {
    public final List<InputEntry> entries = new ArrayList<>();
    public final List<BadObject> bad = new ArrayList<>();
}

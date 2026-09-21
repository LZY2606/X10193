package court.service;

import court.elf.ArchiveParser;
import court.elf.ElfException;
import court.elf.ElfObject;
import court.elf.ElfParser;
import court.engine.InputEntry;
import court.model.BadObject;
import court.store.CourtRepository;
import court.store.UploadRow;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 把上传字节解析为引擎输入。三类：
 *  - ELF：直接解析；
 *  - archive：逐成员解析，坏成员进入 BadObject，好成员进入惰性候选；
 *  - 其它/损坏：登记为 BROKEN，隔离自身。
 */
@Service
public class ImportService {

    private final CourtRepository repo;

    public ImportService(CourtRepository repo) {
        this.repo = repo;
    }

    public record StoredUpload(String uploadId, String name, String kind, String parseError) {
    }

    public StoredUpload store(String name, byte[] data) {
        String sha = sha256(data);
        String id = "u_" + sha.substring(0, 16);
        String kind = "BROKEN";
        String parseError = null;
        if (ArchiveParser.isArchive(data)) {
            kind = "ARCHIVE";
        } else {
            try {
                ElfParser.parse(data, id, name);
                kind = "ELF";
            } catch (RuntimeException e) {
                kind = "BROKEN";
                parseError = rootMessage(e);
            }
        }
        UploadRow existing = repo.getUpload(id);
        if (existing == null) {
            repo.insertUpload(new UploadRow(id, name, kind, data, sha, parseError,
                    Instant.now().toString()));
        }
        return new StoredUpload(id, name, kind, parseError);
    }

    /** 把方案需要的上传记录重新解析为引擎输入（方案结果是快照，但允许重新执行产生新版本）。 */
    public InputEntry toEntry(String uploadId, int order) {
        UploadRow row = repo.getUpload(uploadId);
        if (row == null) {
            throw new IllegalArgumentException("上传对象不存在: " + uploadId);
        }
        if ("ARCHIVE".equals(row.kind)) {
            List<InputEntry.ArchiveMemberInput> members = new ArrayList<>();
            try {
                List<ArchiveParser.Member> raw = ArchiveParser.parse(row.raw);
                for (ArchiveParser.Member m : raw) {
                    try {
                        ElfObject elf = ElfParser.parse(m.data,
                                uploadId + "::" + m.name, row.name + "(" + m.name + ")");
                        elf.archiveMember = m.name;
                        elf.sourcePath = row.name;
                        members.add(new InputEntry.ArchiveMemberInput(m.name, elf, null));
                    } catch (RuntimeException e) {
                        members.add(new InputEntry.ArchiveMemberInput(m.name, null, rootMessage(e)));
                    }
                }
            } catch (ElfException e) {
                // archive 容器本身损坏：整体隔离
                return new InputEntry(uploadId, row.name, null,
                        List.of(new InputEntry.ArchiveMemberInput("<archive>", null,
                                "archive 解析失败: " + e.getMessage())), order);
            }
            return new InputEntry(uploadId, row.name, null, members, order);
        }
        try {
            ElfObject elf = ElfParser.parse(row.raw, uploadId, row.name);
            elf.sourcePath = row.name;
            return new InputEntry(uploadId, row.name, elf, null, order);
        } catch (RuntimeException e) {
            return new InputEntry(uploadId, row.name, null,
                    List.of(new InputEntry.ArchiveMemberInput(row.name, null, rootMessage(e))),
                    order);
        }
    }

    public List<BadObject> collectBadObjects(InputEntry entry) {
        List<BadObject> out = new ArrayList<>();
        if (entry.isArchive()) {
            for (InputEntry.ArchiveMemberInput m : entry.members) {
                if (m.elf == null) {
                    out.add(new BadObject(entry.uploadId + "::" + m.name,
                            entry.name + "(" + m.name + ")", m.parseError));
                }
            }
        }
        return out;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

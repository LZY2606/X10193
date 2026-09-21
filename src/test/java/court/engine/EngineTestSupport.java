package court.engine;

import court.elf.ArchiveParser;
import court.elf.ElfParser;
import court.model.BadObject;
import court.model.Decision;
import court.model.Policy;

import java.util.ArrayList;
import java.util.List;

/** 引擎测试支持：内存字节 -> InputEntry -> LinkEngine，绕过 SQLite/HTTP。 */
final class EngineTestSupport {

    private record Unit(String id, String name, byte[] bytes, boolean archive) {
    }

    private final List<Unit> units = new ArrayList<>();

    static EngineTestSupport create() {
        return new EngineTestSupport();
    }

    EngineTestSupport elf(String id, String name, byte[] bytes) {
        units.add(new Unit(id, name, bytes, false));
        return this;
    }

    EngineTestSupport archive(String id, String name, byte[] bytes) {
        units.add(new Unit(id, name, bytes, true));
        return this;
    }

    Decision run(Policy policy) {
        List<InputEntry> entries = new ArrayList<>();
        List<BadObject> bad = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (u.archive) {
                List<InputEntry.ArchiveMemberInput> members = new ArrayList<>();
                for (ArchiveParser.Member m : ArchiveParser.parse(u.bytes)) {
                    try {
                        members.add(new InputEntry.ArchiveMemberInput(m.name,
                                ElfParser.parse(m.data, u.id + "::" + m.name,
                                        u.name + "(" + m.name + ")"), null));
                    } catch (RuntimeException e) {
                        members.add(new InputEntry.ArchiveMemberInput(m.name, null, e.getMessage()));
                    }
                }
                entries.add(new InputEntry(u.id, u.name, null, members, i));
            } else {
                try {
                    entries.add(new InputEntry(u.id, u.name,
                            ElfParser.parse(u.bytes, u.id, u.name), null, i));
                } catch (RuntimeException e) {
                    bad.add(new BadObject(u.id, u.name, e.getMessage()));
                    entries.add(new InputEntry(u.id, u.name, null,
                            List.of(new InputEntry.ArchiveMemberInput(u.name, null, e.getMessage())), i));
                }
            }
        }
        return LinkEngine.run("s_test", "test", policy, entries, bad);
    }
}

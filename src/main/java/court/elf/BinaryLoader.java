package court.elf;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Parses raw uploaded bytes into {@link LoadedFile}s; never throws for bad content. */
@Component
public class BinaryLoader {

    public LoadedFile load(int order, String fileName, byte[] raw) {
        LoadedFile f = new LoadedFile();
        f.order = order;
        f.fileName = fileName;
        f.raw = raw;
        try {
            if (ArReader.looksLikeArchive(raw)) {
                f.kind = "ARCHIVE";
                f.archive = ArReader.readIndex(raw);
                if (!f.archive.valid) {
                    f.corrupt = true;
                    f.error = "archive index unreadable: " + f.archive.error;
                    f.kind = "CORRUPT";
                }
            } else if (raw != null && raw.length >= 4 && raw[0] == 0x7f && raw[1] == 'E'
                    && raw[2] == 'L' && raw[3] == 'F') {
                f.kind = "ELF";
                f.image = ElfReader.read(raw);
                if (f.image.corrupt) {
                    f.corrupt = true;
                    f.error = f.image.error;
                }
            } else {
                f.kind = "CORRUPT";
                f.corrupt = true;
                f.error = "not an ELF object or ar archive";
            }
        } catch (Throwable t) {
            f.kind = "CORRUPT";
            f.corrupt = true;
            f.error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        return f;
    }

    /** Lazily parse one archive member; corruption stays local to that member. */
    public LoadedBinary loadMember(LoadedFile file, ArReader.Member member) {
        LoadedBinary b = new LoadedBinary();
        b.sourceFile = file.fileName;
        b.memberName = member.name;
        b.memberOrder = member.order;
        b.unitId = file.fileName + "(" + member.name + ")";
        byte[] data = ArReader.memberData(file.raw, member);
        ElfImage img = ElfReader.read(data);
        b.image = img;
        b.corrupt = img.corrupt;
        b.error = img.error;
        file.members.add(b);
        return b;
    }
}

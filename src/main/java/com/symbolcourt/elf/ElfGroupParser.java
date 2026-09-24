package com.symbolcourt.elf;

import com.symbolcourt.model.ElfSection;
import com.symbolcourt.model.GroupInfo;
import com.symbolcourt.model.ParsedElf;

class ElfGroupParser {

    private static final int GRP_COMDAT = 1;

    void parseGroups(ByteReader r, ParsedElf elf, boolean elf64) {
        for (ElfSection s : elf.sections) {
            if (s.type != ElfSection.SHT_GROUP) continue;
            if (s.data.length < 4) continue;
            GroupInfo g = new GroupInfo();
            int flagsWord = r.u32(s.offset);
            g.comdat = (flagsWord & GRP_COMDAT) != 0;
            int count = (int) (s.data.length / 4L);
            int[] members = new int[Math.max(0, count - 1)];
            for (int i = 1; i < count; i++) {
                members[i - 1] = (int) r.u32(s.offset + i * 4L);
            }
            g.sectionIndices = members;
            ElfSection sigSec = elf.sectionAt(s.link);
            if (sigSec != null) {
                try {
                    g.signature = r.cstringInSection(s.info & 0xffffffffL, sigSec.offset, sigSec.size);
                } catch (ElfFormatException e) {
                    g.signature = "<bad-group-signature>";
                }
            }
            elf.groups.add(g);
        }
    }
}

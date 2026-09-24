package com.symbolcourt.elf;

import com.symbolcourt.model.ArchiveFile;
import com.symbolcourt.model.ParsedElf;
import com.symbolcourt.model.Enums.ObjectKind;
import com.symbolcourt.resolve.InputUnit;

import java.nio.charset.StandardCharsets;

/** Detects ELF vs ar archive and builds the right input unit. */
public class BinaryLoader {

    private final ElfParser elfParser = new ElfParser();
    private final ArParser arParser = new ArParser();

    public static boolean isArMagic(byte[] data) {
        return data != null && data.length >= 8
                && new String(data, 0, 8, StandardCharsets.US_ASCII).equals("!<arch>\n");
    }

    public static boolean isElfMagic(byte[] data) {
        return data != null && data.length >= 4 && data[0] == 0x7f
                && data[1] == 'E' && data[2] == 'L' && data[3] == 'F';
    }

    public InputUnit load(byte[] data, String displayName, int order) {
        if (isArMagic(data)) {
            ArchiveFile ar = arParser.parse(data, displayName);
            InputUnit u = new InputUnit("u" + order, InputUnit.UnitType.ARCHIVE, order, null, ar);
            if (ar.corrupt) u.type = InputUnit.UnitType.CORRUPT;
            return u;
        }
        ParsedElf elf = elfParser.parse(data, displayName, displayName);
        InputUnit.UnitType t;
        if (elf.corrupt) t = InputUnit.UnitType.CORRUPT;
        else if (elf.kind == ObjectKind.SHARED) t = InputUnit.UnitType.SHARED;
        else if (elf.kind == ObjectKind.EXECUTABLE) t = InputUnit.UnitType.EXECUTABLE;
        else t = InputUnit.UnitType.OBJECT;
        return new InputUnit("u" + order, t, order, elf, null);
    }
}

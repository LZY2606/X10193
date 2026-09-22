package com.symbolcourt.service;

import com.symbolcourt.elf.ArchiveParser;
import com.symbolcourt.elf.ElfConstants;
import com.symbolcourt.model.InputEntry;
import org.springframework.stereotype.Component;

/**
 * Determines the role of an imported binary. ET_REL -> object,
 * ET_DYN/ET_EXEC -> shared/executable, ar magic -> archive. We do not trust
 * the file extension; the content decides.
 */
@Component
public class FileClassifier {

    private final ArchiveParser archiveParser = new ArchiveParser();

    public InputEntry.Kind classify(byte[] data, String name) {
        if (archiveParser.isArchive(data)) {
            return InputEntry.Kind.ARCHIVE;
        }
        if (data != null && data.length >= 18 && data[0] == 0x7f && data[1] == 'E'
                && data[2] == 'L' && data[3] == 'F') {
            int type = data[16] & 0xff | ((data[17] & 0xff) << 8);
            if (type == ElfConstants.ET_REL) {
                return InputEntry.Kind.OBJECT;
            }
            return InputEntry.Kind.SHARED;
        }
        return null;
    }
}

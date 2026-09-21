package org.tribunal.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.tribunal.model.ArchiveMember;
import org.tribunal.model.BinaryKind;
import org.tribunal.model.ElfFile;
import org.tribunal.model.ParsedBinary;
import org.tribunal.parser.ArchiveParser;
import org.tribunal.parser.ElfFormatException;
import org.tribunal.parser.ElfParser;
import org.tribunal.repository.BinaryRepository;

@Service
public class DefaultBinaryService implements BinaryService {
    private final BinaryRepository repository;
    private final ArchiveParser archiveParser = new ArchiveParser();

    public DefaultBinaryService(BinaryRepository repository) {
        this.repository = repository;
    }

    @Override
    public ParsedBinary importBinary(String filename, byte[] payload) {
        ParsedBinary parsed = parseInMemory(filename, payload);
        return repository.save(filename, payload, parsed);
    }

    @Override
    public ParsedBinary parseInMemory(String filename, byte[] payload) {
        String unitId = "binary:" + filename;
        if (archiveParser.supports(payload)) {
            List<ArchiveMember> members = archiveParser.parse(unitId, filename, payload);
            boolean any = members.stream().anyMatch(ArchiveMember::parseOk);
            String error = any && members.stream().anyMatch(member -> !member.parseOk())
                    ? "Some archive members are corrupt and were isolated"
                    : any ? null : "Archive contains no parseable ELF members";
            return new ParsedBinary(0, filename, "ARCHIVE", "", "", payload.length, any, error, null, members);
        }
        try {
            ElfFile elf = new ElfParser(unitId, filename).parse(payload);
            return new ParsedBinary(0, filename, elf.kind().name(), "", "", payload.length, true, null, elf, List.of());
        } catch (ElfFormatException | RuntimeException ex) {
            return new ParsedBinary(0, filename, BinaryKind.UNKNOWN.name(), "", "", payload.length, false,
                    ex.getMessage(), null, List.of());
        }
    }

    @Override
    public List<ParsedBinary> list() {
        return repository.findAll();
    }

    @Override
    public byte[] payload(long id) {
        return repository.readPayload(id);
    }
}

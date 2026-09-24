package arb.ar;

import arb.elf.ElfException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArchiveReader {

    private static final String MAGIC = "!<arch>\n";

    public static boolean isArchive(byte[] data) {
        if (data == null || data.length < MAGIC.length()) {
            return false;
        }
        for (int i = 0; i < MAGIC.length(); i++) {
            if (data[i] != (byte) MAGIC.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public Archive read(String name, byte[] data) throws ElfException {
        if (!isArchive(data)) {
            throw new ElfException("not an ar archive");
        }
        List<ArchiveMember> members = new ArrayList<>();
        int pos = MAGIC.length();
        byte[] longNames = null;
        while (pos + 60 <= data.length) {
            String rawName = new String(data, pos, 16, StandardCharsets.US_ASCII).trim();
            long size;
            try {
                String sizeField = new String(data, pos + 48, 10,
                        StandardCharsets.US_ASCII).trim();
                size = Long.parseLong(sizeField);
            } catch (NumberFormatException e) {
                throw new ElfException("corrupt archive member header at " + pos);
            }
            long dataStart = pos + 60L;
            if (dataStart + size > data.length || size < 0) {
                throw new ElfException("archive member " + rawName + " extends past EOF");
            }
            byte[] memberData = new byte[(int) size];
            System.arraycopy(data, (int) dataStart, memberData, 0, (int) size);

            if (rawName.equals("/") || rawName.equals("/SYM64/") || rawName.startsWith("/SYM")) {
                // symbol index - skipped; lazy extraction is driven by own scan
            } else if (rawName.equals("//")) {
                longNames = memberData;
            } else {
                String memberName = resolveName(rawName, longNames);
                members.add(new ArchiveMember(memberName, memberData, pos, false, null));
            }
            pos = (int) (dataStart + size);
            if (size % 2 == 1) {
                if (pos < data.length) {
                    pos++;
                }
            }
        }
        return new Archive(name, members);
    }

    private String resolveName(String rawName, byte[] longNames) {
        if (rawName.startsWith("/")) {
            String digits = rawName.substring(1);
            if (digits.matches("\\d+") && longNames != null) {
                int offset = Integer.parseInt(digits);
                if (offset >= 0 && offset < longNames.length) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = offset; i < longNames.length; i++) {
                        byte b = longNames[i];
                        if (b == '/' || b == '\n' || b == 0) {
                            break;
                        }
                        if (b == '`' && i + 1 < longNames.length && longNames[i + 1] == '\n') {
                            break;
                        }
                        sb.append((char) (b & 0xff));
                    }
                    return sb.toString().trim();
                }
            }
        }
        int slash = rawName.indexOf('/');
        return slash >= 0 ? rawName.substring(0, slash) : rawName;
    }
}

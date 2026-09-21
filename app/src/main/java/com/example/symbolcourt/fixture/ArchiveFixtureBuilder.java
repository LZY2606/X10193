package com.example.symbolcourt.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ArchiveFixtureBuilder {
    private final Map<String, byte[]> members = new LinkedHashMap<>();

    public ArchiveFixtureBuilder member(String name, byte[] content) {
        members.put(normalize(name), content);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream longNames = new ByteArrayOutputStream();
        Map<String, String> shortNames = new LinkedHashMap<>();
        int longOffset = 0;
        for (String name : members.keySet()) {
            if (name.length() <= 15) {
                shortNames.put(name, pad(name + "/", 16));
            } else {
                shortNames.put(name, pad("/" + longOffset, 16));
                longNames.writeBytes((name + "/\n").getBytes(StandardCharsets.US_ASCII));
                longOffset += name.length() + 2;
            }
        }
        if (longNames.size() > 0) {
            writeMember(out, "//", longNames.toByteArray());
        }
        for (Map.Entry<String, byte[]> entry : members.entrySet()) {
            writeMember(out, shortNames.get(entry.getKey()), entry.getValue());
        }
        return out.toByteArray();
    }

    private void writeMember(ByteArrayOutputStream out, String rawName, byte[] content) {
        String header = pad(rawName, 16)
                + pad("0", 12)
                + pad("0", 6)
                + pad("0", 6)
                + pad("100644", 8)
                + pad(String.valueOf(content.length), 10)
                + "`\n";
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(content);
        if ((content.length & 1) == 1) {
            out.write('\n');
        }
    }

    private String normalize(String name) {
        return name.endsWith(".o") ? name : name + ".o";
    }

    private String pad(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }
}

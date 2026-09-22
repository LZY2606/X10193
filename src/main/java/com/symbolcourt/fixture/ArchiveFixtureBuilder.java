package com.symbolcourt.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArchiveFixtureBuilder {

    private final Map<String, byte[]> members = new LinkedHashMap<>();

    public ArchiveFixtureBuilder member(String name, byte[] content) {
        members.put(name.endsWith("/") ? name : name + "/", content);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        for (Map.Entry<String, byte[]> entry : members.entrySet()) {
            byte[] body = entry.getValue();
            String header = String.format("%-16s%-12d%-6d%-6d%-8o%-10d`\n",
                    entry.getKey(), 0, 0, 0, 0, body.length);
            assert header.length() == 60 : header.length();
            out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(body);
            if (body.length % 2 == 1) {
                out.write('\n');
            }
        }
        return out.toByteArray();
    }
}

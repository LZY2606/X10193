package com.example.tribunal.core.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArchiveFixtureBuilder {
    public record Member(String name, byte[] content) {}
    private final List<Member> members = new ArrayList<>();

    public ArchiveFixtureBuilder add(String name, byte[] content) {
        members.add(new Member(name, content));
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "!<arch>\n");
        StringBuilder longNames = new StringBuilder();
        for (Member member : members) {
            if (member.name().length() >= 16) longNames.append(member.name()).append("/\n");
        }
        if (longNames.length() > 0) {
            byte[] longBytes = longNames.toString().getBytes(StandardCharsets.UTF_8);
            writeHeader(out, "//", longBytes.length);
            write(out, longBytes);
            if ((longBytes.length & 1) == 1) write(out, "\n");
        }
        int longOffset = 0;
        for (Member member : members) {
            byte[] content = member.content();
            String nameField;
            if (member.name().length() >= 16) {
                nameField = "/" + longOffset;
                longOffset += member.name().length() + 2;
            } else {
                nameField = member.name() + "/";
            }
            writeHeader(out, nameField, content.length);
            write(out, content);
            if ((content.length & 1) == 1) write(out, "\n");
        }
        return out.toByteArray();
    }

    private void writeHeader(ByteArrayOutputStream out, String name, int size) {
        StringBuilder header = new StringBuilder(pad(name, 16));
        header.append(pad("0", 12)).append(pad("0", 6)).append(pad("0", 6)).append(pad("100644", 8));
        header.append(pad(Integer.toString(size), 10)).append(pad("`", 2));
        write(out, header.toString());
    }

    private String pad(String value, int length) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) builder.append(' ');
        return builder.toString();
    }

    private void write(ByteArrayOutputStream out, String value) {
        write(out, value.getBytes(StandardCharsets.US_ASCII));
    }

    private void write(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }
}

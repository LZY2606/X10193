package com.symcourt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** System V ar archive fixture builder. */
public final class ArBuilder {

    private final Map<String, byte[]> members = new LinkedHashMap<>();

    public ArBuilder member(String name, byte[] data) {
        members.put(name, data);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        members.forEach((name, data) -> {
            String header = pad(name + "/", 16) + pad("0", 12) + pad("0", 6) + pad("0", 6)
                    + pad("100644", 8) + pad(String.valueOf(data.length), 10) + "`\n";
            out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(data);
            if ((data.length & 1) != 0) {
                out.write('\n');
            }
        });
        return out.toByteArray();
    }

    private static String pad(String s, int width) {
        if (s.length() > width) {
            throw new IllegalArgumentException("field too long: " + s);
        }
        return s + " ".repeat(width - s.length());
    }
}

package court.symbol.fixture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArchiveFixtureBuilder {
    private final Map<String, byte[]> members = new LinkedHashMap<>();

    public ArchiveFixtureBuilder member(String name, byte[] data) {
        members.put(name, data);
        return this;
    }

    public byte[] build() {
        ByteArrayOutputStream longNames = new ByteArrayOutputStream();
        Map<String, String> shortNames = new LinkedHashMap<>();
        int numeric = 0;
        for (String name : members.keySet()) {
            String simple = name.length() <= 15 ? name + "/" : "/" + numeric++ + "";
            if (name.length() > 15) {
                simple = "/" + (longNames.size());
                longNames.writeBytes(name.getBytes(StandardCharsets.US_ASCII));
                longNames.write('/');
                longNames.write('\n');
            }
            shortNames.put(name, simple);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        if (!longNames.toString(StandardCharsets.US_ASCII).isEmpty()) {
            writeMember(output, "//", longNames.toByteArray());
        }
        for (Map.Entry<String, byte[]> entry : members.entrySet()) {
            writeMember(output, shortNames.get(entry.getKey()), entry.getValue());
        }
        return output.toByteArray();
    }

    private void writeMember(ByteArrayOutputStream output, String name, byte[] data) {
        StringBuilder header = new StringBuilder();
        header.append(padRight(name, 16));
        header.append(padRight("0", 12));
        header.append(padRight("0", 6));
        header.append(padRight("0", 6));
        header.append(padRight("100644", 8));
        header.append(padRight(String.valueOf(data.length), 10));
        header.append("`\n");
        output.writeBytes(header.toString().getBytes(StandardCharsets.US_ASCII));
        output.writeBytes(data);
        if (data.length % 2 == 1) {
            output.write('\n');
        }
    }

    private String padRight(String value, int length) {
        StringBuilder result = new StringBuilder(value);
        while (result.length() < length) {
            result.append(' ');
        }
        return result.substring(0, length);
    }
}

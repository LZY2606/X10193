package court.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One explicitly ordered input supplied to the court. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class InputEntry {
    public String id;
    public String name;
    @JsonIgnore
    public byte[] data;
    public String detectedKind; // ELF_REL / ELF_DYN / ELF_EXEC / ARCHIVE / CORRUPT
    public String corruptReason;

    public InputEntry() {
    }

    public InputEntry(String id, String name, byte[] data) {
        this.id = id;
        this.name = name;
        this.data = data;
    }
}

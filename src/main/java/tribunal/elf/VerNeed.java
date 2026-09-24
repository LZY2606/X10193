package tribunal.elf;

import java.util.ArrayList;
import java.util.List;

public class VerNeed {
    public String fileName;
    public final List<VAux> aux = new ArrayList<>();

    public static class VAux {
        public int flags;
        public int index;
        public String name;
        public boolean hidden;
        public boolean weak;
        public List<String> aliases = new ArrayList<>();
    }
}

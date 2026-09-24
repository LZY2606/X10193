package tribunal.engine;

import java.util.ArrayList;
import java.util.List;

/** 版本图节点：定义/需求版本及其别名。 */
public class VersionNode {
    public String unit;
    public String kind;
    public String name;
    public int index;
    public boolean nonDefault;
    public String parent;
    public final List<String> symbols = new ArrayList<>();
    public final List<String> aliases = new ArrayList<>();
}

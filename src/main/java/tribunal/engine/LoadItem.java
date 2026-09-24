package tribunal.engine;

/** 方案输入中的一个显式加载条目。groupStart/groupEnd 标记 --start-group/--end-group 范围。 */
public class LoadItem {
    public String objectHash;
    public String label;
    public boolean groupStart;
    public boolean groupEnd;

    public LoadItem() {}

    public LoadItem(String objectHash, String label) {
        this.objectHash = objectHash;
        this.label = label;
    }
}

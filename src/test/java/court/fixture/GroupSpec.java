package court.fixture;

import java.util.List;

/**
 * COMDAT 组规格：签名 + 成员 section 的数据（每个成员作为独立 PROGBITS 节）。
 * 组 section 的 sh_info 指向签名字符串对应的符号（构造器自动创建）。
 */
public final class GroupSpec {
    public final String signature;
    public final List<byte[]> memberData;

    public GroupSpec(String signature, List<byte[]> memberData) {
        this.signature = signature;
        this.memberData = memberData;
    }

    /** 构造时填充：成员 section 索引（1 基，含 null section）。 */
    public transient List<Integer> _memberIndices;
}

package com.symbolcourt.linker;

/** 目标平台链接策略；策略不同 => 生成新的不可变方案版本。 */
public class Policy {
    /** 单遍严格顺序（ld 风格默认）。 */
    public boolean singlePass = true;
    /** --start-group/--end-group：archive 反复扫描至定点。 */
    public boolean repeatArchiveGroups = false;
    /** 弱未定义引用也会抽取 archive 成员。 */
    public boolean weakTriggersArchive = false;
    /** 共享库只在引用已出现的位置之后解析（as-needed 语义近似）。 */
    public boolean asNeeded = false;
    /** 允许对 PROTECTED 数据做 copy relocation（传统 glibc 宽松行为）。 */
    public boolean allowProtectedCopy = true;
    /** COMDAT 选择：first（严格加载顺序）或 largest（部分平台取最大节）。 */
    public String comdatPick = "first";

    public String fingerprint() {
        return "sp=" + singlePass + ",rag=" + repeatArchiveGroups
                + ",wta=" + weakTriggersArchive + ",an=" + asNeeded
                + ",apc=" + allowProtectedCopy + ",cp=" + comdatPick;
    }
}

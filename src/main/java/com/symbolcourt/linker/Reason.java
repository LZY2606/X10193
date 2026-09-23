package com.symbolcourt.linker;

/** 候选淘汰原因（展示给“裁决庭”）。 */
public enum Reason {
    PROVIDED("选中：成为最终提供者"),
    AMBIGUOUS("保留：存在多个同等候选，无法裁决"),
    ORDER_SHADOWED("淘汰：加载顺序中被更早的同等定义遮蔽"),
    WEAK_LOSES_TO_STRONG("淘汰：弱定义让位于强定义"),
    COMMON_LOSES_TO_DEF("淘汰：common 让位于真实定义"),
    COMMON_SMALLER("淘汰：common 合并，取对齐/尺寸更大者"),
    LOCAL_NOT_EXPORTED("淘汰：LOCAL 绑定不跨对象"),
    HIDDEN_NOT_EXPORTED("淘汰：HIDDEN 符号不进入动态查找"),
    PROTECTED_NO_COPY("淘汰/报错：PROTECTED 符号不能被 copy relocation 捕获"),
    VERSION_MISMATCH("淘汰：版本名不匹配引用所需版本"),
    VERSION_NONDEFAULT("说明：非默认版本，仅匹配显式版本引用"),
    VERSION_UNMET("未满足：引用所需版本在提供者处不存在"),
    COMDAT_DISCARDED("淘汰：COMDAT 组去重，该副本被舍弃"),
    ARCHIVE_NOT_NEEDED("淘汰：archive 成员未被任何未决引用需要，保持惰性"),
    ARCHIVE_NOT_EXTRACTED("淘汰：archive 成员在该扫描策略下未被抽取（循环依赖）"),
    WRONG_ARTIFACT("淘汰：符号为内部/重定位专用，不满足引用种类"),
    COPY_RELOCATION("特殊：触发 copy relocation，运行期由可执行文件复制数据"),
    DYNAMIC_AT_REFERENCE("淘汰：该共享库在引用产生时尚未出现在加载序列中"),
    WEAK_ALLOW_NULL("说明：弱引用允许为 0（无候选时保持未定义）"),
    LOCAL_REBIND("说明：LOCAL 引用在对象内部直接绑定");

    public final String label;

    Reason(String label) {
        this.label = label;
    }
}

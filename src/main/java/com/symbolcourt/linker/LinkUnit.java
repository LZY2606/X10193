package com.symbolcourt.linker;

import com.symbolcourt.model.ElfFile;

import java.util.ArrayList;
import java.util.List;

/** 加载序列中的一个输入：普通/共享对象，或 archive 及其成员。 */
public class LinkUnit {
    public int order;
    public String objectName;
    public String kind; // REL | EXEC | DYN | AR
    public ElfFile elf;
    public boolean archive;
    public boolean isExec;

    public static class Member {
        public String name;
        public ElfFile elf;
        public boolean extracted;
        public boolean extractionAttempted;
        /** 抽取触发符号。 */
        public String pulledBy;
        public List<String> provides = new ArrayList<>();
        public List<String> undefined = new ArrayList<>();
    }

    public List<Member> members = new ArrayList<>();
    /** archive 成员选择的 COMDAT 组 / 普通对象 group 索引集合。 */
    public java.util.Set<String> discardedComdats = new java.util.LinkedHashSet<>();
    /** 归档成员被去重：key = groupName@member。 */
    public java.util.Set<String> discardedMemberComdats = new java.util.LinkedHashSet<>();
    public boolean included;
    public String notNeededReason;
}

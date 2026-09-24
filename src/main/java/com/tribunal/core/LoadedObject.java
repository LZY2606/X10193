package com.tribunal.core;

import com.tribunal.elf.ElfFile;
import java.util.HashSet;
import java.util.Set;

/** 参与裁决的一个已加载对象。 */
public class LoadedObject {
    public int seq;                     // 加载顺序（含 archive 抽取序）
    public String name;                 // 显示名
    public ElfFile elf;
    public boolean fromArchive;
    public String archiveName;          // 来源归档
    public final Set<Integer> droppedSections = new HashSet<>(); // COMDAT 去重丢弃

    public boolean sectionDropped(int shndx) {
        return droppedSections.contains(shndx);
    }
}

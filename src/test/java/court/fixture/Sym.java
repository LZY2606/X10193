package court.fixture;

/**
 * fixture 符号描述。shndx=-1=UNDEF，-2=COMMON，-3=ABS；其它为已添加 section 的序号（0 基）。
 */
public final class Sym {
    public final String name;
    public int bind = 1; // STB_GLOBAL
    public int type = 0; // STT_NOTYPE
    public int vis = 0; // STV_DEFAULT
    public int shndx = 0; // 0=UNDEF
    public long value;
    public long size;
    public Integer versionIndex; // dynsym 对应的 verdef/verneed 索引（原始值，含 0x8000）
    public boolean local;

    public Sym(String name) {
        this.name = name;
    }

    public static Sym undef(String name) {
        Sym s = new Sym(name);
        s.shndx = 0;
        return s;
    }

    public static Sym weakUndef(String name) {
        Sym s = undef(name);
        s.bind = 2;
        return s;
    }

    public Sym global() { this.bind = 1; return this; }
    public Sym weak() { this.bind = 2; return this; }
    public Sym localSym() { this.bind = 0; return this; }
    public Sym func() { this.type = 2; return this; }
    public Sym object(long sz) { this.type = 1; this.size = sz; return this; }
    public Sym ifunc() { this.type = 10; return this; }
    public Sym hidden() { this.vis = 2; return this; }
    public Sym internal() { this.vis = 1; return this; }
    public Sym protectedVis() { this.vis = 3; return this; }
    public Sym defined(int secIdx) { this.shndx = secIdx + 1; return this; } // +1: section 0
    public Sym common(long sz) { this.shndx = 0xfff2; this.type = 5; this.size = sz; return this; }
    public Sym abs(long val) { this.shndx = 0xfff1; this.value = val; return this; }
    public Sym at(long value) { this.value = value; return this; }
    public Sym sized(long sz) { this.size = sz; return this; }
    public Sym version(int idx) { this.versionIndex = idx; return this; }
    public Sym nonDefaultVersion(int idx) { this.versionIndex = idx | 0x8000; return this; }
}

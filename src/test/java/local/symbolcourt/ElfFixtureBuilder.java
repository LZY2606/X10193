package local.symbolcourt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElfFixtureBuilder {
    public enum Bind { LOCAL(0), GLOBAL(1), WEAK(2); final int v; Bind(int v){this.v=v;} }
    public enum Type { NOTYPE(0), OBJECT(1), FUNC(2); final int v; Type(int v){this.v=v;} }
    public enum Vis { DEFAULT(0), HIDDEN(2), PROTECTED(3); final int v; Vis(int v){this.v=v;} }
    private record Sym(String name,Bind bind,Type type,Vis vis,int section,long value,long size,Integer version){}
    private record Rel(int symbol,int type,long offset,long addend){}

    private final List<Sym> symbols = new ArrayList<>();
    private final List<Rel> rels = new ArrayList<>();
    private final List<String> needed = new ArrayList<>();
    private int type=1, machine=62;
    private int textIndex=1;
    private String comdatName;

    public ElfFixtureBuilder relocatable(){type=1;return this;}
    public ElfFixtureBuilder shared(){type=3;return this;}
    public ElfFixtureBuilder machine(int value){machine=value;return this;}
    public ElfFixtureBuilder needed(String value){needed.add(value);return this;}
    public ElfFixtureBuilder comdat(String signature){this.comdatName=signature;symbols.add(new Sym(signature,Bind.LOCAL,Type.NOTYPE,Vis.DEFAULT,0,0,0,1));textIndex=7;return this;}
    public int symbol(String name,Bind bind,Type type,Vis vis,int section,long value,long size,Integer version){symbols.add(new Sym(name,bind,type,vis,section,value,size,version));return symbols.size();}
    public int symbol(String name,Bind bind,Type type,Vis vis,long value,Integer version){return symbol(name,bind,type,vis,textIndex,value,1,version);}
    public int common(String name,Bind bind,long size,Integer version){symbols.add(new Sym(name,bind,Type.OBJECT,Vis.DEFAULT,0xfff2,16,size,version));return symbols.size();}
    public int undefined(String name,Bind bind,Type type,Vis vis,Integer version){symbols.add(new Sym(name,bind,type,Vis.DEFAULT,0,0,0,version));return symbols.size();}
    public void rela(int symbol,int type,long offset,long addend){rels.add(new Rel(symbol,type,offset,addend));}

    public byte[] build() {
        String symName = type==3 ? ".dynsym" : ".symtab";
        String strName = type==3 ? ".dynstr" : ".strtab";
        List<String> names = new ArrayList<>(List.of("", ".text", symName, strName, ".gnu.version", ".rela.text", ".dynamic", ".group", ".text.comdat", ".data"));
        byte[] text = { (byte)0xc3 };
        ByteArrayOutputStream str = new ByteArrayOutputStream(); str.write(0);
        Map<String,Integer> off = new LinkedHashMap<>();
        for (Sym s:symbols) if(!off.containsKey(s.name())){off.put(s.name(),str.size());put(str,s.name());}
        int dynStrName = 0;
        ByteArrayOutputStream dynstr = new ByteArrayOutputStream(); dynstr.write(0);
        for(String n:needed){ if(dynStrName==0) dynStrName=str.size(); put(str,n); put(dynstr,n); }
        byte[] symb = symbols(off);
        byte[] versym = versions();
        byte[] rela = relaBytes();
        byte[] group = groupBytes();
        byte[] dynamic = dynamic();
        List<byte[]> datas = List.of(new byte[0], text, symb, str.toByteArray(), versym, rela, dynamic, group, comdatName==null?new byte[0]:text, new byte[4]);
        int count = 11;
        Map<String,Integer> shname = new LinkedHashMap<>();
        ByteArrayOutputStream shstr = new ByteArrayOutputStream(); shstr.write(0);
        for(String n:names){shname.put(n,shstr.size());putRaw(shstr,n);}
        shname.put(".shstrtab", shstr.size()); putRaw(shstr, ".shstrtab");
        if (dynStrName != 0) { shname.put(".dynstr", shstr.size()); putRaw(shstr, ".dynstr"); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x7f,'E','L','F',2,1,1,0,0,0,0,0,0,0,0,0});
        put16(out,type);put16(out,machine);put32(out,1);put64(out,0);put64(out,0);put64(out,0);put32(out,0);put16(out,64);put16(out,0);put16(out,64);put16(out,count);put16(out,count-1);
        while(out.size()%8!=0) out.write(0);
        long[] offsets=new long[count], sizes=new long[count];
        for(int i=1;i<count-1;i++){while(out.size()%8!=0)out.write(0);offsets[i]=out.size();sizes[i]=datas.get(i).length;out.writeBytes(datas.get(i));}
        offsets[count-1]=out.size(); sizes[count-1]=shstr.size(); out.writeBytes(shstr.toByteArray());
        while(out.size()%8!=0)out.write(0); long shoff=out.size();
        sh(out,0,0,0,0,0,0,0,0,0,0);
        sh(out,shname.get(".text"),1,6,0,offsets[1],sizes[1],0,0,1,0);
        sh(out,shname.get(symName),type==3?11:2,0,3,offsets[2],sizes[2],3,symbols.size(),8,24);
        sh(out,shname.get(strName),3,0,0,offsets[3],sizes[3],0,0,1,0);
        sh(out,shname.get(".gnu.version"),0x6fffffff,0,2,offsets[4],sizes[4],0,0,2,2);
        sh(out,shname.get(".rela.text"),4,0,2,offsets[5],sizes[5],2,1,8,24);
        sh(out,shname.get(".dynamic"),6,3,3,offsets[6],sizes[6],0,0,8,16);
        sh(out,shname.get(".group"),17,0,2,offsets[7],sizes[7],2,comdatName==null?0:1,4,4);
        sh(out,shname.get(".text.comdat"),1,6,0,comdatName==null?0:offsets[1],comdatName==null?0:sizes[1],0,0,1,0);
        sh(out,shname.get(".data"),1,3,0,0,0,0,0,1,0);
        sh(out,shname.get(".shstrtab"),3,0,0,offsets[count-1],sizes[count-1],0,0,1,0);
        byte[] result=out.toByteArray(); patch(result,40,shoff); return result;
    }
    private byte[] symbols(Map<String,Integer> off){ByteArrayOutputStream o=new ByteArrayOutputStream();put32(o,0);put32(o,0);put16(o,0);put16(o,0);put16(o,0);put64(o,0);put64(o,0);for(Sym s:symbols){put32(o,off.get(s.name));o.write((s.bind.v<<4)|s.type.v);o.write(s.vis.v);put16(o,s.section);put64(o,s.value);put64(o,s.size);}return o.toByteArray();}
    private byte[] versions(){ByteArrayOutputStream o=new ByteArrayOutputStream();for(Sym s:symbols)put16(o,s.version==null?1:s.version);return o.toByteArray();}
    private byte[] relaBytes(){ByteArrayOutputStream o=new ByteArrayOutputStream();for(Rel r:rels){put64(o,r.offset);put64(o,((long)r.symbol<<32)|(r.type&0xffffffffL));put64(o,r.addend);}return o.toByteArray();}
    private byte[] groupBytes(){if(comdatName==null)return new byte[0];ByteArrayOutputStream o=new ByteArrayOutputStream();put32(o,1);put32(o,1);return o.toByteArray();}
    private byte[] dynamic(){ByteArrayOutputStream o=new ByteArrayOutputStream();for(String n:needed){put64(o,1);put64(o,0);}put64(o,0);put64(o,0);return o.toByteArray();}
    private void sh(ByteArrayOutputStream o,int n,int t,long f,int l,long off,long size,int link,int info,long align,long ent){put32(o,n);put32(o,t);put64(o,f);put64(o,0);put64(o,off);put64(o,size);put32(o,link);put32(o,info);put64(o,align);put64(o,ent);}
    private void put(ByteArrayOutputStream o,String s){o.writeBytes(s.getBytes(StandardCharsets.UTF_8));o.write(0);}
    private void putRaw(ByteArrayOutputStream o,String s){o.writeBytes(s.getBytes(StandardCharsets.UTF_8));o.write(0);}
    private void put16(ByteArrayOutputStream o,int v){o.write(v);o.write(v>>>8);}
    private void put32(ByteArrayOutputStream o,long v){for(int i=0;i<4;i++)o.write((byte)(v>>>(8*i)));}
    private void put64(ByteArrayOutputStream o,long v){for(int i=0;i<8;i++)o.write((byte)(v>>>(8*i)));}
    private void patch(byte[] b,int o,long v){for(int i=0;i<8;i++)b[o+i]=(byte)(v>>>(8*i));}
}

package court.link;

import court.elf.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class DbgTest {
    @Test
    void copy() {
        var l = new BinaryLoader();
        var e = new LinkEngine(l, new UnitExtractor());
        byte[] so = ElfBuilder.dyn().dynDef("global_state", Const.STB_GLOBAL, Const.STT_OBJECT, "", Const.STV_DEFAULT).build();
        byte[] exec = ElfBuilder.dyn().dynUndef("global_state", Const.STB_GLOBAL, null)
                .rela(0x3000, 8, 1, 0, ".bss").build();
        ElfImage img = ElfReader.read(exec);
        System.out.println("exec corrupt="+img.corrupt+" err="+img.error);
        img.sections.forEach(s->System.out.println("SEC '"+s.name+"' t="+s.type+" link="+s.link+" info="+s.info+" sz="+s.size));
        System.out.println("dynsyms="+img.dynSymbols.stream().map(x->x.name).toList());
        System.out.println("relocs="+img.relocations.size());
        img.relocations.forEach(r->System.out.println("R "+r.computedType+" sym="+r.symbolIndex+" sec="+r.sectionName+" write="+r.writeTarget));
        var r = e.resolve(new ArrayList<>(List.of(l.load(0,"prog",exec), l.load(1,"libstate.so",so))), "t", LinkPolicy.x86_64());
        r.verdicts.forEach(v->{System.out.println("VERDICT "+v.name+" "+v.outcome+" copy="+v.copyRelocation+" refs="+v.affectedReferences.size()+" sum="+v.summary);
          v.candidates.forEach(c->System.out.println("  C "+c.displayName+" "+c.status+" "+c.reason));});
    }
}

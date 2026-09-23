package local.symbolcourt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import local.symbolcourt.elf.ElfModels.LoadedFile;
import local.symbolcourt.elf.ElfParser;
import local.symbolcourt.engine.EngineModels.AdjudicationResult;
import local.symbolcourt.engine.EngineModels.Decision;
import local.symbolcourt.engine.EngineModels.InputKind;
import local.symbolcourt.engine.EngineModels.InputRef;
import local.symbolcourt.engine.EngineModels.PlatformPolicy;
import local.symbolcourt.engine.EngineModels.TiePolicy;
import local.symbolcourt.engine.LinkAdjudicator;
import org.junit.jupiter.api.Test;

class LinkAdjudicatorTest {
    private final ElfParser parser = new ElfParser();
    private final LinkAdjudicator adjudicator = new LinkAdjudicator();
    private final PlatformPolicy policy = PlatformPolicy.x8664Linux(TiePolicy.STRICT_AMBIGUOUS);

    @Test void strongBeatsWeakAndCommon() {
        AdjudicationResult result = runNamed(List.of(
                tuple("main.o", defined("resolve_me", ElfFixtureBuilder.Bind.GLOBAL, 0x10)),
                tuple("weak.o", defined("resolve_me", ElfFixtureBuilder.Bind.WEAK, 0x11)),
                tuple("common.o", common("resolve_me"))));
        Decision decision = only(result, "resolve_me");
        assertEquals("SELECTED", decision.status());
        assertTrue(decision.selectedCandidateId().startsWith("input:main.o"));
    }

    @Test void hiddenDefinitionDoesNotCrossObjectBoundary() {
        AdjudicationResult result = runNamed(List.of(tuple("main.o", mainWithHiddenRequest()), tuple("hidden.o", hidden())));
        assertEquals("SELECTED", only(result, "private_thing").status());
        assertTrue(only(result, "private_thing").selectedCandidateId().startsWith("input:main.o"));
    }

    @Test void versionedReferenceRequiresMatchingVersionAlias() {
        AdjudicationResult result = runNamed(List.of(tuple("main.o", versionedRequest(2)), tuple("libv.so", sharedVersioned(1))));
        assertEquals("UNRESOLVED", only(result, "versioned_fn").status());
    }

    @Test void duplicateEqualStrongCandidatesRemainAmbiguous() {
        AdjudicationResult result = runNamed(List.of(tuple("first.o", defined("dup", ElfFixtureBuilder.Bind.GLOBAL, 0x10)),
                tuple("second.o", defined("dup", ElfFixtureBuilder.Bind.GLOBAL, 0x20))));
        assertEquals("AMBIGUOUS", only(result, "dup").status());
    }

    @Test void comdatDuplicatesAreDedupedBeforeSymbolRanking() {
        AdjudicationResult result = runNamed(List.of(tuple("first.o", comdatDefined(0x100)), tuple("second.o", comdatDefined(0x200))));
        assertEquals("SELECTED", only(result, "inline_fn").status());
        assertTrue(only(result, "inline_fn").selectedCandidateId().startsWith("input:first.o"));
    }

    @Test void archiveIsLazilyExtractedAndResolvesCycle() {
        byte[] archive = archive(defined("provide_b", ElfFixtureBuilder.Bind.GLOBAL, 0x30));
        AdjudicationResult result = runNamed(List.of(tuple("a.o", archiveConsumer()), tuple("libab.a", archive)));
        assertTrue(result.activeUnits().stream().anyMatch(u -> u.displayName().equals("libab.a(member0.o)")));
        assertEquals(1, result.extractionEvents().size());
    }

    @Test void corruptObjectIsIsolatedOnly() {
        AdjudicationResult result = runNamed(List.of(tuple("good.o", defined("good_sym", ElfFixtureBuilder.Bind.GLOBAL,0x10)),
                tuple("bad.o", new byte[]{0,1,2,3,4,5,6,7,8,9})));
        assertEquals(1, result.isolatedUnits().size());
    }

    private record Tuple(String name, byte[] data) {}
    private Tuple tuple(String name, byte[] data){return new Tuple(name,data);}
    private Decision only(AdjudicationResult result, String symbol) {
        List<Decision> list = result.decisions().stream().filter(d -> d.symbol().equals(symbol)).toList();
        assertEquals(1, list.size(), result.diagnostics().toString()); return list.get(0);
    }
    private AdjudicationResult runNamed(List<Tuple> files) {
        List<LoadedFile> loaded = new java.util.ArrayList<>(); List<InputRef> refs = new java.util.ArrayList<>();
        for(int i=0;i<files.size();i++) { Tuple t=files.get(i); LoadedFile f=parser.parse(t.name(),t.data(),"application/octet-stream"); loaded.add(f); refs.add(new InputRef(i+1,t.name(),"sha"+i, f.archive()?InputKind.ARCHIVE:InputKind.OBJECT,i,false)); }
        return adjudicator.adjudicate(loaded, refs, policy);
    }
    private byte[] defined(String symbol, ElfFixtureBuilder.Bind bind,long value){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.symbol(symbol,bind,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,value,1);return b.build();}
    private byte[] common(String symbol){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.common(symbol,ElfFixtureBuilder.Bind.GLOBAL,8,1);return b.build();}
    private byte[] mainWithHiddenRequest(){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.symbol("private_thing",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,0x11,1);b.undefined("private_thing",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,1);b.rela(2,1026,0,0);return b.build();}
    private byte[] hidden(){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.symbol("private_thing",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.HIDDEN,0x20,1);return b.build();}
    private byte[] versionedRequest(int version){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.symbol("main",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,0,1);b.undefined("versioned_fn",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,version);b.rela(2,1026,0,0);return b.build();}
    private byte[] sharedVersioned(int version){ElfFixtureBuilder b=new ElfFixtureBuilder().shared();b.symbol("versioned_fn",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,0x30,version);return b.build();}
    private byte[] comdatDefined(long value){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable().comdat("inline_signature");b.symbol("inline_fn",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,7,value,1,1);b.undefined("inline_fn",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,1);b.rela(2,1026,0,0);return b.build();}
    private byte[] archiveConsumer(){ElfFixtureBuilder b=new ElfFixtureBuilder().relocatable();b.symbol("need_b",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,0,1);b.undefined("provide_b",ElfFixtureBuilder.Bind.GLOBAL,ElfFixtureBuilder.Type.FUNC,ElfFixtureBuilder.Vis.DEFAULT,1);b.rela(2,1026,0,0);return b.build();}
    private byte[] archive(byte[]... members) { ByteArrayOutputStream out=new ByteArrayOutputStream(); out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII)); for(int i=0;i<members.length;i++) writeMember(out,"member"+i+".o",members[i]); return out.toByteArray(); }
    private void writeMember(ByteArrayOutputStream out,String name,byte[] data){String header=pad(name+"/",16)+pad("0",12)+pad("0",6)+pad("0",6)+pad("100644",8)+pad(String.valueOf(data.length),10)+"`\n";out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));out.writeBytes(data);if((data.length&1)==1)out.write('\n');}
    private String pad(String s,int n){StringBuilder x=new StringBuilder(s);while(x.length()<n)x.append(' ');return x.toString();}
}

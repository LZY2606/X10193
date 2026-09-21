package court.engine;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import court.model.Decision;
import court.model.Policy;
import court.model.RefResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionResolutionTest {

    private RefResult ref(Decision d, String unitId, String name, String version) {
        return d.refs.stream()
                .filter(r -> r.key.unitId.equals(unitId) && r.key.symbol.equals(name)
                        && java.util.Objects.equals(r.key.versionName, version))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "缺少引用 " + unitId + "::" + name + "@" + version));
    }

    private byte[] providerSo() {
        return ElfBuilder.so().soname("libx.so.1")
                .verdef(new ElfBuilder.Verdef("LIBX_1.0", 1, true, List.of()))
                .verdef(new ElfBuilder.Verdef("LIBX_2.0", 3, false, List.of("LIBX_1.0")))
                .syms(
                        new Sym("api").defined(0).func().at(0x100).version(3),
                        new Sym("legacy").defined(0).func().at(0x80).version(1),
                        new Sym("secret").defined(0).func().at(0x90).nonDefaultVersion(4)
                )
                .verdef(new ElfBuilder.Verdef("LIBX_PRIVATE", 4, false, List.of()))
                .build();
    }

    @Test
    void unversionedReferenceBindsDefaultVersionOnly() {
        byte[] main = ElfBuilder.so().soname("main")
                .need("libx.so.1")
                .syms(Sym.undef("api").func())
                .build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("x", "libx.so.1", providerSo())
                .run(Policy.RTLD_GLOBAL);
        RefResult r = ref(d, "main", "api", null);
        assertEquals("RESOLVED", r.status);
        assertEquals("LIBX_2.0", r.winner.versionName);
    }

    @Test
    void versionedReferenceRequiresExactVersionAndAliasesMatch() {
        // 引用 api@LIBX_1.0：提供方只有 LIBX_2.0 节点，但它把 LIBX_1.0 作为别名 => 命中同一节点
        byte[] main = ElfBuilder.so().soname("main").need("libx.so.1")
                .verneed(new ElfBuilder.Verneed("libx.so.1", "LIBX_1.0", 2))
                .syms(Sym.undef("api").func().version(2))
                .build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("x", "libx.so.1", providerSo())
                .run(Policy.RTLD_GLOBAL);
        RefResult r = ref(d, "main", "api", "LIBX_1.0");
        assertEquals("RESOLVED", r.status);
        assertEquals("LIBX_2.0", r.winner.versionName);
    }

    @Test
    void unversionedReferenceDoesNotBindNonDefaultVersion() {
        byte[] main = ElfBuilder.so().soname("main").need("libx.so.1")
                .syms(Sym.undef("secret").func())
                .build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("x", "libx.so.1", providerSo())
                .run(Policy.RTLD_GLOBAL);
        RefResult r = ref(d, "main", "secret", null);
        assertEquals("UNRESOLVED", r.status);
        var cand = r.candidates.stream()
                .filter(c -> c.provider.unitName.equals("libx.so.1")).findFirst().orElseThrow();
        assertFalse(cand.eligible);
        assertTrue(cand.rejectReason.contains("非默认版本"));
    }

    @Test
    void requiredVersionMissingIsUnmetEvenIfSymbolExistsUnderOtherVersion() {
        byte[] main = ElfBuilder.so().soname("main").need("libx.so.1")
                .verneed(new ElfBuilder.Verneed("libx.so.1", "LIBX_9.9", 2))
                .syms(Sym.undef("api").func().version(2))
                .build();
        Decision d = EngineTestSupport.create()
                .elf("main", "main", main).elf("x", "libx.so.1", providerSo())
                .run(Policy.RTLD_GLOBAL);
        RefResult r = ref(d, "main", "api", "LIBX_9.9");
        assertEquals("UNRESOLVED", r.status);
        var cand = r.candidates.stream()
                .filter(c -> c.provider.unitName.equals("libx.so.1")).findFirst().orElseThrow();
        assertFalse(cand.eligible);
        assertTrue(cand.rejectReason.contains("版本不符"));
    }
}

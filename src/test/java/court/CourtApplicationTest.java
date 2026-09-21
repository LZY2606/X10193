package court;

import court.domain.Scheme;
import court.elf.*;
import court.link.LinkPolicy;
import court.link.LinkReport;
import court.link.SchemeComparator;
import court.service.DemoFixtures;
import court.service.SchemeService;
import court.service.SchemeService.Upload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CourtApplicationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired SchemeService service;
    @Autowired DemoFixtures demo;
    @Autowired SchemeComparator comparator;

    private List<Upload> ups(DemoFixtures.Fixture... fs) {
        List<Upload> l = new ArrayList<>();
        for (DemoFixtures.Fixture f : fs) l.add(new Upload(f.fileName, f.data));
        return l;
    }

    @Test
    void indexPageShowsTitle() {
        ResponseEntity<String> body = rest.getForEntity("http://127.0.0.1:" + port + "/", String.class);
        assertEquals(200, body.getStatusCode().value());
        assertTrue(body.getBody().contains("符号裁决庭"));
    }

    @Test
    void demoSchemesResolve() {
        Scheme a = service.createVersion("演示A", LinkPolicy.x86_64(),
                upsFrom(demo.orderA()), null);
        LinkReport ra = service.loadReport(a);
        assertNotNull(ra);
        assertTrue(ra.verdicts.size() > 0);
        assertTrue(ra.warnings.stream().anyMatch(w -> w.contains("broken.o")),
                "corrupt demo object must be isolated with a warning");
    }

    @Test
    void oldSchemeNeverDriftsWhenNewVersionChangesOrder() {
        List<Upload> base = upsFrom(demo.orderA());
        Scheme v1 = service.createVersion("漂移演示", LinkPolicy.x86_64(), base, null);
        LinkReport first = service.loadReport(v1);
        String firstJson = v1.reportJson;

        // reverse order into a new version
        List<Upload> reversed = new ArrayList<>(base);
        java.util.Collections.reverse(reversed);
        Scheme v2 = service.createVersion("漂移演示", LinkPolicy.x86_64(), reversed, v1.id);
        assertNotEquals(v1.id, v2.id);
        assertEquals(v1.id, v2.parentId);

        // re-read v1: byte-identical stored report
        Scheme reread = service.get(v1.id);
        assertEquals(firstJson, reread.reportJson, "old scheme result must not drift");
        assertEquals(first.verdicts.size(), service.loadReport(reread).verdicts.size());
    }

    @Test
    void comparatorDistinguishesProviderChangeFromAddressOnly() {
        // Two strong providers for "swap" in two orders; default tie=ERROR -> AMBIGUOUS.
        // Use LOAD_ORDER policy so each order deterministically picks a different winner.
        byte[] pa = ElfBuilder.rel().def("swap", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] pb = ElfBuilder.rel().def("swap", Const.STB_GLOBAL, Const.STT_FUNC, 1).build();
        byte[] user = ElfBuilder.rel().undef("swap", Const.STB_GLOBAL).build();
        LinkPolicy p = LinkPolicy.x86_64();
        p.tiePolicy = "LOAD_ORDER";
        Scheme s1 = service.createVersion("cmp", p,
                ups(new DemoFixtures.Fixture("a.o", pa), new DemoFixtures.Fixture("b.o", pb),
                        new DemoFixtures.Fixture("user.o", user)), null);
        Scheme s2 = service.createVersion("cmp", p,
                ups(new DemoFixtures.Fixture("b.o", pb), new DemoFixtures.Fixture("a.o", pa),
                        new DemoFixtures.Fixture("user.o", user)), s1.id);
        SchemeComparator.Diff d = comparator.compare(service.loadReport(s1), service.loadReport(s2));
        assertTrue(d.providerChanged.stream().anyMatch(c -> c.name.equals("swap")),
                "reversed equal-strong order must show a PROVIDER_CHANGED reference");
        assertTrue(d.addressOnly.isEmpty() || d.providerChanged.size() >= 1);
    }

    @Test
    void apiEndpointsWork() {
        ResponseEntity<Object[]> schemes = rest.getForEntity("/api/schemes", Object[].class);
        assertEquals(200, schemes.getStatusCode().value());
    }

    private List<Upload> upsFrom(List<DemoFixtures.Fixture> fs) {
        List<Upload> l = new ArrayList<>();
        for (DemoFixtures.Fixture f : fs) l.add(new Upload(f.fileName, f.data));
        return l;
    }
}

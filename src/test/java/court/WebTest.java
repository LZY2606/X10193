package court;

import court.fixture.ArBuilder;
import court.fixture.ElfBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static court.fixture.ElfBuilder.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "COURT_DB=:memory:")
@AutoConfigureMockMvc
class WebTest {

    @Autowired MockMvc mvc;

    @Test
    void indexShowsCourtTitle() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("符号裁决庭")));
    }

    @Test
    void uploadAdjudicatesAndPersistsImmutableScheme() throws Exception {
        byte[] def = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("f", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        byte[] ref = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("f", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "f", R_X86_64_64, 0, 0, false).build();
        MockMultipartFile f1 = new MockMultipartFile("files", "def.o", "application/octet-stream", def);
        MockMultipartFile f2 = new MockMultipartFile("files", "ref.o", "application/octet-stream", ref);
        String body = mvc.perform(multipart("/api/schemes").file(f1).file(f2)
                        .param("label", "t1").param("phase", "STATIC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("RESOLVED"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("符号裁决庭") == false);
        mvc.perform(get("/api/schemes")).andExpect(status().isOk());
    }

    @Test
    void archiveAndCorruptInOneRun() throws Exception {
        byte[] member = new ElfBuilder(ET_REL).progbits(".text", 8)
                .symVal("h", STB_GLOBAL, STT_FUNC, STV_DEFAULT, ".text", 0, 4).build();
        byte[] ar = new ArBuilder().add("h.o", member).build();
        byte[] need = new ElfBuilder(ET_REL).progbits(".text", 8)
                .sym("h", STB_GLOBAL, STT_NOTYPE, STV_DEFAULT, SHN_UNDEF)
                .reloc(".text", "h", R_X86_64_64, 0, 0, false).build();
        byte[] bad = "not-an-elf".getBytes();
        mvc.perform(multipart("/api/schemes")
                        .file(new MockMultipartFile("files", "lib.a", null, ar))
                        .file(new MockMultipartFile("files", "need.o", null, need))
                        .file(new MockMultipartFile("files", "bad.o", null, bad))
                        .param("phase", "STATIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ARCHIVE_EXTRACT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bad.o")));
    }
}

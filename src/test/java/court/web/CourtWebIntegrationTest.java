package court.web;

import court.fixture.ElfBuilder;
import court.fixture.Sym;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/test-court.db",
        "server.port=0"
})
@AutoConfigureMockMvc
class CourtWebIntegrationTest {

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void cleanup() throws Exception {
        Files.deleteIfExists(Path.of("build/test-court.db"));
    }

    @AfterAll
    static void done() {
    }

    @Test
    void indexPageShowsCourtTitle() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("符号裁决庭")));
    }

    @Test
    void uploadCreateVersionsAndKeepOldResultsImmutable() throws Exception {
        byte[] main = ElfBuilder.rel().text("m").syms(Sym.undef("fn")).build();
        byte[] a = ElfBuilder.rel().text("a").syms(new Sym("fn").defined(0).at(0x10)).build();
        byte[] b = ElfBuilder.rel().text("b").syms(new Sym("fn").defined(0).at(0x20)).build();

        String mainId = upload("main.o", main);
        String aId = upload("a.o", a);
        String bId = upload("b.o", b);

        // v1: main + a
        String v1 = createScheme("immut", "STATIC_LD", mainId, aId);
        String json1 = mvc.perform(get("/api/schemes/" + v1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json1.contains("\"status\":\"RESOLVED\""));
        org.junit.jupiter.api.Assertions.assertTrue(json1.contains("a.o"));

        // v2: 改变加载顺序 main + b + a（这里 fn 强定义来自 a 与 b，会歧义）
        String v2 = createScheme("immut", "STATIC_LD", mainId, bId, aId);
        // v3: 仅 main+b
        String v3 = createScheme("immut", "STATIC_LD", mainId, bId);

        String json1Again = mvc.perform(get("/api/schemes/" + v1))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(json1, json1Again,
                "旧方案结果必须保持快照，不随后续方案漂移");

        String json3 = mvc.perform(get("/api/schemes/" + v3))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(json3.contains("b.o"));

        // 列表应有 3 个版本
        String list = mvc.perform(get("/api/schemes"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(list.contains("\"versionNo\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(list.contains("\"versionNo\":3"));

        // 比较 v1 vs v3：提供者改变
        String cmp = mvc.perform(get("/api/compare").param("from", v1).param("to", v3))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(cmp.contains("PROVIDER_CHANGED"));
    }

    @Test
    void corruptUploadIsMarkedBrokenAndIsolated() throws Exception {
        MockMultipartFile bad = new MockMultipartFile("files", "junk.o",
                "application/octet-stream", "garbage".getBytes());
        mvc.perform(multipart("/api/uploads").file(bad))
                .andExpect(status().is3xxRedirection());
        String list = mvc.perform(get("/api/uploads"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(list.contains("BROKEN"));
    }

    private String upload(String name, byte[] bytes) throws Exception {
        MockMultipartFile f = new MockMultipartFile("files", name,
                "application/x-object", bytes);
        mvc.perform(multipart("/api/uploads").file(f)).andExpect(status().is3xxRedirection());
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        String list = mvc.perform(get("/api/uploads")).andReturn().getResponse().getContentAsString();
        for (var node : om.readTree(list)) {
            if (name.equals(node.get("name").asText())) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("上传后未找到 " + name);
    }

    private String createScheme(String name, String policy, String... uploadIds) throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = om.createObjectNode();
        body.put("name", name);
        body.put("policy", policy);
        var arr = body.putArray("orderedUploadIds");
        for (String id : uploadIds) arr.add(id);
        String resp = mvc.perform(post("/api/schemes")
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("schemeId").asText();
    }
}

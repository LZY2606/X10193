package com.example.tribunal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:./data/test-tribunal.db")
@AutoConfigureMockMvc
class WebApplicationTest {
    @Autowired MockMvc mvc;

    @Test
    void homePageShowsTribunalTitle() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk());
        String body = mvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("符号裁决庭"));
    }

    @Test
    void healthApiWorks() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                ;
        String health = mvc.perform(get("/api/health")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(health.contains("符号裁决庭"));
    }
}

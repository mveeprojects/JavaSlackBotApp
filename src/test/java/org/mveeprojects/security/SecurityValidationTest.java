package org.mveeprojects.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
class SecurityValidationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testSQLInjectionAttempt() throws Exception {
        String maliciousPayload = """
            {
              "channel": "'; DROP TABLE users; --",
              "threadTs": "1234567890.123456"
            }
            """;

        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(maliciousPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testXSSAttempt() throws Exception {
        String xssPayload = """
            {
              "channel": "<script>alert('xss')</script>",
              "threadTs": "1234567890.123456"
            }
            """;

        // Should be handled safely without executing script
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(xssPayload))
                .andExpect(status().isOk());
    }

    @Test
    void testOversizedPayload() throws Exception {
        String largePayload = "{\"channel\":\"" + "A".repeat(10_000_000) + "\",\"threadTs\":\"1234567890.123456\"}";

        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(largePayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testNullByteInjection() throws Exception {
        String nullBytePayload = """
            {
              "channel": "C1234567890\\u0000malicious",
              "threadTs": "1234567890.123456"
            }
            """;

        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(nullBytePayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testInvalidJsonPayload() throws Exception {
        String invalidJson = "{ invalid json payload";

        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEmptyPayload() throws Exception {
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
                .andExpect(status().isUnsupportedMediaType());
    }
}

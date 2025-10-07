package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownRendererTest {

    private MarkdownRenderer markdownRenderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        markdownRenderer = new MarkdownRenderer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testRenderSimpleJsonToMarkdown() throws Exception {
        String jsonString = """
            {
              "status": "success",
              "message": "Data retrieved successfully"
            }
            """;

        JsonNode jsonNode = objectMapper.readTree(jsonString);
        String markdown = markdownRenderer.renderJsonToMarkdown(jsonNode);

        String expected = """
            **status:** success
            **message:** Data retrieved successfully
            """;

        assertEquals(expected.trim(), markdown.trim());
    }

    @Test
    void testRenderNestedJsonToMarkdown() throws Exception {
        String jsonString = """
            {
              "status": "success",
              "data": {
                "title": "Sample Data",
                "count": 42
              },
              "timestamp": "2025-10-07T10:00:00Z"
            }
            """;

        JsonNode jsonNode = objectMapper.readTree(jsonString);
        String markdown = markdownRenderer.renderJsonToMarkdown(jsonNode);

        String expected = """
            **status:** success
            **data:**
              **title:** Sample Data
              **count:** `42`
            **timestamp:** 2025-10-07T10:00:00Z
            """;

        assertEquals(expected.trim(), markdown.trim());
    }

    @Test
    void testRenderArrayToMarkdown() throws Exception {
        String jsonString = """
            {
              "status": "success",
              "items": [
                {"name": "Item 1", "value": 100},
                {"name": "Item 2", "value": 200}
              ]
            }
            """;

        JsonNode jsonNode = objectMapper.readTree(jsonString);
        String markdown = markdownRenderer.renderJsonToMarkdown(jsonNode);

        String expected = """
            **status:** success
            **items:**
              • **name:** Item 1
                **value:** `100`
              • **name:** Item 2
                **value:** `200`
            """;

        assertEquals(expected.trim(), markdown.trim());
    }

    @Test
    void testRenderComplexJsonToMarkdown() throws Exception {
        String jsonString = """
            {
              "status": "success",
              "data": {
                "title": "Sample Data",
                "items": [
                  {"name": "Item 1", "value": 100},
                  {"name": "Item 2", "value": 200}
                ]
              },
              "timestamp": "2025-10-07T10:00:00Z"
            }
            """;

        JsonNode jsonNode = objectMapper.readTree(jsonString);
        String markdown = markdownRenderer.renderJsonToMarkdown(jsonNode);

        String expected = """
            **status:** success
            **data:**
              **title:** Sample Data
              **items:**
                • **name:** Item 1
                  **value:** `100`
                • **name:** Item 2
                  **value:** `200`
            **timestamp:** 2025-10-07T10:00:00Z
            """;

        assertEquals(expected.trim(), markdown.trim());
    }
}

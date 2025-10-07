package org.mveeprojects.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.service.MarkdownRenderer;

import static org.junit.jupiter.api.Assertions.*;

class EdgeCaseTest {

    private MarkdownRenderer markdownRenderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        markdownRenderer = new MarkdownRenderer();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testEmptyJsonObject() throws Exception {
        JsonNode emptyJson = objectMapper.readTree("{}");
        String markdown = markdownRenderer.renderJsonToMarkdown(emptyJson);
        assertEquals("", markdown.trim());
    }

    @Test
    void testEmptyJsonArray() throws Exception {
        JsonNode emptyArray = objectMapper.readTree("[]");
        String markdown = markdownRenderer.renderJsonToMarkdown(emptyArray);
        assertEquals("", markdown.trim());
    }

    @Test
    void testNullValues() throws Exception {
        JsonNode jsonWithNulls = objectMapper.readTree("""
            {
              "validField": "value",
              "nullField": null,
              "emptyString": ""
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(jsonWithNulls);
        assertTrue(markdown.contains("**validField:** value"));
        assertTrue(markdown.contains("**nullField:** `null`"));
        assertTrue(markdown.contains("**emptyString:**"));
    }

    @Test
    void testSpecialCharactersInJson() throws Exception {
        JsonNode specialCharsJson = objectMapper.readTree("""
            {
              "unicode": "Hello 世界 🌍",
              "symbols": "!@#$%^&*()_+-=[]{}|;':\",./<>?",
              "newlines": "Line 1\\nLine 2\\nLine 3",
              "tabs": "Col1\\tCol2\\tCol3"
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(specialCharsJson);
        assertTrue(markdown.contains("Hello 世界 🌍"));
        assertTrue(markdown.contains("!@#$%^&*()_+-=[]{}|;':\",./<>?"));
        assertTrue(markdown.contains("Line 1\nLine 2\nLine 3"));
        assertTrue(markdown.contains("Col1\tCol2\tCol3"));
    }

    @Test
    void testDeeplyNestedJson() throws Exception {
        JsonNode deeplyNested = objectMapper.readTree("""
            {
              "level1": {
                "level2": {
                  "level3": {
                    "level4": {
                      "level5": {
                        "deepValue": "Found me!"
                      }
                    }
                  }
                }
              }
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(deeplyNested);
        assertTrue(markdown.contains("**deepValue:** Found me!"));

        // Check proper indentation for deep nesting
        String[] lines = markdown.split("\n");
        boolean foundDeepValue = false;
        for (String line : lines) {
            if (line.contains("**deepValue:**")) {
                foundDeepValue = true;
                // Should be indented 10 spaces (5 levels * 2 spaces)
                assertTrue(line.startsWith("          "),
                    "Deep nesting should have proper indentation: '" + line + "'");
            }
        }
        assertTrue(foundDeepValue, "Should find deeply nested value");
    }

    @Test
    void testMixedArrayTypes() throws Exception {
        JsonNode mixedArray = objectMapper.readTree("""
            {
              "mixedItems": [
                "string value",
                42,
                true,
                null,
                {"nestedObject": "value"},
                [1, 2, 3]
              ]
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(mixedArray);
        assertTrue(markdown.contains("• string value"));
        assertTrue(markdown.contains("• `42`"));
        assertTrue(markdown.contains("• `true`"));
        assertTrue(markdown.contains("• `null`"));
        assertTrue(markdown.contains("**nestedObject:** value"));
        assertTrue(markdown.contains("• `1`"));
    }

    @Test
    void testVeryLargeNumbers() throws Exception {
        JsonNode largeNumbers = objectMapper.readTree("""
            {
              "bigInteger": 9223372036854775807,
              "bigDecimal": 1.7976931348623157E+308,
              "smallDecimal": 0.000000000000001,
              "negative": -9999999999999999
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(largeNumbers);
        assertTrue(markdown.contains("`9223372036854775807`"));
        assertTrue(markdown.contains("`1.7976931348623157E308`"));
        assertTrue(markdown.contains("`1.0E-15`"));
        assertTrue(markdown.contains("`-9999999999999999`"));
    }

    @Test
    void testBooleanValues() throws Exception {
        JsonNode booleans = objectMapper.readTree("""
            {
              "isTrue": true,
              "isFalse": false,
              "nested": {
                "enabled": true,
                "disabled": false
              }
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(booleans);
        assertTrue(markdown.contains("**isTrue:** `true`"));
        assertTrue(markdown.contains("**isFalse:** `false`"));
        assertTrue(markdown.contains("**enabled:** `true`"));
        assertTrue(markdown.contains("**disabled:** `false`"));
    }

    @Test
    void testArrayOfObjects() throws Exception {
        JsonNode arrayOfObjects = objectMapper.readTree("""
            {
              "users": [
                {"id": 1, "name": "Alice", "active": true},
                {"id": 2, "name": "Bob", "active": false},
                {"id": 3, "name": "Charlie", "active": true}
              ]
            }
            """);

        String markdown = markdownRenderer.renderJsonToMarkdown(arrayOfObjects);

        // Should have proper bullet structure for each object
        assertTrue(markdown.contains("**users:**"));
        assertTrue(markdown.contains("• **id:** `1`"));
        assertTrue(markdown.contains("**name:** Alice"));
        assertTrue(markdown.contains("**active:** `true`"));
        assertTrue(markdown.contains("• **id:** `2`"));
        assertTrue(markdown.contains("**name:** Bob"));
        assertTrue(markdown.contains("**active:** `false`"));

        // Verify structure - each object should start with bullet
        String[] lines = markdown.split("\n");
        int bulletCount = 0;
        for (String line : lines) {
            if (line.trim().startsWith("• **id:**")) {
                bulletCount++;
            }
        }
        assertEquals(3, bulletCount, "Should have 3 user objects with bullets");
    }

    @Test
    void testCircularReferenceProtection() throws Exception {
        // Test that we handle self-referencing structures gracefully
        // This is more about ensuring we don't get infinite loops
        JsonNode selfRef = objectMapper.readTree("""
            {
              "name": "root",
              "children": [
                {
                  "name": "child1",
                  "parent": "root"
                },
                {
                  "name": "child2", 
                  "parent": "root"
                }
              ]
            }
            """);

        // Should not hang or throw stack overflow
        assertDoesNotThrow(() -> {
            String markdown = markdownRenderer.renderJsonToMarkdown(selfRef);
            assertTrue(markdown.contains("**name:** root"));
            assertTrue(markdown.contains("**children:**"));
        });
    }

    @Test
    void testExtremelyLongStrings() throws Exception {
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longString.append("a");
        }

        JsonNode longStringJson = objectMapper.readTree("""
            {
              "shortField": "short",
              "longField": "%s"
            }
            """.formatted(longString.toString()));

        String markdown = markdownRenderer.renderJsonToMarkdown(longStringJson);
        assertTrue(markdown.contains("**shortField:** short"));
        assertTrue(markdown.contains("**longField:** " + longString.toString()));

        // Should handle long strings without memory issues
        assertTrue(markdown.length() > 10000);
    }
}

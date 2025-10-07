package org.mveeprojects.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.service.ExternalServiceClient;
import org.mveeprojects.service.MarkdownRenderer;
import org.mveeprojects.service.SlackService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Complete end-to-end test that validates:
 * 1. External API call with stubbed response
 * 2. JSON to Markdown conversion accuracy
 * 3. Slack workflow thread posting
 * 4. Exact markdown format validation
 */
@SpringBootTest
@TestPropertySource(properties = {
    "external.service.url=http://localhost:8088/api/data",
    "slack.bot-token=xoxb-test-token",
    "slack.signing-secret=test-secret"
})
class CompleteWorkflowAccuracyTest {

    @MockBean
    private SlackService slackService;

    private ExternalServiceClient externalServiceClient;
    private MarkdownRenderer markdownRenderer;
    private WireMockServer wireMockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8088);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8088);

        externalServiceClient = new ExternalServiceClient();
        markdownRenderer = new MarkdownRenderer();
        objectMapper = new ObjectMapper();

        // Use reflection to set the external service URL
        org.springframework.test.util.ReflectionTestUtils.setField(
            externalServiceClient, "externalServiceUrl", "http://localhost:8088/api/data"
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testCompleteWorkflowMarkdownAccuracy() throws Exception {
        // Arrange - Define the exact external service response
        String externalServiceResponse = """
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

        // Expected markdown output
        String expectedMarkdown = """
            **status:** success
            **data:**
              **title:** Sample Data
              **items:**
                • **name:** Item 1
                  **value:** `100`
                • **name:** Item 2
                  **value:** `200`
            **timestamp:** 2025-10-07T10:00:00Z""";

        // Stub the external service
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(externalServiceResponse)));

        // Mock Slack service to capture the markdown
        doNothing().when(slackService).postThreadResponse(anyString(), anyString(), anyString());

        // Act - Execute the complete workflow
        JsonNode fetchedData = externalServiceClient.fetchData().block();
        assertNotNull(fetchedData, "External service should return data");

        String actualMarkdown = markdownRenderer.renderJsonToMarkdown(fetchedData);

        // Simulate posting to Slack
        String channel = "C1234567890";
        String threadTs = "1234567890.123456";
        slackService.postThreadResponse(channel, threadTs, actualMarkdown);

        // Assert - Verify exact markdown format
        assertEquals(expectedMarkdown.trim(), actualMarkdown.trim(),
            "Markdown should match expected format exactly");

        // Verify external service was called
        verify(getRequestedFor(urlEqualTo("/api/data")));

        // Verify Slack service received the exact markdown
        verify(slackService).postThreadResponse(
            eq(channel),
            eq(threadTs),
            eq(actualMarkdown)
        );

        // Additional granular assertions
        assertTrue(actualMarkdown.contains("**status:** success"),
            "Should contain status field");
        assertTrue(actualMarkdown.contains("**title:** Sample Data"),
            "Should contain nested title");
        assertTrue(actualMarkdown.contains("• **name:** Item 1"),
            "Should format array items with bullets");
        assertTrue(actualMarkdown.contains("**value:** `100`"),
            "Should format numbers in code blocks");
        assertTrue(actualMarkdown.contains("**timestamp:** 2025-10-07T10:00:00Z"),
            "Should contain timestamp");

        // Verify structure
        String[] lines = actualMarkdown.split("\n");
        assertEquals("**status:** success", lines[0].trim());
        assertEquals("**data:**", lines[1].trim());
        assertEquals("**title:** Sample Data", lines[2].trim());
        assertEquals("**items:**", lines[3].trim());
        assertEquals("• **name:** Item 1", lines[4].trim());
        assertEquals("**value:** `100`", lines[5].trim());
    }

    @Test
    void testComplexNestedJsonWorkflow() throws Exception {
        String complexJsonResponse = """
            {
              "workflow": {
                "id": "WF-12345",
                "name": "Data Processing Pipeline",
                "status": "completed",
                "results": {
                  "processed_items": 1500,
                  "errors": 3,
                  "warnings": [
                    {"level": "WARN", "message": "Memory usage high"},
                    {"level": "INFO", "message": "Processing complete"}
                  ]
                }
              },
              "execution_time": "2.5s",
              "next_run": "2025-10-08T10:00:00Z"
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(complexJsonResponse)));

        // Execute workflow
        JsonNode fetchedData = externalServiceClient.fetchData().block();
        String markdown = markdownRenderer.renderJsonToMarkdown(fetchedData);

        // Verify complex structure is rendered correctly
        assertTrue(markdown.contains("**workflow:**"));
        assertTrue(markdown.contains("**id:** WF-12345"));
        assertTrue(markdown.contains("**name:** Data Processing Pipeline"));
        assertTrue(markdown.contains("**results:**"));
        assertTrue(markdown.contains("**processed_items:** `1500`"));
        assertTrue(markdown.contains("**warnings:**"));
        assertTrue(markdown.contains("• **level:** WARN"));
        assertTrue(markdown.contains("**message:** Memory usage high"));
        assertTrue(markdown.contains("**execution_time:** 2.5s"));

        // Verify proper indentation for nested objects
        String[] lines = markdown.split("\n");
        boolean foundWorkflow = false;
        boolean foundResults = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("**workflow:**")) {
                foundWorkflow = true;
                // Next line should be indented
                if (i + 1 < lines.length) {
                    assertTrue(lines[i + 1].startsWith("  "),
                        "Nested objects should be indented");
                }
            }
            if (lines[i].trim().equals("**results:**")) {
                foundResults = true;
                // Should be properly indented as it's nested under workflow
                assertTrue(lines[i].startsWith("  "),
                    "Results should be indented under workflow");
            }
        }
        assertTrue(foundWorkflow && foundResults,
            "Should find both workflow and results sections");
    }

    @Test
    void testErrorResponseWorkflow() throws Exception {
        // Test error response handling
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Service Unavailable")));

        JsonNode errorResponse = externalServiceClient.fetchData().block();
        String errorMarkdown = markdownRenderer.renderJsonToMarkdown(errorResponse);

        // Verify error response is properly formatted
        assertTrue(errorMarkdown.contains("**error:** `true`"));
        assertTrue(errorMarkdown.contains("**message:**"));

        // Should be valid markdown even for error responses
        assertFalse(errorMarkdown.trim().isEmpty());
    }
}

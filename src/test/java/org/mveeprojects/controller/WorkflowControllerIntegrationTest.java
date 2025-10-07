package org.mveeprojects.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.service.SlackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "external.service.url=http://localhost:8089/api/data",
    "slack.bot-token=xoxb-test-token",
    "slack.signing-secret=test-secret"
})
class WorkflowControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockBean
    private SlackService slackService;

    private MockMvc mockMvc;
    private WireMockServer wireMockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);

        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testCompleteWorkflowSuccess() throws Exception {
        // Arrange - Mock external service response
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

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(externalServiceResponse)));

        // Mock Slack service to not actually post to Slack
        doNothing().when(slackService).postThreadResponse(anyString(), anyString(), anyString());

        // Prepare request
        String requestBody = """
            {
              "channel": "C1234567890",
              "threadTs": "1234567890.123456",
              "metadata": {
                "source": "test"
              }
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Response posted to Slack thread"));

        // Verify external service was called
        verify(getRequestedFor(urlEqualTo("/api/data")));

        // Verify Slack service was called with expected markdown
        verify(slackService).postThreadResponse(
                eq("C1234567890"),
                eq("1234567890.123456"),
                argThat(markdown -> {
                    // Validate that the markdown contains expected content
                    return markdown.contains("**status:** success") &&
                           markdown.contains("**title:** Sample Data") &&
                           markdown.contains("• **name:** Item 1") &&
                           markdown.contains("**value:** `100`") &&
                           markdown.contains("• **name:** Item 2") &&
                           markdown.contains("**value:** `200`") &&
                           markdown.contains("**timestamp:** 2025-10-07T10:00:00Z");
                })
        );
    }

    @Test
    void testWorkflowWithMissingChannel() throws Exception {
        String requestBody = """
            {
              "threadTs": "1234567890.123456"
            }
            """;

        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields: channel and threadTs"));

        // Verify no external calls were made
        verify(slackService, never()).postThreadResponse(anyString(), anyString(), anyString());
    }

    @Test
    void testWorkflowWithExternalServiceError() throws Exception {
        // Arrange - Mock external service to return error
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        doNothing().when(slackService).postThreadResponse(anyString(), anyString(), anyString());

        String requestBody = """
            {
              "channel": "C1234567890",
              "threadTs": "1234567890.123456"
            }
            """;

        // Act & Assert - Should still succeed but with error response formatted
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Verify Slack service was called with error markdown
        verify(slackService).postThreadResponse(
                eq("C1234567890"),
                eq("1234567890.123456"),
                argThat(markdown ->
                    markdown.contains("**error:** `true`") &&
                    markdown.contains("**message:**")
                )
        );
    }

    @Test
    void testWorkflowWithSlackServiceError() throws Exception {
        // Arrange - Mock external service success but Slack failure
        String externalServiceResponse = """
            {
              "status": "success",
              "message": "Test data"
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(externalServiceResponse)));

        doThrow(new RuntimeException("Slack API error"))
                .when(slackService).postThreadResponse(anyString(), anyString(), anyString());

        String requestBody = """
            {
              "channel": "C1234567890",
              "threadTs": "1234567890.123456"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/workflow/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error: Slack API error"));
    }
}

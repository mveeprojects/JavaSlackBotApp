package org.mveeprojects.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests that verify the application starts correctly and basic functionality works
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "slack.bot-token=xoxb-test-token",
    "slack.signing-secret=test-secret",
    "external.service.url=http://localhost:8090/api/data"
})
class ApplicationSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Verify Spring context loads successfully
        assertNotNull(applicationContext);

        // Verify key beans are created
        assertTrue(applicationContext.containsBean("slackApp"));
        assertTrue(applicationContext.containsBean("appConfig"));
        assertTrue(applicationContext.containsBean("externalServiceClient"));
        assertTrue(applicationContext.containsBean("markdownRenderer"));
        assertTrue(applicationContext.containsBean("slackService"));
    }

    @Test
    void healthEndpointWorks() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("UP"));
        assertTrue(response.getBody().contains("Slack Bot Application"));
    }

    @Test
    void workflowEndpointExists() {
        // Test that the workflow endpoint exists (even if it fails due to missing external service)
        String requestBody = """
            {
              "channel": "C1234567890",
              "threadTs": "1234567890.123456"
            }
            """;

        ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/workflow/trigger",
            requestBody,
            String.class
        );

        // Should not be 404 (endpoint exists)
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        // Might be 500 due to external service not available, but that's expected
        assertTrue(
            response.getStatusCode() == HttpStatus.OK ||
            response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR ||
            response.getStatusCode() == HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void applicationPropertiesLoaded() {
        // Verify configuration is loaded
        String slackToken = applicationContext.getEnvironment().getProperty("slack.bot-token");
        String signingSecret = applicationContext.getEnvironment().getProperty("slack.signing-secret");
        String externalUrl = applicationContext.getEnvironment().getProperty("external.service.url");

        assertEquals("xoxb-test-token", slackToken);
        assertEquals("test-secret", signingSecret);
        assertEquals("http://localhost:8090/api/data", externalUrl);
    }

    @Test
    void serverStartsOnCorrectPort() {
        assertTrue(port > 0, "Server should start on a valid port");
        assertTrue(port != 8080, "Should use random port in tests, not default 8080");
    }

    @Test
    void applicationRespondsToRequests() {
        // Basic connectivity test
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/health", String.class);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotEquals(0, response.getBody().length());
    }
}

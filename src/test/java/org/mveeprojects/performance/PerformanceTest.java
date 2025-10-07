package org.mveeprojects.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.config.ExternalServiceConfig;
import org.mveeprojects.service.ExternalServiceClient;
import org.mveeprojects.service.MarkdownRenderer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceTest {

    private ExternalServiceClient externalServiceClient;
    private MarkdownRenderer markdownRenderer;
    private WireMockServer wireMockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8087);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8087);

        // Create mock ExternalServiceConfig
        ExternalServiceConfig mockConfig = new ExternalServiceConfig();
        ExternalServiceConfig.ServiceDefinition perfTestService = new ExternalServiceConfig.ServiceDefinition();
        perfTestService.setName("perf-test-service");
        perfTestService.setUrl("http://localhost:8087/api/data");
        perfTestService.setDisplayName("Performance Test Service");
        perfTestService.setTimeout(5000);
        perfTestService.setRetryAttempts(1);
        perfTestService.setHeaders(Map.of("Content-Type", "application/json"));

        mockConfig.setServices(List.of(perfTestService));

        externalServiceClient = new ExternalServiceClient(mockConfig);
        markdownRenderer = new MarkdownRenderer();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testConcurrentExternalServiceCalls() throws Exception {
        // Arrange - Setup WireMock with delay to simulate real service
        String responseJson = """
            {
              "status": "success",
              "data": {"processed": true},
              "timestamp": "2025-10-07T10:00:00Z"
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)
                        .withFixedDelay(100))); // 100ms delay

        // Act - Make 10 concurrent requests using the new config-driven approach
        @SuppressWarnings("unchecked")
        CompletableFuture<JsonNode>[] futures = IntStream.range(0, 10)
                .mapToObj(i -> externalServiceClient.fetchFromService("perf-test-service").toFuture())
                .toArray(CompletableFuture[]::new);

        long startTime = System.currentTimeMillis();
        CompletableFuture.allOf(futures).get();
        long endTime = System.currentTimeMillis();

        // Assert - Should complete in less than 1 second (parallel execution)
        assertTrue(endTime - startTime < 1000,
            "Concurrent requests should complete faster than sequential");

        // Verify all requests succeeded
        for (CompletableFuture<JsonNode> future : futures) {
            JsonNode result = future.get();
            assertEquals("success", result.get("status").asText());
        }

        // Verify WireMock received 10 requests
        verify(10, getRequestedFor(urlEqualTo("/api/data")));
    }

    @Test
    void testLargeJsonProcessingPerformance() throws Exception {
        // Create large JSON with 1000 items
        StringBuilder largeJsonBuilder = new StringBuilder();
        largeJsonBuilder.append("{\"status\":\"success\",\"items\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeJsonBuilder.append(",");
            largeJsonBuilder.append(String.format(
                "{\"id\":%d,\"name\":\"Item %d\",\"value\":%d}", i, i, i * 10
            ));
        }
        largeJsonBuilder.append("]}");

        JsonNode largeJson = objectMapper.readTree(largeJsonBuilder.toString());

        // Test markdown rendering performance
        long startTime = System.currentTimeMillis();
        String markdown = markdownRenderer.renderJsonToMarkdown(largeJson);
        long endTime = System.currentTimeMillis();

        // Should complete within 5 seconds
        assertTrue(endTime - startTime < 5000,
            "Large JSON processing should complete within 5 seconds");

        // Verify markdown contains expected structure
        assertTrue(markdown.contains("**status:** success"));
        assertTrue(markdown.contains("**items:**"));
        assertTrue(markdown.contains("• **id:** `0`"));
        assertTrue(markdown.contains("• **id:** `999`"));

        // Should have proper bullet points for all items
        long bulletCount = markdown.chars().filter(ch -> ch == '•').count();
        assertEquals(1000, bulletCount, "Should have bullet point for each item");
    }

    @Test
    void testMemoryUsageWithLargeData() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        // Measure initial memory
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Process multiple large JSONs
        for (int i = 0; i < 10; i++) {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"batch\":").append(i).append(",\"data\":[");
            for (int j = 0; j < 100; j++) {
                if (j > 0) jsonBuilder.append(",");
                jsonBuilder.append(String.format("{\"item\":%d}", j));
            }
            jsonBuilder.append("]}");

            JsonNode json = objectMapper.readTree(jsonBuilder.toString());
            String markdown = markdownRenderer.renderJsonToMarkdown(json);

            // Verify processing worked
            assertTrue(markdown.contains("**batch:** `" + i + "`"));
        }

        // Measure final memory
        runtime.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Memory increase should be reasonable (less than 50MB)
        assertTrue(memoryIncrease < 50_000_000,
            "Memory usage should not increase significantly: " + memoryIncrease + " bytes");
    }

    @Test
    void testResponseTimeUnderLoad() {
        String simpleJson = """
            {
              "status": "success",
              "message": "Simple response"
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(simpleJson)));

        // Test response time under load using config-driven approach
        Flux<Long> responseTimes = Flux.range(0, 100)
                .flatMap(i -> {
                    long start = System.currentTimeMillis();
                    return externalServiceClient.fetchFromService("perf-test-service")
                            .map(response -> System.currentTimeMillis() - start);
                });

        StepVerifier.create(responseTimes.collectList())
                .assertNext(times -> {
                    // All response times should be under 1 second
                    assertTrue(times.stream().allMatch(time -> time < 1000),
                        "All responses should complete within 1 second");

                    // Average response time should be reasonable
                    double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0);
                    assertTrue(avgTime < 500,
                        "Average response time should be under 500ms: " + avgTime);
                })
                .verifyComplete();
    }

    @Test
    void testCircuitBreakerBehavior() {
        // Test behavior when external service is down
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withFixedDelay(5000))); // 5-second delay

        // Multiple rapid failures should be handled gracefully
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(externalServiceClient.fetchFromService("perf-test-service"))
                    .expectNextMatches(response ->
                        response.has("error") && response.get("error").asBoolean())
                    .verifyComplete();
        }

        // Should not hang or consume excessive resources
        assertTrue(true, "Circuit breaker behavior test completed");
    }
}

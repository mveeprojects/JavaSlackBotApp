package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.config.ExternalServiceConfig;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class ExternalServiceClientTest {

    private ExternalServiceClient externalServiceClient;
    private WireMockServer wireMockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);

        // Create mock ExternalServiceConfig
        ExternalServiceConfig mockConfig = new ExternalServiceConfig();
        ExternalServiceConfig.ServiceDefinition testService = new ExternalServiceConfig.ServiceDefinition();
        testService.setName("test-service");
        testService.setUrl("http://localhost:8089/api/data");
        testService.setDisplayName("Test Service");
        testService.setTimeout(5000);
        testService.setRetryAttempts(1);
        testService.setHeaders(Map.of("Content-Type", "application/json"));

        mockConfig.setServices(List.of(testService));

        // Create ExternalServiceClient with mock config
        externalServiceClient = new ExternalServiceClient(mockConfig);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testFetchFromServiceSuccess() {
        String responseJson = """
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
                        .withBody(responseJson)));

        StepVerifier.create(externalServiceClient.fetchFromService("test-service"))
                .expectNextMatches(jsonNode -> {
                    try {
                        JsonNode expectedNode = objectMapper.readTree(responseJson);
                        return jsonNode.equals(expectedNode);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/api/data")));
    }

    @Test
    void testFetchFromServiceError() {
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        StepVerifier.create(externalServiceClient.fetchFromService("test-service"))
                .expectNextMatches(jsonNode ->
                    jsonNode.has("error") &&
                    jsonNode.get("error").asBoolean() &&
                    jsonNode.has("message")
                )
                .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/api/data")));
    }

    @Test
    void testFetchFromServiceTimeout() {
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000) // 10-second delay to cause timeout
                        .withBody("{}")));

        StepVerifier.create(externalServiceClient.fetchFromService("test-service"))
                .expectNextMatches(jsonNode ->
                    jsonNode.has("error") &&
                    jsonNode.get("error").asBoolean()
                )
                .verifyComplete();
    }

    @Test
    void testFetchFromServiceNotFound() {
        StepVerifier.create(externalServiceClient.fetchFromService("non-existent-service"))
                .expectNextMatches(jsonNode ->
                    jsonNode.has("error") &&
                    jsonNode.get("error").asBoolean() &&
                    jsonNode.get("message").asText().contains("Service not found")
                )
                .verifyComplete();
    }

    @Test
    void testFetchAllServices() {
        String responseJson = """
            {
              "status": "success",
              "service": "test-service"
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseJson)));

        var allServices = externalServiceClient.fetchAllServices();

        // Verify that we get a map with our test service
        assert allServices.containsKey("test-service");

        StepVerifier.create(allServices.get("test-service"))
                .expectNextMatches(jsonNode -> jsonNode.has("status"))
                .verifyComplete();
    }

    @Test
    void testGetConfiguredServices() {
        var services = externalServiceClient.getConfiguredServices();

        assert services.size() == 1;
        assert services.getFirst().getName().equals("test-service");
        assert services.getFirst().getDisplayName().equals("Test Service");
        assert services.getFirst().getUrl().equals("http://localhost:8089/api/data");
    }
}

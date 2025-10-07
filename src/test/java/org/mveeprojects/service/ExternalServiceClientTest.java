package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class ExternalServiceClientTest {

    private ExternalServiceClient externalServiceClient;
    private WireMockServer wireMockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);

        externalServiceClient = new ExternalServiceClient();
        ReflectionTestUtils.setField(externalServiceClient, "externalServiceUrl", "http://localhost:8089/api/data");

        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testFetchDataSuccess() throws Exception {
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

        StepVerifier.create(externalServiceClient.fetchData())
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
    void testFetchDataError() {
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        StepVerifier.create(externalServiceClient.fetchData())
                .expectNextMatches(jsonNode ->
                    jsonNode.has("error") &&
                    jsonNode.get("error").asBoolean() &&
                    jsonNode.has("message")
                )
                .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/api/data")));
    }

    @Test
    void testFetchDataTimeout() {
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(30000) // 30 second delay to cause timeout
                        .withBody("{}")));

        StepVerifier.create(externalServiceClient.fetchData())
                .expectNextMatches(jsonNode ->
                    jsonNode.has("error") &&
                    jsonNode.get("error").asBoolean()
                )
                .verifyComplete();
    }
}

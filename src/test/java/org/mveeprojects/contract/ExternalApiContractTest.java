package org.mveeprojects.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mveeprojects.service.ExternalServiceClient;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests to validate external API compatibility
 * These tests ensure the application works with different API response formats
 */
class ExternalApiContractTest {

    private ExternalServiceClient externalServiceClient;
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8086);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8086);

        externalServiceClient = new ExternalServiceClient();
        ReflectionTestUtils.setField(
            externalServiceClient, "externalServiceUrl", "http://localhost:8086/api/data"
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testMinimalValidResponse() {
        // Test the absolute minimum valid JSON response
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.isObject());
    }

    @Test
    void testRestApiStandardResponse() {
        // Test standard REST API response format
        String standardResponse = """
            {
              "success": true,
              "data": {
                "id": "12345",
                "type": "user",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
              },
              "meta": {
                "timestamp": "2025-10-07T10:00:00Z",
                "version": "1.0"
              }
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(standardResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.get("success").asBoolean());
        assertTrue(response.has("data"));
        assertTrue(response.has("meta"));
    }

    @Test
    void testJsonApiFormat() {
        // Test JSON:API specification format
        String jsonApiResponse = """
            {
              "data": {
                "type": "articles",
                "id": "1",
                "attributes": {
                  "title": "JSON:API paints my bikeshed!"
                },
                "relationships": {
                  "author": {
                    "data": { "type": "people", "id": "9" }
                  }
                }
              },
              "included": [{
                "type": "people",
                "id": "9",
                "attributes": {
                  "first-name": "Dan",
                  "last-name": "Gebhardt"
                }
              }]
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.api+json")
                        .withBody(jsonApiResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("data"));
        assertTrue(response.has("included"));
    }

    @Test
    void testHalJsonFormat() {
        // Test HAL (Hypertext Application Language) format
        String halResponse = """
            {
              "_links": {
                "self": { "href": "/orders/523" },
                "warehouse": { "href": "/warehouse/56" },
                "invoice": { "href": "/invoices/873" }
              },
              "currency": "USD",
              "status": "shipped",
              "total": 10.20
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/hal+json")
                        .withBody(halResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("_links"));
        assertTrue(response.has("status"));
    }

    @Test
    void testGraphQlResponse() {
        // Test GraphQL response format
        String graphqlResponse = """
            {
              "data": {
                "user": {
                  "id": "1",
                  "name": "Luke Skywalker",
                  "friends": [
                    {
                      "id": "2",
                      "name": "Han Solo"
                    },
                    {
                      "id": "3",
                      "name": "Leia Organa"
                    }
                  ]
                }
              },
              "extensions": {
                "tracing": {
                  "version": 1,
                  "startTime": "2025-10-07T10:00:00.000Z"
                }
              }
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(graphqlResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("data"));
        assertTrue(response.get("data").has("user"));
    }

    @Test
    void testPaginatedResponse() {
        // Test paginated API response
        String paginatedResponse = """
            {
              "data": [
                {"id": 1, "name": "Item 1"},
                {"id": 2, "name": "Item 2"},
                {"id": 3, "name": "Item 3"}
              ],
              "pagination": {
                "currentPage": 1,
                "totalPages": 10,
                "totalItems": 100,
                "itemsPerPage": 10
              },
              "links": {
                "first": "/api/data?page=1",
                "next": "/api/data?page=2",
                "last": "/api/data?page=10"
              }
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(paginatedResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("data"));
        assertTrue(response.has("pagination"));
        assertTrue(response.has("links"));
    }

    @Test
    void testErrorResponseFormats() {
        // Test RFC 7807 Problem Details format
        String problemDetailsResponse = """
            {
              "type": "https://example.com/probs/out-of-credit",
              "title": "You do not have enough credit.",
              "detail": "Your current balance is 30, but that costs 50.",
              "instance": "/account/12345/msgs/abc",
              "balance": 30,
              "accounts": ["/account/12345", "/account/67890"]
            }
            """;

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(problemDetailsResponse)));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertTrue(response.get("error").asBoolean());
    }

    @Test
    void testContentTypeVariations() {
        String jsonResponse = "{\"status\": \"success\", \"message\": \"OK\"}";

        // Test various content type headers
        String[] contentTypes = {
            "application/json",
            "application/json; charset=utf-8",
            "application/json;charset=UTF-8",
            "text/json",
            "application/vnd.api+json"
        };

        for (String contentType : contentTypes) {
            stubFor(get(urlEqualTo("/api/data"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", contentType)
                            .withBody(jsonResponse)));

            JsonNode response = externalServiceClient.fetchData().block();
            assertNotNull(response);
            assertEquals("success", response.get("status").asText());

            // Reset for next iteration
            wireMockServer.resetAll();
        }
    }

    @Test
    void testResponseSizeVariations() {
        // Test tiny response
        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        JsonNode response = externalServiceClient.fetchData().block();
        assertNotNull(response);
        assertTrue(response.has("ok"));

        wireMockServer.resetAll();

        // Test medium response (1KB)
        StringBuilder mediumResponse = new StringBuilder("{\"data\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) mediumResponse.append(",");
            mediumResponse.append("{\"id\":").append(i).append("}");
        }
        mediumResponse.append("]}");

        stubFor(get(urlEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mediumResponse.toString())));

        JsonNode response2 = externalServiceClient.fetchData().block();
        assertNotNull(response2);
        assertTrue(response2.has("data"));
        assertTrue(response2.get("data").isArray());
    }
}

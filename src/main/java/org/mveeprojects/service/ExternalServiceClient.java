package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.mveeprojects.config.ExternalServiceConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExternalServiceClient {

    private final ExternalServiceConfig externalServiceConfig;
    private final WebClient webClient;

    public ExternalServiceClient(ExternalServiceConfig externalServiceConfig) {
        this.externalServiceConfig = externalServiceConfig;
        this.webClient = WebClient.builder().build();
    }

    /**
     * Fetch data from all configured external services
     */
    public Map<String, Mono<JsonNode>> fetchAllServices() {
        return externalServiceConfig.getServices().stream()
                .collect(Collectors.toMap(
                    ExternalServiceConfig.ServiceDefinition::getName,
                    this::fetchFromService
                ));
    }

    /**
     * Fetch data from a specific service by name
     */
    public Mono<JsonNode> fetchFromService(String serviceName) {
        return externalServiceConfig.getServices().stream()
                .filter(service -> service.getName().equals(serviceName))
                .findFirst()
                .map(this::fetchFromService)
                .orElse(Mono.just(createErrorResponse("Service not found: " + serviceName)));
    }

    /**
     * Fetch data from a specific service configuration
     */
    public Mono<JsonNode> fetchFromService(ExternalServiceConfig.ServiceDefinition service) {
        WebClient.RequestHeadersSpec<?> request = webClient
                .get()
                .uri(service.getUrl());

        // Add headers if configured
        if (service.getHeaders() != null) {
            for (Map.Entry<String, String> header : service.getHeaders().entrySet()) {
                request = request.header(header.getKey(), header.getValue());
            }
        }

        return request
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(service.getTimeout()))
                .retryWhen(Retry.fixedDelay(service.getRetryAttempts(), Duration.ofSeconds(1)))
                .onErrorResume(throwable -> {
                    System.err.println("Error fetching from " + service.getName() + ": " + throwable.getMessage());
                    return Mono.just(createErrorResponse("Failed to fetch from " + service.getDisplayName() + ": " + throwable.getMessage()));
                });
    }

    /**
     * Get all configured services
     */
    public List<ExternalServiceConfig.ServiceDefinition> getConfiguredServices() {
        return externalServiceConfig.getServices();
    }

    // Legacy methods for backward compatibility
    @Deprecated
    public Mono<JsonNode> fetchData() {
        return fetchFromService("primary-api");
    }

    @Deprecated
    public Mono<JsonNode> fetchPrimaryData() {
        return fetchFromService("primary-api");
    }

    @Deprecated
    public Mono<JsonNode> fetchSecondaryData() {
        return fetchFromService("secondary-api");
    }

    private JsonNode createErrorResponse(String errorMessage) {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                .objectNode()
                .put("error", true)
                .put("message", errorMessage);
    }
}

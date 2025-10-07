package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.mveeprojects.config.ExternalServiceConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Unified service that handles the complete workflow:
 * 1. Fetch data from all configured external services
 * 2. Convert each response to markdown
 * 3. Post each response as separate threaded replies to Slack
 */
@Service
public class SlackWorkflowService {

    private final ExternalServiceClient externalServiceClient;
    private final MarkdownRenderer markdownRenderer;
    private final SlackService slackService;

    public SlackWorkflowService(ExternalServiceClient externalServiceClient,
                               MarkdownRenderer markdownRenderer,
                               SlackService slackService) {
        this.externalServiceClient = externalServiceClient;
        this.markdownRenderer = markdownRenderer;
        this.slackService = slackService;
    }

    /**
     * Execute the complete workflow: fetch data from all configured services, convert to markdown, and post to Slack
     */
    public void executeWorkflow(String channel, String threadTs) {
        // Fetch data from all configured external services
        Map<String, Mono<JsonNode>> serviceResponses = externalServiceClient.fetchAllServices();

        // Process each service response
        for (ExternalServiceConfig.ServiceDefinition service : externalServiceClient.getConfiguredServices()) {
            String serviceName = service.getName();
            String displayName = service.getDisplayName() != null ? service.getDisplayName() : serviceName;

            Mono<JsonNode> responseMono = serviceResponses.get(serviceName);
            if (responseMono != null) {
                JsonNode response = responseMono.block();
                if (response != null) {
                    String markdownContent = String.format("**%s Response:**\n\n%s",
                                                         displayName,
                                                         markdownRenderer.renderJsonToMarkdown(response));
                    slackService.postThreadResponse(channel, threadTs, markdownContent);
                }
            }
        }
    }

    /**
     * Execute workflow for specific services by name
     */
    public void executeWorkflowForServices(String channel, String threadTs, String... serviceNames) {
        for (String serviceName : serviceNames) {
            Mono<JsonNode> responseMono = externalServiceClient.fetchFromService(serviceName);
            JsonNode response = responseMono.block();

            if (response != null) {
                // Find the service config to get display name
                String displayName = externalServiceClient.getConfiguredServices().stream()
                    .filter(service -> service.getName().equals(serviceName))
                    .map(service -> service.getDisplayName() != null ? service.getDisplayName() : service.getName())
                    .findFirst()
                    .orElse(serviceName);

                String markdownContent = String.format("**%s Response:**\n\n%s",
                                                     displayName,
                                                     markdownRenderer.renderJsonToMarkdown(response));
                slackService.postThreadResponse(channel, threadTs, markdownContent);
            }
        }
    }

    /**
     * Legacy method for backward compatibility - now calls both services
     */
    @Deprecated
    public void executeSingleWorkflow(String channel, String threadTs) {
        JsonNode jsonResponse = externalServiceClient.fetchData().block();
        if (jsonResponse != null) {
            String markdownContent = markdownRenderer.renderJsonToMarkdown(jsonResponse);
            slackService.postThreadResponse(channel, threadTs, markdownContent);
        }
    }
}

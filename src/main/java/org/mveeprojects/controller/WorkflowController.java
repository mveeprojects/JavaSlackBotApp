package org.mveeprojects.controller;

import org.mveeprojects.config.ExternalServiceConfig;
import org.mveeprojects.service.ExternalServiceClient;
import org.mveeprojects.service.SlackWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final SlackWorkflowService slackWorkflowService;
    private final ExternalServiceClient externalServiceClient;

    public WorkflowController(SlackWorkflowService slackWorkflowService,
                             ExternalServiceClient externalServiceClient) {
        this.slackWorkflowService = slackWorkflowService;
        this.externalServiceClient = externalServiceClient;
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> handleWorkflowTrigger(
            @RequestBody WorkflowRequest request) {

        try {
            // Validate request
            if (request.channel() == null || request.threadTs() == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields: channel and threadTs"));
            }

            // Execute the complete workflow
            slackWorkflowService.executeWorkflow(request.channel(), request.threadTs());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Response posted to Slack thread"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Execute workflow for all configured external services
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeWorkflow(@RequestBody WorkflowRequest request) {
        try {
            if (request.channel() == null || request.threadTs() == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields: channel and threadTs"));
            }

            slackWorkflowService.executeWorkflow(request.channel(), request.threadTs());

            return ResponseEntity.ok(Map.of(
                "message", "Workflow executed successfully",
                "servicesProcessed", externalServiceClient.getConfiguredServices().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Execute workflow for specific services only
     */
    @PostMapping("/execute/services")
    public ResponseEntity<Map<String, Object>> executeWorkflowForServices(
            @RequestBody WorkflowServiceRequest request) {
        try {
            if (request.channel() == null || request.threadTs() == null ||
                request.serviceNames() == null || request.serviceNames().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields: channel, threadTs, and serviceNames"));
            }

            slackWorkflowService.executeWorkflowForServices(
                request.channel(),
                request.threadTs(),
                request.serviceNames().toArray(new String[0])
            );

            return ResponseEntity.ok(Map.of(
                "message", "Workflow executed successfully for specified services",
                "servicesProcessed", request.serviceNames().size(),
                "services", request.serviceNames()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Get list of all configured external services
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getConfiguredServices() {
        try {
            List<Map<String, Object>> services = externalServiceClient.getConfiguredServices().stream()
                .map(service -> Map.of(
                    "name", service.getName(),
                    "displayName", service.getDisplayName() != null ? service.getDisplayName() : service.getName(),
                    "url", service.getUrl(),
                    "timeout", service.getTimeout(),
                    "retryAttempts", service.getRetryAttempts()
                ))
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "services", services,
                "totalServices", services.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    public record WorkflowRequest(String channel, String threadTs) {}

    public record WorkflowServiceRequest(String channel, String threadTs, List<String> serviceNames) {}
}

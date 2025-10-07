package org.mveeprojects.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mveeprojects.service.SlackService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/slack")
public class SlackEventController {

    private final SlackService slackService;
    private final ObjectMapper objectMapper;

    public SlackEventController(SlackService slackService) {
        this.slackService = slackService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/events")
    public ResponseEntity<String> handleSlackEvents(@RequestBody String requestBody) {
        try {
            JsonNode event = objectMapper.readTree(requestBody);

            // Handle URL verification (required for Slack app setup)
            if ("url_verification".equals(event.path("type").asText())) {
                String challenge = event.path("challenge").asText();
                return ResponseEntity.ok(challenge);
            }

            // Handle app mention events
            if ("event_callback".equals(event.path("type").asText())) {
                JsonNode eventData = event.path("event");
                String eventType = eventData.path("type").asText();

                if ("app_mention".equals(eventType)) {
                    String channel = eventData.path("channel").asText();
                    String ts = eventData.path("ts").asText();

                    // Respond to app mentions with helpful information
                    String responseMessage = """
                            👋 Hello! I'm your workflow bot.
                            
                            **Available endpoints:**
                            • `POST /api/workflow/execute` - Run all configured services
                            • `GET /api/workflow/services` - List available services
                            • `POST /api/workflow/execute/services` - Run specific services""";

                    slackService.postThreadResponse(channel, ts, responseMessage);
                }
            }

            // Acknowledge all events
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            // Log error and return OK to avoid retries from Slack
            System.err.println("Error processing Slack event: " + e.getMessage());
            return ResponseEntity.ok("OK");
        }
    }

    @PostMapping("/commands")
    public ResponseEntity<Map<String, String>> handleSlashCommands(@RequestParam String command) {
        try {
            if ("/ping".equals(command)) {
                return ResponseEntity.ok(Map.of(
                    "response_type", "in_channel",
                    "text", "🏓 Pong! The workflow bot is running and ready to process external APIs."
                ));
            }

            if ("/workflow".equals(command)) {
                return ResponseEntity.ok(Map.of(
                    "response_type", "ephemeral",
                    "text", """
                            Use the REST API endpoints to trigger workflows:
                            • `POST /api/workflow/execute` - Execute all services
                            • `GET /api/workflow/services` - List services"""
                ));
            }

            return ResponseEntity.ok(Map.of(
                "response_type", "ephemeral",
                "text", "Unknown command. Available commands: `/ping`, `/workflow`"
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "response_type", "ephemeral",
                "text", "Sorry, there was an error processing your command."
            ));
        }
    }
}

package org.mveeprojects.controller;

import com.slack.api.bolt.App;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/slack")
public class SlackEventController {

    private final App slackApp;

    public SlackEventController(App slackApp) {
        this.slackApp = slackApp;
    }

    @PostConstruct
    public void init() {
        // Simple ping command
        slackApp.command("/ping", (req, ctx) -> ctx.ack("Pong! The bot is working."));

        // Simple app mention handler
        slackApp.event(com.slack.api.model.event.AppMentionEvent.class, (req, ctx) -> {
            ctx.say("Hello! Use POST /api/workflow/trigger to trigger workflows.");
            return ctx.ack();
        });
    }

    @PostMapping("/events/**")
    public ResponseEntity<String> handleSlackEvents(@RequestBody String requestBody) {
        // Handle URL verification (required for Slack app setup)
        if (requestBody.contains("\"type\":\"url_verification\"")) {
            int start = requestBody.indexOf("\"challenge\":\"") + 13;
            int end = requestBody.indexOf("\"", start);
            return ResponseEntity.ok(requestBody.substring(start, end));
        }

        // Acknowledge other events
        return ResponseEntity.ok("OK");
    }
}

package org.mveeprojects.service;

import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import org.springframework.stereotype.Service;
import com.slack.api.methods.MethodsClient;

import java.io.IOException;
import java.util.List;

/**
 * Service for posting messages to Slack threads
 */
@Service
public class SlackService {

    private final MethodsClient methodsClient;

    public SlackService(MethodsClient methodsClient) {
        this.methodsClient = methodsClient;
    }

    public void postThreadResponse(String channel, String threadTs, String markdownContent) {
        try {
            // Create markdown blocks for better formatting
            List<LayoutBlock> blocks = List.of(
                SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                        .text(markdownContent)
                        .build())
                    .build()
            );

            ChatPostMessageResponse response = methodsClient.chatPostMessage(req -> req
                .channel(channel)
                .threadTs(threadTs)
                .blocks(blocks)
            );

            if (!response.isOk()) {
                throw new RuntimeException("Failed to post message to Slack: " + response.getError());
            }

        } catch (IOException | SlackApiException e) {
            throw new RuntimeException("Error posting to Slack thread", e);
        }
    }
}

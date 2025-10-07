package org.mveeprojects.service;

import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class SlackService {

    @Value("${slack.bot-token}")
    private String botToken;

    private final Slack slack;

    public SlackService() {
        this.slack = Slack.getInstance();
    }

    public void postThreadResponse(String channel, String threadTs, String markdownContent) {
        try {
            MethodsClient methods = slack.methods(botToken);

            // Create markdown block
            List<LayoutBlock> blocks = Arrays.asList(
                SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                        .text(markdownContent)
                        .build())
                    .build()
            );

            ChatPostMessageResponse response = methods.chatPostMessage(req -> req
                .channel(channel)
                .threadTs(threadTs)
                .blocks(blocks)
                .text("API Response") // Fallback text
            );

            if (!response.isOk()) {
                throw new RuntimeException("Failed to post message to Slack: " + response.getError());
            }

        } catch (IOException | SlackApiException e) {
            throw new RuntimeException("Error posting to Slack thread", e);
        }
    }
}

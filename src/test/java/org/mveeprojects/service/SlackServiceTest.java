package org.mveeprojects.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackServiceTest {

    private SlackService slackService;

    @Mock
    private Slack mockSlack;

    @Mock
    private MethodsClient mockMethodsClient;

    @Mock
    private ChatPostMessageResponse mockResponse;

    @BeforeEach
    void setUp() {
        slackService = new SlackService();
        ReflectionTestUtils.setField(slackService, "botToken", "xoxb-test-token");
    }

    @Test
    void testPostThreadResponseSuccess() throws Exception {
        // Arrange
        String channel = "C1234567890";
        String threadTs = "1234567890.123456";
        String markdownContent = "**status:** success\n**message:** Test message";

        when(mockResponse.isOk()).thenReturn(true);
        when(mockMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(mockResponse);

        try (MockedStatic<Slack> slackStatic = mockStatic(Slack.class)) {
            //noinspection ResultOfMethodCallIgnored
            slackStatic.when(Slack::getInstance).thenReturn(mockSlack);
            when(mockSlack.methods("xoxb-test-token")).thenReturn(mockMethodsClient);

            // Act
            assertDoesNotThrow(() ->
                slackService.postThreadResponse(channel, threadTs, markdownContent)
            );

            // Assert
            verify(mockMethodsClient).chatPostMessage(any(ChatPostMessageRequest.class));
            verify(mockResponse).isOk();
        }
    }

    @Test
    void testPostThreadResponseFailure() throws Exception {
        // Arrange
        String channel = "C1234567890";
        String threadTs = "1234567890.123456";
        String markdownContent = "**status:** error";

        when(mockResponse.isOk()).thenReturn(false);
        when(mockResponse.getError()).thenReturn("channel_not_found");
        when(mockMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(mockResponse);

        try (MockedStatic<Slack> slackStatic = mockStatic(Slack.class)) {
            //noinspection ResultOfMethodCallIgnored
            slackStatic.when(Slack::getInstance).thenReturn(mockSlack);
            when(mockSlack.methods("xoxb-test-token")).thenReturn(mockMethodsClient);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                slackService.postThreadResponse(channel, threadTs, markdownContent)
            );

            assertTrue(exception.getMessage().contains("Failed to post message to Slack"));
            assertTrue(exception.getMessage().contains("channel_not_found"));
        }
    }

    @Test
    void testPostThreadResponseSlackApiException() throws Exception {
        // Arrange
        String channel = "C1234567890";
        String threadTs = "1234567890.123456";
        String markdownContent = "**status:** error";

        when(mockMethodsClient.chatPostMessage(any(ChatPostMessageRequest.class)))
            .thenThrow(new RuntimeException("Network error"));

        try (MockedStatic<Slack> slackStatic = mockStatic(Slack.class)) {
            //noinspection ResultOfMethodCallIgnored
            slackStatic.when(Slack::getInstance).thenReturn(mockSlack);
            when(mockSlack.methods("xoxb-test-token")).thenReturn(mockMethodsClient);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                slackService.postThreadResponse(channel, threadTs, markdownContent)
            );

            assertTrue(exception.getMessage().contains("Error posting to Slack thread"));
        }
    }
}

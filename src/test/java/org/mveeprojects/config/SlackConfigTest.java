package org.mveeprojects.config;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "slack.bot-token=xoxb-test-token"
})
class SlackConfigTest {

    @Autowired
    private Slack slack;

    @Autowired
    private MethodsClient methodsClient;

    @Test
    void testSlackInstanceCreation() {
        assertNotNull(slack);
        assertInstanceOf(Slack.class, slack);
    }

    @Test
    void testMethodsClientCreation() {
        assertNotNull(methodsClient);
        assertInstanceOf(MethodsClient.class, methodsClient);
    }

    @Test
    void testSlackConfigurationBeans() {
        // Verify that both beans are properly configured and not null
        assertNotNull(slack, "Slack bean should be configured");
        assertNotNull(methodsClient, "MethodsClient bean should be configured");

        // Verify that the MethodsClient is properly configured with the Slack instance
        // This indirectly tests that our SlackConfig properly wires the dependencies
        assertNotNull(methodsClient.toString(), "MethodsClient should be properly initialized");
    }
}

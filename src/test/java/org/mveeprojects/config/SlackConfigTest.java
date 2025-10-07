package org.mveeprojects.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "slack.bot-token=xoxb-test-token",
    "slack.signing-secret=test-secret"
})
class SlackConfigTest {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private App slackApp;

    @Test
    void testAppConfigCreation() {
        assertNotNull(appConfig);
        assertEquals("xoxb-test-token", appConfig.getSingleTeamBotToken());
        assertEquals("test-secret", appConfig.getSigningSecret());
    }

    @Test
    void testSlackAppCreation() {
        assertNotNull(slackApp);
        assertNotNull(slackApp.config());
    }

    @Test
    void testSlackAppConfiguration() {
        assertEquals(appConfig, slackApp.config());
    }
}

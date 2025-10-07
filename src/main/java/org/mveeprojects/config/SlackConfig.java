package org.mveeprojects.config;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {

    @Value("${slack.bot-token}")
    private String botToken;

    @Bean
    public Slack slack() {
        return Slack.getInstance();
    }

    @Bean
    public MethodsClient slackMethodsClient(Slack slack) {
        return slack.methods(botToken);
    }
}

package com.acme.moviebot.telegram.internal.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TelegramClientConfiguration {

    @Bean
    RestClient telegramRestClient(RestClient.Builder builder, TelegramProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.polling().timeoutSeconds() + 10L));
        return builder.requestFactory(requestFactory).build();
    }
}

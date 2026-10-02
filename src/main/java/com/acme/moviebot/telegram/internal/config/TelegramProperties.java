package com.acme.moviebot.telegram.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.telegram")
public record TelegramProperties(String botToken, String apiBaseUrl, Polling polling) {

    public TelegramProperties {
        botToken = botToken == null ? "" : botToken.trim();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.replaceAll("/$", "");
        polling = polling == null ? new Polling(true, 30, 30) : polling;
    }

    public record Polling(boolean enabled, int timeoutSeconds, int retryMaxSeconds) {
        public Polling {
            if (timeoutSeconds < 1 || timeoutSeconds > 50) timeoutSeconds = 30;
            if (retryMaxSeconds < 1) retryMaxSeconds = 30;
        }
    }
}

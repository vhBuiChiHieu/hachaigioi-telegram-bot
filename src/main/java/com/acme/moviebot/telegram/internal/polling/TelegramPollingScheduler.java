package com.acme.moviebot.telegram.internal.polling;

import com.acme.moviebot.telegram.internal.client.TelegramBotClient;
import com.acme.moviebot.telegram.internal.client.TelegramUpdateDto;
import com.acme.moviebot.telegram.internal.config.TelegramProperties;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelegramPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingScheduler.class);
    private final TelegramBotClient client;
    private final TelegramUpdateProcessor processor;
    private final TelegramProperties properties;
    private long nextOffset;
    private int consecutiveFailures;
    private Instant nextPollAt = Instant.EPOCH;
    private boolean missingTokenReported;

    public TelegramPollingScheduler(TelegramBotClient client, TelegramUpdateProcessor processor, TelegramProperties properties) {
        this.client = client;
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 500)
    public synchronized void poll() {
        if (!properties.polling().enabled()) return;
        if (properties.botToken().isBlank()) {
            if (!missingTokenReported) {
                log.info("telegram.polling.disabled reason=missing_bot_token");
                missingTokenReported = true;
            }
            return;
        }
        if (Instant.now().isBefore(nextPollAt)) return;
        try {
            List<TelegramUpdateDto> updates = client.getUpdates(nextOffset, properties.polling().timeoutSeconds());
            for (TelegramUpdateDto update : updates) {
                processor.process(update);
                nextOffset = Math.max(nextOffset, update.updateId() + 1);
            }
            consecutiveFailures = 0;
            nextPollAt = Instant.EPOCH;
        } catch (RuntimeException exception) {
            consecutiveFailures++;
            long delaySeconds = Math.min(1L << Math.min(consecutiveFailures - 1, 10), properties.polling().retryMaxSeconds());
            nextPollAt = Instant.now().plusSeconds(delaySeconds);
            log.warn("telegram.polling.failed failures={} retryInSeconds={} error={}", consecutiveFailures, delaySeconds,
                    exception.getMessage());
        }
    }
}

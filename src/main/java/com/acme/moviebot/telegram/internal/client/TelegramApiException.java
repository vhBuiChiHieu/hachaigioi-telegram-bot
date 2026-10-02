package com.acme.moviebot.telegram.internal.client;

public class TelegramApiException extends RuntimeException {

    private final int statusCode;
    private final Integer retryAfterSeconds;

    public TelegramApiException(String message, int statusCode, Integer retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public TelegramApiException(String message) {
        this(message, 0, null, null);
    }

    public int statusCode() { return statusCode; }
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
    public boolean isTransientFailure() { return statusCode == 429 || statusCode >= 500 || statusCode == 0; }
}

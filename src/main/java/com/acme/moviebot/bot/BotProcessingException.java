package com.acme.moviebot.bot;

public class BotProcessingException extends RuntimeException {

    public BotProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

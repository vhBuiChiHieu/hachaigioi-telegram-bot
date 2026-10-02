package com.acme.moviebot.catalog;

public class CatalogConflictException extends RuntimeException {

    public CatalogConflictException(String message) {
        super(message);
    }
}

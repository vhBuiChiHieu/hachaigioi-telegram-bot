package com.acme.moviebot.catalog.internal.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchNormalizer {

    private SearchNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public static String slug(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}

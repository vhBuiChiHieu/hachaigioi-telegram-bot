package com.acme.moviebot.catalog.internal.domain;

import java.net.URI;
import java.net.URISyntaxException;

public record ExternalLink(String url) {

    public ExternalLink {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Link không được để trống.");
        }
        url = url.trim();
        if (url.length() > 2048) {
            throw new IllegalArgumentException("Link không được dài quá 2048 ký tự.");
        }
        try {
            URI uri = new URI(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Hãy gửi một link http:// hoặc https:// hợp lệ.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Hãy gửi một link http:// hoặc https:// hợp lệ.");
        }
    }
}

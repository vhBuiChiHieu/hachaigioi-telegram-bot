package com.acme.moviebot.bot.internal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CallbackDataCodec {

    public String movie(long id) { return "m:" + id; }
    public String season(long id) { return "s:" + id; }
    public String episode(long id) { return "e:" + id; }
    public String seasonsPage(long movieId, int page) { return "sp:" + movieId + ":" + page; }
    public String episodesPage(long seasonId, int page) { return "ep:" + seasonId + ":" + page; }
    public String movieSearchPage(String keyword, int page) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyword.getBytes(StandardCharsets.UTF_8));
        String value = "mp:" + page + ":" + encoded;
        return value.length() <= 64 ? value : "";
    }
    public String admin(String action, long id) { return "a:" + action + ":" + id; }
    public String admin(String action) { return "a:" + action; }

    public DecodedCallback decode(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("Callback không hợp lệ.");
        }
        String[] parts = value.split(":", -1);
        if (parts[0].equals("a")) {
            if (parts.length < 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("Callback quản trị không hợp lệ.");
            }
            return new DecodedCallback("a:" + parts[1], Arrays.asList(parts).subList(2, parts.length));
        }
        return new DecodedCallback(parts[0], Arrays.asList(parts).subList(1, parts.length));
    }

    public record DecodedCallback(String type, List<String> arguments) {
        public String stringArgument(int index) {
            return new String(Base64.getUrlDecoder().decode(arguments.get(index)), StandardCharsets.UTF_8);
        }

        public long longArgument(int index) {
            return Long.parseLong(arguments.get(index));
        }

        public int intArgument(int index) {
            return Integer.parseInt(arguments.get(index));
        }
    }
}

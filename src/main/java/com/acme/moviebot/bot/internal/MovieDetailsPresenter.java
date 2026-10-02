package com.acme.moviebot.bot.internal;

import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.SendPhotoAction;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public final class MovieDetailsPresenter {

    private static final int MESSAGE_CHUNK_SIZE = 4000;

    private MovieDetailsPresenter() {
    }

    public static List<BotAction> present(long chatId, MovieDetails movie, String notice, String followUp,
                                          List<List<InlineButton>> keyboard) {
        List<BotAction> actions = new ArrayList<>();
        if (StringUtils.hasText(movie.thumbnailFileId())) {
            actions.add(new SendPhotoAction(chatId, movie.thumbnailFileId(), null));
        }

        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(notice)) text.append(notice).append("\n\n");
        text.append("🎬 ").append(movie.vietnameseName());
        if (StringUtils.hasText(movie.chineseName())) {
            text.append("\n🇨🇳 Tên gốc: ").append(movie.chineseName());
        }
        text.append("\n📦 Trạng thái phát hành: ").append(statusLabel(movie.status()));
        text.append("\n📺 Tình trạng: ").append(movie.full() ? "Trọn bộ" : "Đang cập nhật");
        if (StringUtils.hasText(movie.description())) {
            text.append("\n\n📝 Mô tả:\n").append(movie.description().trim());
        } else {
            text.append("\n\n📝 Chưa có mô tả cho phim này.");
        }
        if (StringUtils.hasText(followUp)) text.append("\n\n").append(followUp);

        List<String> chunks = splitMessage(text.toString());
        for (int index = 0; index < chunks.size(); index++) {
            List<List<InlineButton>> messageKeyboard = index == chunks.size() - 1 ? keyboard : List.of();
            actions.add(new SendTextAction(chatId, chunks.get(index), messageKeyboard));
        }
        return List.copyOf(actions);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "Bản nháp";
            case "PUBLISHED" -> "Đã phát hành";
            case "ARCHIVED" -> "Đã lưu trữ";
            default -> status;
        };
    }

    private static List<String> splitMessage(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int remainingCodePoints = text.codePointCount(start, text.length());
            int end = text.offsetByCodePoints(start, Math.min(MESSAGE_CHUNK_SIZE, remainingCodePoints));
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end - 1);
                int space = text.lastIndexOf(' ', end - 1);
                int boundary = Math.max(newline, space);
                if (boundary > start) end = boundary;
            }
            chunks.add(text.substring(start, end).stripTrailing());
            start = end;
            while (start < text.length()) {
                int codePoint = text.codePointAt(start);
                if (!Character.isWhitespace(codePoint)) break;
                start += Character.charCount(codePoint);
            }
        }
        return chunks;
    }
}

package com.acme.moviebot.telegram.internal.mapper;

import com.acme.moviebot.bot.model.BotUpdate;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.IncomingMedia;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.telegram.internal.client.TelegramUpdateDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TelegramUpdateMapper {

    public Optional<BotUpdate> map(TelegramUpdateDto dto) {
        JsonNode payload = dto.payload();
        JsonNode callback = payload.path("callback_query");
        if (!callback.isMissingNode()) {
            JsonNode message = callback.path("message");
            long userId = callback.path("from").path("id").asLong(-1);
            long chatId = message.path("chat").path("id").asLong(-1);
            if (userId < 1 || chatId == -1) return Optional.empty();
            return Optional.of(new CallbackUpdate(dto.updateId(), userId, chatId,
                    callback.path("id").asText(), callback.path("data").asText(""),
                    nullableLong(message.path("message_id"))));
        }

        JsonNode message = payload.path("message");
        if (message.isMissingNode()) return Optional.empty();
        long userId = message.path("from").path("id").asLong(-1);
        long chatId = message.path("chat").path("id").asLong(-1);
        long messageId = message.path("message_id").asLong(-1);
        if (userId < 1 || chatId == -1 || messageId < 0) return Optional.empty();

        if (message.hasNonNull("text")) {
            return Optional.of(new TextMessageUpdate(dto.updateId(), userId, chatId, message.path("text").asText()));
        }

        JsonNode video = message.path("video");
        if (video.isMissingNode()) {
            JsonNode document = message.path("document");
            if (document.path("mime_type").asText("").toLowerCase().startsWith("video/")) video = document;
        }
        if (!video.isMissingNode() && video.hasNonNull("file_id")) {
            IncomingMedia media = new IncomingMedia(
                    video.path("file_id").asText(),
                    nullableText(video.path("file_unique_id")),
                    nullableText(video.path("file_name")),
                    nullableText(video.path("mime_type")),
                    nullableLong(video.path("file_size")),
                    nullableInteger(video.path("duration")),
                    nullableInteger(video.path("width")),
                    nullableInteger(video.path("height")));
            return Optional.of(new MediaMessageUpdate(dto.updateId(), userId, chatId, messageId, media));
        }

        JsonNode photos = message.path("photo");
        if (photos.isArray() && !photos.isEmpty()) {
            JsonNode photo = photos.get(photos.size() - 1);
            if (photo.hasNonNull("file_id")) {
                IncomingMedia media = new IncomingMedia(
                        photo.path("file_id").asText(),
                        nullableText(photo.path("file_unique_id")),
                        null,
                        "image/jpeg",
                        nullableLong(photo.path("file_size")),
                        null,
                        nullableInteger(photo.path("width")),
                        nullableInteger(photo.path("height")));
                return Optional.of(new MediaMessageUpdate(dto.updateId(), userId, chatId, messageId, media));
            }
        }
        return Optional.empty();
    }

    private String nullableText(JsonNode node) { return node == null || node.isMissingNode() || node.isNull() ? null : node.asText(); }
    private Long nullableLong(JsonNode node) { return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong(); }
    private Integer nullableInteger(JsonNode node) { return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt(); }
}

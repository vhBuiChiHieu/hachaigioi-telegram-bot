package com.acme.moviebot.telegram.internal.client;

import com.acme.moviebot.telegram.internal.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TelegramBotApiClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotApiClient.class);
    private final RestClient restClient;
    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;

    public TelegramBotApiClient(RestClient telegramRestClient, TelegramProperties properties, ObjectMapper objectMapper) {
        this.restClient = telegramRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TelegramUpdateDto> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("offset", offset);
        request.put("timeout", timeoutSeconds);
        request.put("allowed_updates", List.of("message", "callback_query"));
        JsonNode body = call("getUpdates", request);
        JsonNode result = body.path("result");
        List<TelegramUpdateDto> updates = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode update : result) {
                long id = update.path("update_id").asLong(-1);
                if (id >= 0) updates.add(new TelegramUpdateDto(id, update));
            }
        }
        return updates;
    }

    @Override
    public void sendMessage(long chatId, String text, List<List<Map<String, String>>> keyboard) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        if (keyboard != null && !keyboard.isEmpty()) request.put("reply_markup", Map.of("inline_keyboard", keyboard));
        call("sendMessage", request);
    }

    @Override
    public void sendPhoto(long chatId, String fileId, String caption) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("photo", fileId);
        if (caption != null && !caption.isBlank()) request.put("caption", caption);
        call("sendPhoto", request);
    }

    @Override
    public void sendVideo(long chatId, String fileId, String caption) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("video", fileId);
        if (caption != null && !caption.isBlank()) request.put("caption", caption);
        call("sendVideo", request);
    }

    @Override
    public void editMessageText(long chatId, long messageId, String text, List<List<Map<String, String>>> keyboard) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", chatId);
        request.put("message_id", messageId);
        request.put("text", text);
        if (keyboard != null && !keyboard.isEmpty()) request.put("reply_markup", Map.of("inline_keyboard", keyboard));
        call("editMessageText", request);
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("callback_query_id", callbackQueryId);
        if (text != null && !text.isBlank()) request.put("text", text);
        request.put("show_alert", showAlert);
        call("answerCallbackQuery", request);
    }

    @Override
    public void setMyCommands(List<TelegramBotCommand> commands, Map<String, Object> scope) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("commands", commands);
        request.put("scope", scope);
        call("setMyCommands", request);
    }

    private JsonNode call(String method, Map<String, Object> request) {
        String token = properties.botToken();
        if (token.isBlank()) throw new TelegramApiException("Telegram bot token is not configured.");
        String url = properties.apiBaseUrl() + "/bot" + token + "/" + method;
        try {
            JsonNode body = restClient.post().uri(url).body(request).retrieve().body(JsonNode.class);
            if (body == null || !body.path("ok").asBoolean(false)) {
                int code = body == null ? 0 : body.path("error_code").asInt(0);
                Integer retryAfter = retryAfter(body);
                String description = body == null ? "Empty response" : body.path("description").asText("Unknown Telegram API error");
                throw new TelegramApiException("Telegram API " + method + " failed: " + description, code, retryAfter, null);
            }
            return body;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            Integer retryAfter = null;
            String message = "Telegram API " + method + " returned HTTP " + status.value();
            try {
                JsonNode errorBody = objectMapper.readTree(exception.getResponseBodyAsString());
                retryAfter = retryAfter(errorBody);
                if (errorBody != null && errorBody.hasNonNull("description")) message += ": " + errorBody.path("description").asText();
            } catch (Exception ignored) {
                log.debug("Could not parse Telegram error response body");
            }
            throw new TelegramApiException(message, status.value(), retryAfter, exception);
        } catch (RestClientException exception) {
            throw new TelegramApiException("Telegram API " + method + " request failed.", 0, null, exception);
        }
    }

    private Integer retryAfter(JsonNode body) {
        JsonNode retryAfter = body == null ? null : body.path("parameters").path("retry_after");
        return retryAfter != null && retryAfter.canConvertToInt() ? retryAfter.asInt() : null;
    }
}

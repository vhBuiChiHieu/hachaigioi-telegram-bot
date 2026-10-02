package com.acme.moviebot.telegram.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public interface TelegramBotClient {

    List<TelegramUpdateDto> getUpdates(long offset, int timeoutSeconds);

    void sendMessage(long chatId, String text, List<List<Map<String, String>>> keyboard);

    void sendVideo(long chatId, String fileId, String caption);

    void editMessageText(long chatId, long messageId, String text, List<List<Map<String, String>>> keyboard);

    void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert);
}

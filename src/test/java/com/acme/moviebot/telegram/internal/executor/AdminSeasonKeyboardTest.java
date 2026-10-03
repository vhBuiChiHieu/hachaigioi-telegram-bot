package com.acme.moviebot.telegram.internal.executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.internal.BotUpdateRouter;
import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.internal.admin.AdminCommandHandler;
import com.acme.moviebot.bot.internal.admin.AdminConversationService;
import com.acme.moviebot.bot.internal.admin.AdminSessionRepository;
import com.acme.moviebot.bot.internal.user.UserCommandHandler;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
import com.acme.moviebot.telegram.internal.client.TelegramBotClient;
import com.acme.moviebot.telegram.internal.client.TelegramUpdateDto;
import com.acme.moviebot.telegram.internal.mapper.TelegramUpdateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminSeasonKeyboardTest {

    @Test
    void telegramCallbackUpdatesOnlyTheKeyboardOfItsOriginalMessage() throws Exception {
        AccessControl access = mock(AccessControl.class);
        CatalogQuery query = mock(CatalogQuery.class);
        CallbackDataCodec callbacks = new CallbackDataCodec();
        CatalogManagement catalog = mock(CatalogManagement.class);
        AdminConversationService conversations = new AdminConversationService(
                mock(AdminSessionRepository.class), catalog, query, callbacks);
        BotUpdateRouter router = new BotUpdateRouter(access,
                new AdminCommandHandler(access, conversations, catalog), mock(UserCommandHandler.class), callbacks);
        TelegramBotClient client = mock(TelegramBotClient.class);
        TelegramActionExecutor executor = new TelegramActionExecutor(client);
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(
                new MovieDetails(5L, "Bố già", null, "thumbnail", "Mô tả dài".repeat(1000), false, "PUBLISHED")));
        when(query.findSeasonsForAdmin(5L)).thenReturn(List.of(
                new SeasonDetails(9L, 5L, 1, 10, "DRAFT"),
                new SeasonDetails(10L, 5L, 2, 20, "PUBLISHED")));
        TelegramUpdateDto dto = new TelegramUpdateDto(1L, new ObjectMapper().readTree("""
                {"callback_query":{"id":"cb","from":{"id":7},"data":"a:manage_seasons:5",
                  "message":{"message_id":99,"chat":{"id":7}}}}
                """));

        router.route(new TelegramUpdateMapper().map(dto).orElseThrow()).forEach(executor::execute);

        verify(client).answerCallbackQuery("cb", "", false);
        verify(client).editMessageReplyMarkup(7L, 99L, List.of(
                List.of(Map.of("text", "➕ Thêm Season", "callback_data", "a:add_season:5")),
                List.of(Map.of("text", "Mùa 1 (DRAFT)", "callback_data", "a:manage_season:9"),
                        Map.of("text", "Đăng tải", "callback_data", "a:publish_season_row:9")),
                List.of(Map.of("text", "Mùa 2 (PUBLISHED)", "callback_data", "a:manage_season:10"),
                        Map.of("text", "Lưu trữ", "callback_data", "a:unpublish_season_row:10")),
                List.of(Map.of("text", "🔄 Chuyển trạng thái", "callback_data", "a:archive_movie:5"))));
        verifyNoMoreInteractions(client);
    }
}

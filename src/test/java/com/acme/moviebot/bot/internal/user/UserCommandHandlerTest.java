package com.acme.moviebot.bot.internal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.internal.CallbackDataCodec.DecodedCallback;
import com.acme.moviebot.bot.internal.BotUpdateRouter;
import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.internal.admin.AdminCommandHandler;
import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.SendVideoAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserCommandHandlerTest {

    @Test
    void findWithoutKeywordUsesTheNextTextMessageAsTheSearchKeyword() {
        CatalogQuery catalog = mock(CatalogQuery.class);
        CallbackDataCodec callbacks = new CallbackDataCodec();
        UserCommandHandler users = new UserCommandHandler(catalog, callbacks);
        BotUpdateRouter router = new BotUpdateRouter(
                mock(AccessControl.class), mock(AdminCommandHandler.class), users, callbacks);
        when(catalog.searchMovies("Bố già", 11))
                .thenReturn(List.of(new MovieSummary(5L, "Bố già", null)));

        List<BotAction> prompt = router.route(new TextMessageUpdate(1L, 7L, 7L, "/find"));

        assertThat(prompt).containsExactly(new SendTextAction(7L,
                "Cú pháp tìm phim: /find <tên phim>. Bạn cũng có thể gửi trực tiếp từ khóa cần tìm trong tin nhắn tiếp theo."));
        assertThat(users.awaitsSearchKeyword(7L, 7L)).isTrue();

        List<BotAction> results = router.route(new TextMessageUpdate(2L, 7L, 7L, "Bố già"));

        assertThat(results).containsExactly(new SendTextAction(7L, "Kết quả tìm kiếm:",
                List.of(List.of(new InlineButton("Bố già", "m:5")))));
        assertThat(users.awaitsSearchKeyword(7L, 7L)).isFalse();
        verify(catalog).searchMovies("Bố già", 11);
    }

    @Test
    void selectingSeasonSendsEveryPublishedEpisodeVideoImmediately() {
        CatalogQuery catalog = mock(CatalogQuery.class);
        CallbackDataCodec callbacks = new CallbackDataCodec();
        UserCommandHandler users = new UserCommandHandler(catalog, callbacks);
        when(catalog.findPublishedEpisodes(9L)).thenReturn(List.of(
                new EpisodeSummary(91L, 9L, 1),
                new EpisodeSummary(92L, 9L, 2)));
        when(catalog.findEpisodeMedia(91L)).thenReturn(Optional.of(
                new EpisodeMediaView(91L, "Bố già", 1, 1, "file-1")));
        when(catalog.findEpisodeMedia(92L)).thenReturn(Optional.of(
                new EpisodeMediaView(92L, "Bố già", 1, 2, "file-2")));

        List<BotAction> actions = users.handleCallback(
                new CallbackUpdate(3L, 7L, 7L, "callback-1", "s:9"),
                new DecodedCallback("s", List.of("9")));

        assertThat(actions).containsExactly(
                new AnswerCallbackAction("callback-1", "", false),
                new SendVideoAction(7L, "file-1", "Bố già - Mùa 1 - Phần 1"),
                new SendVideoAction(7L, "file-2", "Bố già - Mùa 1 - Phần 2"));
        verify(catalog).findEpisodeMedia(91L);
        verify(catalog).findEpisodeMedia(92L);
    }
}

package com.acme.moviebot.bot.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.internal.BotUpdateRouter;
import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.internal.user.UserCommandHandler;
import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.EditMessageKeyboardAction;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminMovieNavigationTest {

    private final AccessControl access = mock(AccessControl.class);
    private final CatalogQuery query = mock(CatalogQuery.class);
    private final CatalogManagement catalog = mock(CatalogManagement.class);
    private final AdminSessionRepository sessions = mock(AdminSessionRepository.class);
    private final CallbackDataCodec callbacks = new CallbackDataCodec();
    private final AdminConversationService conversations = new AdminConversationService(sessions, catalog, query, callbacks);
    private final BotUpdateRouter router = new BotUpdateRouter(access,
            new AdminCommandHandler(access, conversations, catalog), mock(UserCommandHandler.class), callbacks);

    @Test
    void movieDetailsHideSeasonsUntilManagementIsOpened() {
        when(query.findMovie(5L)).thenReturn(Optional.of(movie()));

        List<BotAction> actions = conversations.showMovieManagement(7L, 5L, "Đã cập nhật.");

        assertThat(actions).hasSize(1);
        SendTextAction details = (SendTextAction) actions.getFirst();
        assertThat(details.text()).contains("Bố già", "Đã cập nhật.").doesNotContain("Danh sách mùa");
        assertThat(details.keyboard()).containsExactly(
                List.of(new InlineButton("Quản lý mùa phim", "a:manage_seasons:5")),
                List.of(new InlineButton("✅ Publish phim", "a:publish_movie:5")));
        verify(query, never()).findSeasonsForAdmin(5L);
    }

    @Test
    void movieWithoutSeasonsStillAllowsAddingOneOnTheSameMessage() {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie()));
        when(query.findSeasonsForAdmin(5L)).thenReturn(List.of());

        assertThat(router.route(new CallbackUpdate(1L, 7L, 7L, "cb", "a:manage_seasons:5", 99L)))
                .containsExactly(new AnswerCallbackAction("cb", "", false),
                        new EditMessageKeyboardAction(7L, 99L, List.of(
                                List.of(new InlineButton("➕ Thêm Season", "a:add_season:5")),
                                List.of(new InlineButton("✅ Publish phim", "a:publish_movie:5")))));
    }

    @Test
    void returningFromSeasonOpensSeasonListDirectly() {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie()));
        when(query.findSeasonForAdmin(9L)).thenReturn(Optional.of(season()));
        when(query.findSeasonsForAdmin(5L)).thenReturn(List.of(season()));

        List<BotAction> seasonActions = router.route(new CallbackUpdate(1L, 7L, 7L, "cb", "a:manage_season:9"));
        SendTextAction seasonDetails = (SendTextAction) seasonActions.getLast();
        InlineButton back = seasonDetails.keyboard().get(1).getFirst();
        assertThat(back.text()).isEqualTo("⬅️ Quay lại danh sách mùa");

        List<BotAction> backActions = router.route(new CallbackUpdate(2L, 7L, 7L, "back", back.callbackData(), 100L));
        SendTextAction seasonList = (SendTextAction) backActions.getLast();
        assertThat(seasonList.text()).contains("Danh sách mùa");
        assertThat(seasonList.keyboard()).containsExactly(
                List.of(new InlineButton("➕ Thêm Season", "a:add_season:5")),
                List.of(new InlineButton("Mùa 1 (DRAFT)", "a:manage_season:9")),
                List.of(new InlineButton("✅ Publish phim", "a:publish_movie:5")));
    }

    @Test
    void nonAdminCannotExpandSeasons() {
        List<BotAction> actions = router.route(new CallbackUpdate(1L, 8L, 7L, "cb", "a:manage_seasons:5", 99L));

        assertThat(actions).containsExactly(new AnswerCallbackAction("cb", "", false),
                new SendTextAction(7L, "Bạn không có quyền thực hiện thao tác quản trị này."));
        verifyNoInteractions(query, catalog, sessions);
    }

    @Test
    void missingMessageOrMovieDoesNotProduceAnEdit() {
        assertThat(conversations.expandMovieSeasons(7L, null, 5L))
                .containsExactly(new SendTextAction(7L, "Không thể cập nhật tin nhắn. Vui lòng mở lại chi tiết phim."));
        when(query.findMovie(5L)).thenReturn(Optional.empty());
        assertThat(conversations.expandMovieSeasons(7L, 99L, 5L))
                .containsExactly(new SendTextAction(7L, "Không tìm thấy phim."));
        verify(query, never()).findSeasonsForAdmin(5L);
    }

    private MovieDetails movie() {
        return new MovieDetails(5L, "Bố già", null, null, "Mô tả phim", false, "DRAFT");
    }

    private SeasonDetails season() {
        return new SeasonDetails(9L, 5L, 1, 10, "DRAFT");
    }
}

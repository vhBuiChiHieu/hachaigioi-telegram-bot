package com.acme.moviebot.bot.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import com.acme.moviebot.bot.model.SendVideoAction;
import com.acme.moviebot.catalog.CatalogConflictException;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeDetails;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaType;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminEpisodeNavigationTest {

    private final AccessControl access = mock(AccessControl.class);
    private final CatalogQuery query = mock(CatalogQuery.class);
    private final CatalogManagement catalog = mock(CatalogManagement.class);
    private final AdminSessionRepository sessions = mock(AdminSessionRepository.class);
    private final CallbackDataCodec callbacks = new CallbackDataCodec();
    private final AdminConversationService conversations = new AdminConversationService(sessions, catalog, query, callbacks);
    private final BotUpdateRouter router = new BotUpdateRouter(access,
            new AdminCommandHandler(access, conversations, catalog), mock(UserCommandHandler.class), callbacks);

    @Test
    void seasonDetailsSummarizeTheSeasonAndPutOrderedPartsInTheKeyboard() {
        when(query.findSeasonForAdmin(9L)).thenReturn(Optional.of(season()));
        when(query.findMovie(5L)).thenReturn(Optional.of(new MovieDetails(
                5L, "Bố già", "教父", null, null, false, "PUBLISHED")));
        when(query.findEpisodesForAdmin(9L)).thenReturn(List.of(part("DRAFT"), otherPart()));

        SendTextAction details = (SendTextAction) conversations.showSeasonManagement(7L, 9L).getFirst();

        assertThat(details.text()).contains("🎞 Phim: Bố già", "🔢 Mùa: 2", "📦 Trạng thái: Đã đăng tải",
                "Tổng số tập gốc: 20", "Số phần đã thêm: 2").doesNotContain("Phần 1 (", "Phần 2 (");
        assertThat(details.keyboard()).isEqualTo(keyboard("DRAFT"));
    }

    @Test
    void emptySeasonStillOffersAddingAPartAndBackNavigation() {
        when(query.findSeasonForAdmin(9L)).thenReturn(Optional.of(season()));
        when(query.findEpisodesForAdmin(9L)).thenReturn(List.of());

        SendTextAction details = (SendTextAction) conversations.showSeasonManagement(7L, 9L).getFirst();

        assertThat(details.text()).contains("Số phần đã thêm: 0", "Chưa có phần phim nào");
        assertThat(details.keyboard()).containsExactly(List.of(new InlineButton("➕ Thêm phần phim", "a:add_episode:9")),
                List.of(new InlineButton("⬅️ Quay lại danh sách mùa", "a:list_seasons:5")),
                List.of(new InlineButton("📦 Lưu trữ Season", "a:archive_season:9")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partPublicationRefreshesTheSameMessageAndPreservesOtherRows(boolean initiallyPublished) {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findSeasonForAdmin(9L)).thenReturn(Optional.of(season()));
        AtomicReference<EpisodeDetails> selected = new AtomicReference<>(part(initiallyPublished ? "PUBLISHED" : "DRAFT"));
        when(query.findEpisodeForAdmin(91L)).thenAnswer(invocation -> Optional.of(selected.get()));
        when(query.findEpisodesForAdmin(9L)).thenAnswer(invocation -> List.of(selected.get(), otherPart()));
        if (initiallyPublished) {
            doAnswer(invocation -> { selected.set(part("DRAFT")); return null; }).when(catalog).unpublishEpisode(91L);
        } else {
            doAnswer(invocation -> { selected.set(part("PUBLISHED")); return null; }).when(catalog).publishEpisode(91L);
        }
        SendTextAction open = (SendTextAction) conversations.showSeasonManagement(7L, 9L).getFirst();
        InlineButton change = open.keyboard().get(1).get(1);

        List<BotAction> actions = router.route(new CallbackUpdate(1L, 7L, 7L, "cb", change.callbackData(), 99L));

        if (initiallyPublished) verify(catalog).unpublishEpisode(91L);
        else verify(catalog).publishEpisode(91L);
        assertThat(actions).containsExactly(new AnswerCallbackAction("cb", "", false),
                new EditMessageKeyboardAction(7L, 99L, keyboard(initiallyPublished ? "DRAFT" : "PUBLISHED")));
    }

    @Test
    void missingContentCannotPublishAPartOrProduceASuccessfulKeyboardUpdate() {
        when(query.findEpisodeForAdmin(91L)).thenReturn(Optional.of(part("DRAFT")));
        when(query.findSeasonForAdmin(9L)).thenReturn(Optional.of(season()));
        doThrow(new CatalogConflictException("Hãy gửi video hoặc link trước khi đăng tải phần phim này.")).when(catalog).publishEpisode(91L);

        assertThatThrownBy(() -> conversations.setEpisodePublished(7L, 99L, 91L, true))
                .isInstanceOf(CatalogConflictException.class).hasMessageContaining("gửi video");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void adminPartSelectionShowsDraftVideoOrAnExplicitMissingVideoMessage(boolean hasVideo) {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findEpisodeForAdmin(91L)).thenReturn(Optional.of(part("DRAFT")));
        when(query.findEpisodeMediaForAdmin(91L)).thenReturn(hasVideo
                ? Optional.of(new EpisodeMediaView(91L, "Bố già", 2, 1, EpisodeMediaType.VIDEO, "draft-video", null)) : Optional.empty());

        List<BotAction> actions = router.route(new CallbackUpdate(1L, 7L, 7L, "cb", "a:manage_episode:91", 99L));

        assertThat(actions.getFirst()).isEqualTo(new AnswerCallbackAction("cb", "", false));
        if (hasVideo) assertThat(actions.get(1)).isEqualTo(new SendVideoAction(7L, "draft-video", "Bố già - Mùa 2 - Phần 1"));
        else assertThat(actions).hasSize(2);
        SendTextAction details = (SendTextAction) actions.getLast();
        assertThat(details.text()).contains("Phần 1", "Bản nháp",
                hasVideo ? "Nội dung được gửi ở trên" : "Chưa có video hoặc link");
        assertThat(details.keyboard()).containsExactly(
                List.of(new InlineButton("⬅️ Quay lại danh sách phần phim", "a:manage_season:9")));
    }

    @Test
    void adminPartSelectionPreviewsDraftLinkWithBackNavigation() {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findEpisodeForAdmin(91L)).thenReturn(Optional.of(part("DRAFT")));
        when(query.findEpisodeMediaForAdmin(91L)).thenReturn(Optional.of(
                new EpisodeMediaView(91L, "Bố già", 2, 1, EpisodeMediaType.LINK, null, "https://example.com/part1")));

        List<BotAction> actions = router.route(new CallbackUpdate(1L, 7L, 7L, "cb", "a:manage_episode:91", 99L));

        assertThat(actions.get(1)).isEqualTo(new SendTextAction(7L, "Bố già - Mùa 2 - Phần 1\nhttps://example.com/part1"));
        SendTextAction details = (SendTextAction) actions.getLast();
        assertThat(details.text()).contains("Phần 1", "Bản nháp", "Nội dung được gửi ở trên");
        assertThat(details.keyboard()).containsExactly(
                List.of(new InlineButton("⬅️ Quay lại danh sách phần phim", "a:manage_season:9")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manage_episode", "publish_episode_row", "unpublish_episode_row"})
    void nonAdminsCannotPreviewOrChangeParts(String action) {
        assertThat(router.route(new CallbackUpdate(1L, 8L, 7L, "cb", "a:" + action + ":91", 99L)))
                .containsExactly(new AnswerCallbackAction("cb", "", false),
                        new SendTextAction(7L, "Bạn không có quyền thực hiện thao tác quản trị này."));
        verifyNoInteractions(query, catalog, sessions);
    }

    @Test
    void missingMessageOrPartCannotChangePublication() {
        assertThat(conversations.setEpisodePublished(7L, null, 91L, true))
                .containsExactly(new SendTextAction(7L, "Không thể cập nhật tin nhắn. Vui lòng mở lại chi tiết mùa phim."));
        when(query.findEpisodeForAdmin(91L)).thenReturn(Optional.empty());
        assertThat(conversations.setEpisodePublished(7L, 99L, 91L, true))
                .containsExactly(new SendTextAction(7L, "Không tìm thấy phần phim."));
        verifyNoInteractions(catalog);
    }

    private SeasonDetails season() {
        return new SeasonDetails(9L, 5L, 2, 20, "PUBLISHED");
    }

    private EpisodeDetails part(String status) {
        return new EpisodeDetails(91L, 9L, 1, status);
    }

    private EpisodeDetails otherPart() {
        return new EpisodeDetails(92L, 9L, 2, "PUBLISHED");
    }

    private List<List<InlineButton>> keyboard(String status) {
        boolean published = status.equals("PUBLISHED");
        return List.of(List.of(new InlineButton("➕ Thêm phần phim", "a:add_episode:9")),
                List.of(new InlineButton("Phần 1 (" + status + ")", "a:manage_episode:91"),
                        new InlineButton(published ? "Lưu trữ" : "Đăng tải",
                                published ? "a:unpublish_episode_row:91" : "a:publish_episode_row:91")),
                List.of(new InlineButton("Phần 2 (PUBLISHED)", "a:manage_episode:92"),
                        new InlineButton("Lưu trữ", "a:unpublish_episode_row:92")),
                List.of(new InlineButton("⬅️ Quay lại danh sách mùa", "a:list_seasons:5")),
                List.of(new InlineButton("📦 Lưu trữ Season", "a:archive_season:9")));
    }
}

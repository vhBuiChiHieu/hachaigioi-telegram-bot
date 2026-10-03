package com.acme.moviebot.bot.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
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
import com.acme.moviebot.bot.model.IncomingMedia;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogCommands.UpdateMovieDetailsCommand;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AdminGeneralManagementTest {

    private final AccessControl access = mock(AccessControl.class);
    private final CatalogQuery query = mock(CatalogQuery.class);
    private final CatalogManagement catalog = mock(CatalogManagement.class);
    private final AdminSessionRepository sessions = mock(AdminSessionRepository.class);
    private final CallbackDataCodec callbacks = new CallbackDataCodec();
    private final AdminConversationService conversations = new AdminConversationService(sessions, catalog, query, callbacks);
    private final AdminCommandHandler handler = new AdminCommandHandler(access, conversations, catalog);
    private final BotUpdateRouter router = new BotUpdateRouter(access, handler, mock(UserCommandHandler.class), callbacks);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openingGeneralManagementEditsTheSameMessageAndReflectsFull(boolean full) {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie(full)));

        assertThat(router.route(callback("manage_general"))).containsExactly(
                new AnswerCallbackAction("cb", "", false),
                new EditMessageKeyboardAction(7L, 99L, keyboard(full)));
        verifyNoInteractions(catalog, sessions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullActionSetsAnExplicitStateAndUpdatesTheButton(boolean full) {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie(full)));

        List<BotAction> actions = router.route(callback(full ? "enable_full" : "disable_full"));

        verify(catalog).setMovieFull(5L, full);
        assertThat(actions).containsExactly(new AnswerCallbackAction("cb", "", false),
                new EditMessageKeyboardAction(7L, 99L, keyboard(full)),
                new SendTextAction(7L, full ? "Đã bật FULL: phim trọn bộ." : "Đã tắt FULL: phim đang cập nhật."));
    }

    @Test
    void detailsEditPersistsTheSessionAndAcceptsThreeQuotedFields() {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie(false)));
        router.route(callback("edit_details"));
        ArgumentCaptor<AdminSession> saved = ArgumentCaptor.forClass(AdminSession.class);
        verify(sessions).save(saved.capture());
        AdminSession session = saved.getValue();
        assertThat(session.getFlow()).isEqualTo(AdminFlow.UPDATE_MOVIE_DETAILS);
        assertThat(session.getState()).isEqualTo(AdminSessionState.WAITING_MOVIE_DETAILS_UPDATE);
        assertThat(session.getContextJson()).containsEntry("movieId", 5L);
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));
        String description = "Mô tả có dấu, \"trích dẫn\" và khoảng trắng.\nDòng thứ hai giữ nguyên.";

        AdminConversationService resumed = new AdminConversationService(sessions, catalog, query, callbacks);
        List<BotAction> actions = resumed.handleText(new TextMessageUpdate(2L, 7L, 7L,
                "\"教 父\" \"Bố già mới\" \"" + description + "\""));

        verify(catalog).updateMovieDetails(new UpdateMovieDetailsCommand(5L, "教 父", "Bố già mới", description));
        verify(sessions).delete(session);
        assertThat(((SendTextAction) actions.getLast()).text()).contains("Đã cập nhật tên và mô tả");
    }

    @ParameterizedTest
    @ValueSource(strings = {"教父 Bố già mô tả", "\"教父\" \"Bố già\"", "\" \" \"Bố già\" \"mô tả\"",
            "\"教父\" \" \" \"mô tả\"", "\"教父\" \"Bố già\" \"   \"", "\"教父 \"Bố già\" \"mô tả\"",
            "\"教父\" \"Bố già\" mô tả", "\"教父\" \"Bố già\" \"mô tả",
            "\"教父\" \"Bố già\" mô tả\"", "\"教父\" \"Bố già\" \"\"",
            "\"教父\" \"Bố già\" \"mô tả\" nội dung ngoài dấu ngoặc"})
    void invalidDetailsKeepTheSessionWithoutWriting(String input) {
        AdminSession session = session(AdminFlow.UPDATE_MOVIE_DETAILS, AdminSessionState.WAITING_MOVIE_DETAILS_UPDATE);
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));

        List<BotAction> actions = conversations.handleText(new TextMessageUpdate(2L, 7L, 7L, input));

        assertThat(((SendTextAction) actions.getFirst()).text()).contains("Gửi đúng cú pháp");
        assertThat(session.getState()).isEqualTo(AdminSessionState.WAITING_MOVIE_DETAILS_UPDATE);
        verifyNoInteractions(catalog);
    }

    @Test
    void oversizedNameIsRejectedBeforeCatalogWrite() {
        AdminSession session = session(AdminFlow.UPDATE_MOVIE_DETAILS, AdminSessionState.WAITING_MOVIE_DETAILS_UPDATE);
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));

        List<BotAction> actions = conversations.handleText(new TextMessageUpdate(2L, 7L, 7L,
                "\"教父\" \"" + "a".repeat(256) + "\" \"mô tả\""));

        assertThat(((SendTextAction) actions.getFirst()).text()).contains("255 ký tự");
        verifyNoInteractions(catalog);
    }

    @Test
    void thumbnailEditRejectsVideoAndTextThenAcceptsPhotoAndEndsSession() {
        when(access.isAdmin(7L)).thenReturn(true);
        when(query.findMovie(5L)).thenReturn(Optional.of(movie(false)));
        router.route(callback("edit_thumbnail"));
        ArgumentCaptor<AdminSession> saved = ArgumentCaptor.forClass(AdminSession.class);
        verify(sessions).save(saved.capture());
        AdminSession session = saved.getValue();
        assertThat(session.getFlow()).isEqualTo(AdminFlow.UPDATE_MOVIE_THUMBNAIL);
        assertThat(session.getState()).isEqualTo(AdminSessionState.WAITING_MOVIE_THUMBNAIL_UPDATE);
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));

        conversations.handleText(new TextMessageUpdate(2L, 7L, 7L, "-"));
        conversations.handleMedia(media("video/mp4"));
        verifyNoInteractions(catalog);
        doAnswer(invocation -> {
            when(query.findMovie(5L)).thenReturn(Optional.of(new MovieDetails(
                    5L, "Bố già", "教父", "new-cover", "Mô tả", false, "PUBLISHED")));
            return null;
        }).when(catalog).updateMovieThumbnail(5L, "new-cover");

        List<BotAction> actions = conversations.handleMedia(media("image/jpeg"));

        verify(catalog).updateMovieThumbnail(5L, "new-cover");
        verify(sessions).delete(session);
        assertThat(actions.getFirst()).isInstanceOf(com.acme.moviebot.bot.model.SendPhotoAction.class);
        assertThat(((SendTextAction) actions.getLast()).text()).contains("Đã cập nhật ảnh bìa");
    }

    @Test
    void expiredSessionCannotUpdateTheMovie() {
        AdminSession expired = new AdminSession(7L, 7L, AdminFlow.UPDATE_MOVIE_DETAILS,
                AdminSessionState.WAITING_MOVIE_DETAILS_UPDATE, Map.of("movieId", 5L), now().minusMinutes(1));
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(expired));

        List<BotAction> actions = conversations.handleText(new TextMessageUpdate(2L, 7L, 7L,
                "\"教父\" \"Bố già\" \"mô tả\""));

        assertThat(((SendTextAction) actions.getFirst()).text()).contains("hết hạn");
        verify(sessions).delete(expired);
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"manage_general", "edit_details", "edit_thumbnail", "enable_full", "disable_full"})
    void nonAdminCannotManageGeneralFields(String action) {
        assertThat(router.route(callback(action))).containsExactly(new AnswerCallbackAction("cb", "", false),
                new SendTextAction(7L, "Bạn không có quyền thực hiện thao tác quản trị này."));
        verifyNoInteractions(query, catalog, sessions);
    }

    private CallbackUpdate callback(String action) {
        return new CallbackUpdate(1L, 7L, 7L, "cb", "a:" + action + ":5", 99L);
    }

    private AdminSession session(AdminFlow flow, AdminSessionState state) {
        return new AdminSession(7L, 7L, flow, state, Map.of("movieId", 5L), now().plusMinutes(30));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private MediaMessageUpdate media(String mimeType) {
        return new MediaMessageUpdate(2L, 7L, 7L, 100L,
                new IncomingMedia("new-cover", "unique", null, mimeType, null, null, null, null));
    }

    private MovieDetails movie(boolean full) {
        return new MovieDetails(5L, "Bố già", "教父", null, "Mô tả", full, "PUBLISHED");
    }

    private List<List<InlineButton>> keyboard(boolean full) {
        return List.of(List.of(new InlineButton("Cập nhật tên, mô tả", "a:edit_details:5")),
                List.of(new InlineButton("Cập nhật ảnh bìa", "a:edit_thumbnail:5")),
                List.of(new InlineButton(full ? "Tắt FULL" : "Bật FULL", full ? "a:disable_full:5" : "a:enable_full:5")));
    }
}

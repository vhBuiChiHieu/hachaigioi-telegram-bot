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
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.IncomingMedia;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogCommands.AttachLinkCommand;
import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AdminEpisodeContentTest {

    private final AdminSessionRepository sessions = mock(AdminSessionRepository.class);
    private final CatalogManagement catalog = mock(CatalogManagement.class);
    private final CatalogQuery query = mock(CatalogQuery.class);
    private final CallbackDataCodec callbacks = new CallbackDataCodec();
    private final AdminConversationService conversations = new AdminConversationService(sessions, catalog, query, callbacks);

    @Test
    void addingPartPersistsTheContentInputStateAndOffersBothSources() {
        when(catalog.createEpisode(new CreateEpisodeCommand(9L))).thenReturn(91L);

        SendTextAction prompt = (SendTextAction) conversations.startEpisode(7L, 7L, 9L).getFirst();

        ArgumentCaptor<AdminSession> saved = ArgumentCaptor.forClass(AdminSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo(AdminSessionState.WAITING_EPISODE_CONTENT);
        assertThat(saved.getValue().getContextJson()).containsEntry("episodeId", 91L).containsEntry("seasonId", 9L);
        assertThat(prompt.text()).contains("video", "link");
    }

    @ParameterizedTest
    @EnumSource(value = AdminSessionState.class, names = {"WAITING_EPISODE_CONTENT", "WAITING_EPISODE_VIDEO"})
    void resumedSessionsAcceptLinkAndOfferPublicationWithoutPublishingAutomatically(AdminSessionState state) {
        AdminSession session = pending(state);
        String url = "https://t.me/c/123/456?single";
        AccessControl access = mock(AccessControl.class);
        when(access.isAdmin(7L)).thenReturn(true);
        AdminConversationService resumed = new AdminConversationService(sessions, catalog, query, callbacks);
        BotUpdateRouter router = new BotUpdateRouter(access,
                new AdminCommandHandler(access, resumed, catalog), mock(UserCommandHandler.class), callbacks);

        assertThat(router.route(new TextMessageUpdate(2L, 7L, 7L, "  " + url + "  ")))
                .containsExactly(attached("Link"));

        verify(catalog).attachLink(new AttachLinkCommand(91L, url));
        verify(catalog, never()).publishEpisode(91L);
        verify(sessions).delete(session);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "xin chào", "ftp://example.com/file", "https://",
            "https://example.com/a https://example.com/b", "https://example.com/a\nhttps://example.com/b"})
    void invalidTextKeepsTheSessionWithoutWritingContent(String text) {
        AdminSession session = pending(AdminSessionState.WAITING_EPISODE_CONTENT);

        List<BotAction> actions = conversations.handleText(new TextMessageUpdate(2L, 7L, 7L, text));

        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst()).isInstanceOf(SendTextAction.class);
        assertThat(session.getState()).isEqualTo(AdminSessionState.WAITING_EPISODE_CONTENT);
        verifyNoInteractions(catalog);
        verify(sessions, never()).delete(session);
    }

    @ParameterizedTest
    @EnumSource(value = AdminSessionState.class, names = {"WAITING_EPISODE_CONTENT", "WAITING_EPISODE_VIDEO"})
    void videoStillWorksInNewAndLegacySessions(AdminSessionState state) {
        AdminSession session = pending(state);
        IncomingMedia video = new IncomingMedia("file", "unique", "part.mp4", "video/mp4", 100L, 10, 640, 480);

        assertThat(conversations.handleMedia(new MediaMessageUpdate(2L, 7L, 7L, 99L, video)))
                .containsExactly(attached("Video"));

        verify(catalog).attachMedia(new AttachMediaCommand(91L, "file", "unique", 7L, 99L,
                "part.mp4", "video/mp4", 100L, 10, 640, 480));
        verify(sessions).delete(session);
    }

    @Test
    void photoKeepsTheSessionAvailableForVideoOrLink() {
        AdminSession session = pending(AdminSessionState.WAITING_EPISODE_CONTENT);
        IncomingMedia photo = new IncomingMedia("photo", null, null, "image/jpeg", null, null, null, null);

        SendTextAction response = (SendTextAction) conversations.handleMedia(
                new MediaMessageUpdate(2L, 7L, 7L, 99L, photo)).getFirst();

        assertThat(response.text()).contains("video", "link");
        verifyNoInteractions(catalog);
        verify(sessions, never()).delete(session);
    }

    @Test
    void expiredSessionCannotAttachLink() {
        AdminSession session = new AdminSession(7L, 7L, AdminFlow.CREATE_EPISODE,
                AdminSessionState.WAITING_EPISODE_CONTENT, Map.of("seasonId", 9L, "episodeId", 91L),
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));

        SendTextAction response = (SendTextAction) conversations.handleText(
                new TextMessageUpdate(2L, 7L, 7L, "https://example.com/part")).getFirst();

        assertThat(response.text()).contains("hết hạn");
        verify(sessions).delete(session);
        verifyNoInteractions(catalog);
    }

    private AdminSession pending(AdminSessionState state) {
        AdminSession session = new AdminSession(7L, 7L, AdminFlow.CREATE_EPISODE, state,
                Map.of("seasonId", 9L, "episodeId", 91L), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30));
        when(sessions.findByTelegramUserIdAndChatId(7L, 7L)).thenReturn(Optional.of(session));
        return session;
    }

    private SendTextAction attached(String label) {
        return new SendTextAction(7L, label + " đã được gắn vào phần phim ✅",
                List.of(List.of(new InlineButton("✅ Publish phần phim", "a:publish_episode:91")),
                        List.of(new InlineButton("➕ Thêm phần tiếp theo", "a:add_episode:9"))));
    }
}

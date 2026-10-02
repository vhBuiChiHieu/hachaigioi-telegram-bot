package com.acme.moviebot.bot.internal.admin;

import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.IncomingMedia;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateMovieCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateSeasonCommand;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AdminConversationService {

    private static final int SESSION_MINUTES = 30;
    private final AdminSessionRepository sessions;
    private final CatalogManagement catalog;
    private final CatalogQuery catalogQuery;
    private final CallbackDataCodec callbacks;

    public AdminConversationService(AdminSessionRepository sessions, CatalogManagement catalog, CatalogQuery catalogQuery,
                                   CallbackDataCodec callbacks) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.catalogQuery = catalogQuery;
        this.callbacks = callbacks;
    }

    public List<BotAction> startMovie(long userId, long chatId) {
        startSession(userId, chatId, AdminFlow.CREATE_MOVIE, AdminSessionState.WAITING_MOVIE_NAME, Map.of());
        return List.of(new SendTextAction(chatId, "Nhập tên phim:"));
    }

    public List<BotAction> startSeason(long userId, long chatId, long movieId) {
        startSession(userId, chatId, AdminFlow.CREATE_SEASON, AdminSessionState.WAITING_SEASON_NUMBER,
                Map.of("movieId", movieId));
        return List.of(new SendTextAction(chatId, "Nhập số season (0 trở lên):"));
    }

    public List<BotAction> startEpisode(long userId, long chatId, long seasonId) {
        startSession(userId, chatId, AdminFlow.CREATE_EPISODE, AdminSessionState.WAITING_EPISODE_NUMBER,
                Map.of("seasonId", seasonId));
        return List.of(new SendTextAction(chatId, "Nhập số tập (0 trở lên):"));
    }

    public List<BotAction> startSearch(long userId, long chatId) {
        startSession(userId, chatId, AdminFlow.SEARCH_MOVIES, AdminSessionState.WAITING_MOVIE_SEARCH, Map.of());
        return List.of(new SendTextAction(chatId, "Nhập tên hoặc từ khóa phim cần tìm:"));
    }

    public List<BotAction> showMovieManagement(long chatId, long movieId) {
        MovieDetails movie = catalogQuery.findMovie(movieId).orElse(null);
        if (movie == null) return List.of(new SendTextAction(chatId, "Không tìm thấy phim."));
        List<List<InlineButton>> keyboard = new ArrayList<>();
        if (!"PUBLISHED".equals(movie.status())) {
            keyboard.add(List.of(new InlineButton("✅ Publish phim", callbacks.admin("publish_movie", movieId))));
        }
        keyboard.add(List.of(new InlineButton("➕ Thêm Season", callbacks.admin("add_season", movieId))));
        List<SeasonSummary> seasons = catalogQuery.findSeasonsForAdmin(movieId);
        for (SeasonSummary season : seasons) {
            keyboard.add(List.of(new InlineButton("Thêm Episode vào Season " + season.seasonNumber(),
                    callbacks.admin("add_episode", season.id()))));
        }
        return List.of(new SendTextAction(chatId, movie.name() + " (" + movie.status() + ")", keyboard));
    }

    public List<BotAction> cancel(long userId, long chatId) {
        sessions.deleteByTelegramUserIdAndChatId(userId, chatId);
        return List.of(new SendTextAction(chatId, "Đã hủy thao tác quản trị."));
    }

    public boolean hasSession(long userId, long chatId) {
        return sessions.findByTelegramUserIdAndChatId(userId, chatId).isPresent();
    }

    public List<BotAction> handleText(TextMessageUpdate update) {
        AdminSession savedSession = sessions.findByTelegramUserIdAndChatId(update.userId(), update.chatId()).orElse(null);
        if (savedSession != null && isExpired(savedSession)) {
            sessions.delete(savedSession);
            return List.of(new SendTextAction(update.chatId(), "Thao tác quản trị đã hết hạn. Vui lòng bắt đầu lại bằng /admin."));
        }
        AdminSession session = savedSession;
        if (session == null) {
            return List.of(new SendTextAction(update.chatId(), "Dùng /admin để bắt đầu quản lý nội dung."));
        }
        String text = update.text() == null ? "" : update.text().trim();
        if (!StringUtils.hasText(text)) {
            return List.of(new SendTextAction(update.chatId(), "Nội dung không được để trống. Hãy nhập lại:"));
        }
        Map<String, Object> context = readContext(session.getContextJson());

        return switch (session.getState()) {
            case WAITING_MOVIE_NAME -> createMovie(session, update.chatId(), text);
            case WAITING_MOVIE_SEARCH -> searchMovies(session, update.chatId(), text);
            case WAITING_SEASON_NUMBER -> createSeason(session, context, update.chatId(), text);
            case WAITING_EPISODE_NUMBER -> acceptEpisodeNumber(session, context, update.chatId(), text);
            case WAITING_EPISODE_NAME -> createEpisode(session, context, update.chatId(), text);
            default -> List.of(new SendTextAction(update.chatId(), "Hãy hoàn tất bước hiện tại hoặc dùng /cancel."));
        };
    }

    public List<BotAction> handleMedia(MediaMessageUpdate update) {
        AdminSession savedSession = sessions.findByTelegramUserIdAndChatId(update.userId(), update.chatId()).orElse(null);
        if (savedSession != null && isExpired(savedSession)) {
            sessions.delete(savedSession);
            return List.of(new SendTextAction(update.chatId(), "Thao tác quản trị đã hết hạn. Vui lòng bắt đầu lại bằng /admin."));
        }
        AdminSession session = savedSession;
        if (session == null || session.getState() != AdminSessionState.WAITING_EPISODE_VIDEO) {
            return List.of(new SendTextAction(update.chatId(), "Chưa có tập nào đang chờ video. Dùng /admin để bắt đầu."));
        }
        IncomingMedia media = update.media();
        if (media == null || !StringUtils.hasText(media.fileId())) {
            return List.of(new SendTextAction(update.chatId(), "Không đọc được metadata video. Hãy gửi lại video."));
        }
        Map<String, Object> context = readContext(session.getContextJson());
        long episodeId = number(context, "episodeId");
        long seasonId = number(context, "seasonId");
        catalog.attachMedia(new AttachMediaCommand(episodeId, media.fileId(), media.fileUniqueId(), update.chatId(),
                update.messageId(), media.fileName(), media.mimeType(), media.fileSize(), media.durationSeconds(),
                media.width(), media.height()));
        sessions.delete(session);
        return List.of(new SendTextAction(update.chatId(), "Video đã được gắn vào tập phim ✅",
                List.of(List.of(new InlineButton("✅ Publish Episode", callbacks.admin("publish_episode", episodeId))),
                        List.of(new InlineButton("➕ Thêm Episode tiếp theo", callbacks.admin("add_episode", seasonId))))));
    }

    @Scheduled(fixedDelay = 10_800_000)
    public void removeExpiredSessions() {
        sessions.deleteByExpiresAtBefore(now());
    }

    private List<BotAction> createMovie(AdminSession session, long chatId, String name) {
        long movieId = catalog.createMovie(new CreateMovieCommand(name, null, null));
        sessions.delete(session);
        return List.of(new SendTextAction(chatId, "Đã tạo phim: " + name + "\nTrạng thái: DRAFT",
                List.of(List.of(new InlineButton("➕ Thêm Season", callbacks.admin("add_season", movieId))),
                        List.of(new InlineButton("✅ Publish phim", callbacks.admin("publish_movie", movieId))))));
    }

    private List<BotAction> searchMovies(AdminSession session, long chatId, String keyword) {
        List<MovieDetails> results = catalogQuery.searchMoviesForAdmin(keyword, 10);
        sessions.delete(session);
        if (results.isEmpty()) return List.of(new SendTextAction(chatId, "Không tìm thấy phim phù hợp."));
        List<List<InlineButton>> keyboard = results.stream()
                .map(movie -> List.of(new InlineButton(movie.name() + " (" + movie.status() + ")",
                        callbacks.admin("manage_movie", movie.id()))))
                .toList();
        return List.of(new SendTextAction(chatId, "Chọn phim để quản lý:", keyboard));
    }

    private List<BotAction> createSeason(AdminSession session, Map<String, Object> context, long chatId, String text) {
        int seasonNumber;
        try {
            seasonNumber = parseNonNegative(text, "Số season phải là số nguyên lớn hơn hoặc bằng 0.");
        } catch (IllegalArgumentException exception) {
            touch(session, context);
            return List.of(new SendTextAction(chatId, exception.getMessage()));
        }
        long movieId = number(context, "movieId");
        long seasonId = catalog.createSeason(new CreateSeasonCommand(movieId, seasonNumber, null));
        sessions.delete(session);
        return List.of(new SendTextAction(chatId, "Đã tạo Season " + seasonNumber + " ở trạng thái DRAFT.",
                List.of(List.of(new InlineButton("➕ Thêm Episode", callbacks.admin("add_episode", seasonId))),
                        List.of(new InlineButton("✅ Publish Season", callbacks.admin("publish_season", seasonId))))));
    }

    private List<BotAction> acceptEpisodeNumber(AdminSession session, Map<String, Object> context, long chatId, String text) {
        int episodeNumber;
        try {
            episodeNumber = parseNonNegative(text, "Số tập phải là số nguyên lớn hơn hoặc bằng 0.");
        } catch (IllegalArgumentException exception) {
            touch(session, context);
            return List.of(new SendTextAction(chatId, exception.getMessage()));
        }
        context.put("episodeNumber", episodeNumber);
        touch(session, context, AdminSessionState.WAITING_EPISODE_NAME);
        return List.of(new SendTextAction(chatId, "Nhập tên tập (có thể gửi dấu - nếu không có tên):"));
    }

    private List<BotAction> createEpisode(AdminSession session, Map<String, Object> context, long chatId, String text) {
        long seasonId = number(context, "seasonId");
        int episodeNumber = Math.toIntExact(number(context, "episodeNumber"));
        String name = "-".equals(text) ? null : text;
        long episodeId = catalog.createEpisode(new CreateEpisodeCommand(seasonId, episodeNumber, name, null));
        context.put("episodeId", episodeId);
        touch(session, context, AdminSessionState.WAITING_EPISODE_VIDEO);
        String displayName = name == null ? "Tập " + episodeNumber : name;
        return List.of(new SendTextAction(chatId, "Hãy gửi video cho Episode " + episodeNumber + " - " + displayName + "."));
    }

    private void startSession(long userId, long chatId, AdminFlow flow, AdminSessionState state, Map<String, Object> context) {
        LocalDateTime expiresAt = now().plusMinutes(SESSION_MINUTES);
        sessions.findByTelegramUserIdAndChatId(userId, chatId)
                .ifPresentOrElse(session -> session.update(flow, state, context, expiresAt),
                        () -> sessions.save(new AdminSession(userId, chatId, flow, state, context, expiresAt)));
    }

    private AdminSession activeSession(long userId, long chatId) {
        AdminSession session = sessions.findByTelegramUserIdAndChatId(userId, chatId).orElse(null);
        if (session != null && isExpired(session)) {
            sessions.delete(session);
            return null;
        }
        return session;
    }

    private boolean isExpired(AdminSession session) {
        return session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now());
    }

    private void touch(AdminSession session, Map<String, Object> context) {
        touch(session, context, session.getState());
    }

    private void touch(AdminSession session, Map<String, Object> context, AdminSessionState state) {
        session.update(session.getFlow(), state, context, now().plusMinutes(SESSION_MINUTES));
    }

    private int parseNonNegative(String value, String error) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(error);
        }
    }

    private long number(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value instanceof Number number) return number.longValue();
        throw new IllegalArgumentException("Thông tin flow bị thiếu. Hãy bắt đầu lại bằng /admin.");
    }

    private Map<String, Object> readContext(Map<String, Object> context) {
        return context == null ? new HashMap<>() : new HashMap<>(context);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}

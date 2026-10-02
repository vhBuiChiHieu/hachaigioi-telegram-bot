package com.acme.moviebot.bot.internal.admin;

import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.internal.MovieDetailsPresenter;
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
import com.acme.moviebot.catalog.CatalogViews.EpisodeDetails;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MoviePage;
import com.acme.moviebot.catalog.CatalogViews.SeasonDetails;
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
    private static final int MOVIES_PAGE_SIZE = 20;
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
        return List.of(new SendTextAction(chatId, "Nhập tên Việt của phim:"));
    }

    public List<BotAction> startSeason(long userId, long chatId, long movieId) {
        startSession(userId, chatId, AdminFlow.CREATE_SEASON, AdminSessionState.WAITING_SEASON_NUMBER,
                Map.of("movieId", movieId));
        return List.of(new SendTextAction(chatId, "Nhập số season (từ 1 trở lên):"));
    }

    public List<BotAction> startEpisode(long userId, long chatId, long seasonId) {
        long episodeId = catalog.createEpisode(new CreateEpisodeCommand(seasonId));
        startSession(userId, chatId, AdminFlow.CREATE_EPISODE, AdminSessionState.WAITING_EPISODE_VIDEO,
                Map.of("seasonId", seasonId, "episodeId", episodeId));
        return List.of(new SendTextAction(chatId, "Hãy gửi video cho phần phim mới."));
    }

    public List<BotAction> startSearch(long userId, long chatId) {
        startSession(userId, chatId, AdminFlow.SEARCH_MOVIES, AdminSessionState.WAITING_MOVIE_SEARCH, Map.of());
        return List.of(new SendTextAction(chatId, "Nhập tên hoặc từ khóa phim cần tìm:"));
    }

    public List<BotAction> showMovies(long chatId, int page) {
        MoviePage moviePage = catalogQuery.findMoviesForAdmin(page, MOVIES_PAGE_SIZE);
        if (moviePage.movies().isEmpty() && page == 0) {
            return List.of(new SendTextAction(chatId, "Chưa có phim nào."));
        }

        List<List<InlineButton>> keyboard = new ArrayList<>();
        for (MovieDetails movie : moviePage.movies()) {
            keyboard.add(List.of(new InlineButton(movieButtonLabel(movie), callbacks.admin("manage_movie", movie.id()))));
        }

        List<InlineButton> navigation = new ArrayList<>();
        if (page > 0) {
            navigation.add(new InlineButton("⬅️ Trang trước", callbacks.admin("list_movies", page - 1)));
        }
        if (moviePage.hasNext()) {
            navigation.add(new InlineButton("Trang sau ➡️", callbacks.admin("list_movies", page + 1)));
        }
        if (!navigation.isEmpty()) {
            keyboard.add(navigation);
        }

        return List.of(new SendTextAction(chatId, "Danh sách phim · Trang " + (page + 1), keyboard));
    }

    public List<BotAction> showMovieManagement(long chatId, long movieId) {
        return showMovieManagement(chatId, movieId, null);
    }

    public List<BotAction> showMovieManagement(long chatId, long movieId, String notice) {
        MovieDetails movie = catalogQuery.findMovie(movieId).orElse(null);
        if (movie == null) return List.of(new SendTextAction(chatId, "Không tìm thấy phim."));

        List<SeasonDetails> seasons = catalogQuery.findSeasonsForAdmin(movieId);
        List<List<InlineButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(new InlineButton("➕ Thêm Season", callbacks.admin("add_season", movieId))));
        for (SeasonDetails season : seasons) {
            String label = "Season " + season.seasonNumber() + " (" + season.status() + ")";
            keyboard.add(List.of(new InlineButton(label, callbacks.admin("manage_season", season.id()))));
        }
        keyboard.add(List.of(movieStatusButton(movie)));

        String seasonPrompt = seasons.isEmpty()
                ? "Danh sách mùa:\nChưa có mùa nào."
                : "Danh sách mùa:\nChọn mùa để quản lý.";
        return MovieDetailsPresenter.present(chatId, movie, notice, seasonPrompt, keyboard);
    }

    public List<BotAction> showSeasonManagement(long chatId, long seasonId) {
        return showSeasonManagement(chatId, seasonId, null);
    }

    public List<BotAction> showSeasonManagement(long chatId, long seasonId, String notice) {
        SeasonDetails season = catalogQuery.findSeasonForAdmin(seasonId).orElse(null);
        if (season == null) return List.of(new SendTextAction(chatId, "Không tìm thấy mùa phim."));

        List<EpisodeDetails> episodes = catalogQuery.findEpisodesForAdmin(seasonId);
        List<List<InlineButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(new InlineButton("➕ Thêm phần phim", callbacks.admin("add_episode", seasonId))));
        keyboard.add(List.of(new InlineButton("⬅️ Quay lại danh sách mùa",
                callbacks.admin("manage_movie", season.movieId()))));
        keyboard.add(List.of(seasonStatusButton(season)));

        StringBuilder text = new StringBuilder();
        appendNotice(text, notice);
        text.append("Season ").append(season.seasonNumber()).append(" (").append(season.status()).append(")")
                .append("\nCác tập gốc: ").append(season.originalEpisodeCount())
                .append("\n\nDanh sách phần phim:");
        if (episodes.isEmpty()) {
            text.append("\nChưa có phần phim nào.");
        } else {
            for (EpisodeDetails episode : episodes) {
                text.append("\nPhần ").append(episode.partNumber()).append(" (").append(episode.status()).append(")");
            }
        }
        return List.of(new SendTextAction(chatId, text.toString(), keyboard));
    }

    private InlineButton movieStatusButton(MovieDetails movie) {
        return switch (movie.status()) {
            case "DRAFT" -> new InlineButton("✅ Publish phim", callbacks.admin("publish_movie", movie.id()));
            case "PUBLISHED" -> new InlineButton("📦 Lưu trữ phim", callbacks.admin("archive_movie", movie.id()));
            default -> new InlineButton("♻️ Khôi phục phim", callbacks.admin("publish_movie", movie.id()));
        };
    }

    private InlineButton seasonStatusButton(SeasonDetails season) {
        return switch (season.status()) {
            case "DRAFT" -> new InlineButton("✅ Publish Season", callbacks.admin("publish_season", season.id()));
            case "PUBLISHED" -> new InlineButton("📦 Lưu trữ Season", callbacks.admin("archive_season", season.id()));
            default -> new InlineButton("♻️ Khôi phục Season", callbacks.admin("publish_season", season.id()));
        };
    }

    private void appendNotice(StringBuilder text, String notice) {
        if (notice != null && !notice.isBlank()) text.append(notice).append("\n\n");
    }

    private String movieButtonLabel(MovieDetails movie) {
        String prefix = movie.id() + " · ";
        int availableNameLength = 64 - prefix.codePointCount(0, prefix.length());
        String name = movie.vietnameseName();
        if (name.codePointCount(0, name.length()) > availableNameLength) {
            name = name.substring(0, name.offsetByCodePoints(0, availableNameLength - 1)) + "…";
        }
        return prefix + name;
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
            case WAITING_MOVIE_NAME -> acceptVietnameseName(session, context, update.chatId(), text);
            case WAITING_MOVIE_CHINESE_NAME -> acceptChineseName(session, context, update.chatId(), text);
            case WAITING_MOVIE_DESCRIPTION -> acceptDescription(session, context, update.chatId(), text);
            case WAITING_MOVIE_FULL -> acceptFull(session, context, update.chatId(), text);
            case WAITING_MOVIE_THUMBNAIL -> "-".equals(text)
                    ? createMovie(session, context, update.chatId(), null)
                    : List.of(new SendTextAction(update.chatId(), "Gửi ảnh thumbnail hoặc dấu - để bỏ qua."));
            case WAITING_MOVIE_SEARCH -> searchMovies(session, update.chatId(), text);
            case WAITING_SEASON_NUMBER -> acceptSeasonNumber(session, context, update.chatId(), text);
            case WAITING_SEASON_EPISODE_COUNT -> createSeason(session, context, update.chatId(), text);
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
        if (session == null) {
            return List.of(new SendTextAction(update.chatId(), "Chưa có phần phim nào đang chờ video. Dùng /admin để bắt đầu."));
        }
        IncomingMedia media = update.media();
        if (media == null || !StringUtils.hasText(media.fileId())) {
            return List.of(new SendTextAction(update.chatId(), "Không đọc được metadata ảnh/video. Hãy gửi lại."));
        }
        if (session.getState() == AdminSessionState.WAITING_MOVIE_THUMBNAIL) {
            if (media.mimeType() == null || !media.mimeType().startsWith("image/")) {
                return List.of(new SendTextAction(update.chatId(), "Hãy gửi ảnh thumbnail hoặc dấu - để bỏ qua."));
            }
            return createMovie(session, readContext(session.getContextJson()), update.chatId(), media.fileId());
        }
        if (session.getState() != AdminSessionState.WAITING_EPISODE_VIDEO) {
            return List.of(new SendTextAction(update.chatId(), "Chưa có ảnh thumbnail hoặc phần phim nào đang chờ. Dùng /admin để bắt đầu."));
        }
        if (media.mimeType() != null && media.mimeType().startsWith("image/")) {
            return List.of(new SendTextAction(update.chatId(), "Hãy gửi video cho phần phim này."));
        }
        Map<String, Object> context = readContext(session.getContextJson());
        long episodeId = number(context, "episodeId");
        long seasonId = number(context, "seasonId");
        catalog.attachMedia(new AttachMediaCommand(episodeId, media.fileId(), media.fileUniqueId(), update.chatId(),
                update.messageId(), media.fileName(), media.mimeType(), media.fileSize(), media.durationSeconds(),
                media.width(), media.height()));
        sessions.delete(session);
        return List.of(new SendTextAction(update.chatId(), "Video đã được gắn vào phần phim ✅",
                List.of(List.of(new InlineButton("✅ Publish phần phim", callbacks.admin("publish_episode", episodeId))),
                        List.of(new InlineButton("➕ Thêm phần tiếp theo", callbacks.admin("add_episode", seasonId))))));
    }

    @Scheduled(fixedDelay = 10_800_000)
    public void removeExpiredSessions() {
        sessions.deleteByExpiresAtBefore(now());
    }

    private List<BotAction> acceptVietnameseName(AdminSession session, Map<String, Object> context, long chatId, String name) {
        context.put("vietnameseName", name);
        touch(session, context, AdminSessionState.WAITING_MOVIE_CHINESE_NAME);
        return List.of(new SendTextAction(chatId, "Nhập tên Trung (gửi dấu - nếu không có):"));
    }

    private List<BotAction> acceptChineseName(AdminSession session, Map<String, Object> context, long chatId, String name) {
        context.put("chineseName", optional(name));
        touch(session, context, AdminSessionState.WAITING_MOVIE_DESCRIPTION);
        return List.of(new SendTextAction(chatId, "Nhập giới thiệu (gửi dấu - nếu không có):"));
    }

    private List<BotAction> acceptDescription(AdminSession session, Map<String, Object> context, long chatId, String description) {
        context.put("description", optional(description));
        touch(session, context, AdminSessionState.WAITING_MOVIE_FULL);
        return List.of(new SendTextAction(chatId, "Phim đã trọn bộ chưa? Gửi true hoặc false:"));
    }

    private List<BotAction> acceptFull(AdminSession session, Map<String, Object> context, long chatId, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            touch(session, context);
            return List.of(new SendTextAction(chatId, "Chỉ nhập true hoặc false cho trạng thái trọn bộ."));
        }
        context.put("full", Boolean.parseBoolean(value));
        touch(session, context, AdminSessionState.WAITING_MOVIE_THUMBNAIL);
        return List.of(new SendTextAction(chatId, "Gửi ảnh thumbnail hoặc dấu - để bỏ qua:"));
    }

    private List<BotAction> createMovie(AdminSession session, Map<String, Object> context, long chatId, String thumbnailFileId) {
        String vietnameseName = (String) context.get("vietnameseName");
        String chineseName = (String) context.get("chineseName");
        String description = (String) context.get("description");
        boolean full = Boolean.TRUE.equals(context.get("full"));
        long movieId = catalog.createMovie(new CreateMovieCommand(vietnameseName, chineseName, thumbnailFileId, description, full));
        sessions.delete(session);
        return List.of(new SendTextAction(chatId, "Đã tạo phim: " + vietnameseName + "\nTrạng thái: DRAFT",
                List.of(List.of(new InlineButton("➕ Thêm Season", callbacks.admin("add_season", movieId))),
                        List.of(new InlineButton("✅ Publish phim", callbacks.admin("publish_movie", movieId))))));
    }

    private List<BotAction> searchMovies(AdminSession session, long chatId, String keyword) {
        List<MovieDetails> results = catalogQuery.searchMoviesForAdmin(keyword, 10);
        sessions.delete(session);
        if (results.isEmpty()) return List.of(new SendTextAction(chatId, "Không tìm thấy phim phù hợp."));
        List<List<InlineButton>> keyboard = results.stream()
                .map(movie -> List.of(new InlineButton(movie.vietnameseName() + " (" + movie.status() + ")",
                        callbacks.admin("manage_movie", movie.id()))))
                .toList();
        return List.of(new SendTextAction(chatId, "Chọn phim để quản lý:", keyboard));
    }

    private List<BotAction> acceptSeasonNumber(AdminSession session, Map<String, Object> context, long chatId, String text) {
        int seasonNumber;
        try {
            seasonNumber = parseNonNegative(text, "Số season phải là số nguyên lớn hơn hoặc bằng 1.");
            if (seasonNumber < 1) throw new IllegalArgumentException("Số season phải lớn hơn hoặc bằng 1.");
        } catch (IllegalArgumentException exception) {
            touch(session, context);
            return List.of(new SendTextAction(chatId, exception.getMessage()));
        }
        context.put("seasonNumber", seasonNumber);
        touch(session, context, AdminSessionState.WAITING_SEASON_EPISODE_COUNT);
        return List.of(new SendTextAction(chatId, "Nhập tổng số tập gốc của season (bên Trung):"));
    }

    private List<BotAction> createSeason(AdminSession session, Map<String, Object> context, long chatId, String text) {
        int originalEpisodeCount;
        try {
            originalEpisodeCount = parseNonNegative(text, "Tổng số tập gốc phải là số nguyên lớn hơn hoặc bằng 0.");
        } catch (IllegalArgumentException exception) {
            touch(session, context);
            return List.of(new SendTextAction(chatId, exception.getMessage()));
        }
        long movieId = number(context, "movieId");
        int seasonNumber = Math.toIntExact(number(context, "seasonNumber"));
        long seasonId = catalog.createSeason(new CreateSeasonCommand(movieId, seasonNumber, originalEpisodeCount));
        sessions.delete(session);
        return List.of(new SendTextAction(chatId, "Đã tạo Season " + seasonNumber + " ở trạng thái DRAFT.",
                List.of(List.of(new InlineButton("➕ Thêm phần phim", callbacks.admin("add_episode", seasonId))),
                        List.of(new InlineButton("✅ Publish Season", callbacks.admin("publish_season", seasonId))))));
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

    private String optional(String value) {
        return "-".equals(value) ? null : value;
    }

    private Map<String, Object> readContext(Map<String, Object> context) {
        return context == null ? new HashMap<>() : new HashMap<>(context);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}

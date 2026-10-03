package com.acme.moviebot.bot.internal.admin;

import com.acme.moviebot.access.AccessControl;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.MediaMessageUpdate;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogManagement;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdminCommandHandler {

    private final AccessControl accessControl;
    private final AdminConversationService conversations;
    private final CatalogManagement catalog;

    public AdminCommandHandler(AccessControl accessControl, AdminConversationService conversations, CatalogManagement catalog) {
        this.accessControl = accessControl;
        this.conversations = conversations;
        this.catalog = catalog;
    }

    public List<BotAction> entry(long userId, long chatId) {
        if (!accessControl.isAdmin(userId)) return unauthorized(chatId);
        return List.of(new SendTextAction(chatId, "Quản lý nội dung",
                List.of(List.of(new InlineButton("➕ Thêm phim", "a:add_movie")),
                        List.of(new InlineButton("🎞 Danh sách phim", "a:list_movies")),
                        List.of(new InlineButton("🔎 Tìm phim", "a:search_movie")),
                        List.of(new InlineButton("❌ Hủy thao tác", "a:cancel")))));
    }

    public List<BotAction> handleText(TextMessageUpdate update) {
        if (!accessControl.isAdmin(update.userId())) return unauthorized(update.chatId());
        return conversations.handleText(update);
    }

    public List<BotAction> handleMedia(MediaMessageUpdate update) {
        if (!accessControl.isAdmin(update.userId())) return unauthorized(update.chatId());
        return conversations.handleMedia(update);
    }

    public List<BotAction> handleCallback(CallbackUpdate update, String action, Long id) {
        if (!accessControl.isAdmin(update.userId())) return unauthorized(update.chatId());
        return switch (action) {
            case "add_movie" -> conversations.startMovie(update.userId(), update.chatId());
            case "list_movies" -> showMovies(update.chatId(), id);
            case "search_movie" -> conversations.startSearch(update.userId(), update.chatId());
            case "manage_movie" -> conversations.showMovieManagement(update.chatId(), requireId(id));
            case "manage_seasons" -> conversations.expandMovieSeasons(update.chatId(), update.messageId(), requireId(id));
            case "list_seasons" -> conversations.showMovieSeasons(update.chatId(), requireId(id));
            case "manage_season" -> conversations.showSeasonManagement(update.chatId(), requireId(id));
            case "add_season" -> conversations.startSeason(update.userId(), update.chatId(), requireId(id));
            case "add_episode" -> conversations.startEpisode(update.userId(), update.chatId(), requireId(id));
            case "cancel" -> conversations.cancel(update.userId(), update.chatId());
            case "publish_movie" -> publishMovie(update.chatId(), requireId(id));
            case "archive_movie" -> archiveMovie(update.chatId(), requireId(id));
            case "publish_season" -> publishSeason(update.chatId(), requireId(id));
            case "archive_season" -> archiveSeason(update.chatId(), requireId(id));
            case "publish_episode" -> publishEpisode(update.chatId(), requireId(id));
            default -> List.of(new SendTextAction(update.chatId(), "Lựa chọn quản trị không hợp lệ."));
        };
    }

    public boolean hasSession(long userId, long chatId) {
        return accessControl.isAdmin(userId) && conversations.hasSession(userId, chatId);
    }

    private List<BotAction> publishMovie(long chatId, long id) {
        catalog.publishMovie(id);
        return conversations.showMovieManagement(chatId, id, "Phim đã được publish ✅");
    }

    private List<BotAction> archiveMovie(long chatId, long id) {
        catalog.archiveMovie(id);
        return conversations.showMovieManagement(chatId, id, "Phim đã được lưu trữ.");
    }

    private List<BotAction> publishSeason(long chatId, long id) {
        catalog.publishSeason(id);
        return conversations.showSeasonManagement(chatId, id, "Season đã được publish ✅");
    }

    private List<BotAction> archiveSeason(long chatId, long id) {
        catalog.archiveSeason(id);
        return conversations.showSeasonManagement(chatId, id, "Season đã được lưu trữ.");
    }

    private List<BotAction> publishEpisode(long chatId, long id) {
        catalog.publishEpisode(id);
        return List.of(new SendTextAction(chatId, "Episode đã được publish ✅"));
    }

    private long requireId(Long id) {
        if (id == null) throw new IllegalArgumentException("Thiếu mã nội dung.");
        return id;
    }

    private List<BotAction> showMovies(long chatId, Long page) {
        long pageNumber = page == null ? 0 : page;
        if (pageNumber < 0 || pageNumber > Integer.MAX_VALUE) {
            return List.of(new SendTextAction(chatId, "Trang danh sách phim không hợp lệ."));
        }
        return conversations.showMovies(chatId, (int) pageNumber);
    }

    private List<BotAction> unauthorized(long chatId) {
        return List.of(new SendTextAction(chatId, "Bạn không có quyền thực hiện thao tác quản trị này."));
    }
}

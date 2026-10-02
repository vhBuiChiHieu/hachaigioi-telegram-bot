package com.acme.moviebot.bot.internal.user;

import com.acme.moviebot.bot.internal.CallbackDataCodec;
import com.acme.moviebot.bot.internal.CallbackDataCodec.DecodedCallback;
import com.acme.moviebot.bot.internal.MovieDetailsPresenter;
import com.acme.moviebot.bot.model.AnswerCallbackAction;
import com.acme.moviebot.bot.model.BotAction;
import com.acme.moviebot.bot.model.CallbackUpdate;
import com.acme.moviebot.bot.model.InlineButton;
import com.acme.moviebot.bot.model.SendTextAction;
import com.acme.moviebot.bot.model.SendVideoAction;
import com.acme.moviebot.bot.model.TextMessageUpdate;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeSummary;
import com.acme.moviebot.catalog.CatalogViews.MovieDetails;
import com.acme.moviebot.catalog.CatalogViews.MovieSummary;
import com.acme.moviebot.catalog.CatalogViews.SeasonSummary;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserCommandHandler {

    private static final int PAGE_SIZE = 10;
    private final CatalogQuery catalog;
    private final CallbackDataCodec callbacks;

    public UserCommandHandler(CatalogQuery catalog, CallbackDataCodec callbacks) {
        this.catalog = catalog;
        this.callbacks = callbacks;
    }

    public List<BotAction> handleCommand(TextMessageUpdate update, String command, String argument) {
        return switch (command) {
            case "/start" -> List.of(new SendTextAction(update.chatId(),
                    "Xin chào!\n\nBạn có thể tìm phim bằng:\n/find <tên phim>"));
            case "/help" -> List.of(new SendTextAction(update.chatId(),
                    "Tìm phim bằng /find <tên phim>. Chọn phim, season và phần để nhận video."));
            case "/find" -> search(update.chatId(), argument, 0);
            default -> List.of(new SendTextAction(update.chatId(), "Lệnh chưa được hỗ trợ. Dùng /help để xem hướng dẫn."));
        };
    }

    public List<BotAction> handleCallback(CallbackUpdate update, DecodedCallback callback) {
        List<BotAction> actions = new ArrayList<>();
        actions.add(new AnswerCallbackAction(update.callbackQueryId(), "", false));
        try {
            switch (callback.type()) {
                case "m" -> showSeasons(update.chatId(), callback.longArgument(0), 0, actions);
                case "s" -> showEpisodes(update.chatId(), callback.longArgument(0), 0, actions);
                case "e" -> sendEpisode(update.chatId(), callback.longArgument(0), actions);
                case "mp" -> actions.addAll(search(update.chatId(), callback.stringArgument(1), callback.intArgument(0)));
                case "sp" -> showSeasons(update.chatId(), callback.longArgument(0), callback.intArgument(1), actions);
                case "ep" -> showEpisodes(update.chatId(), callback.longArgument(0), callback.intArgument(1), actions);
                default -> actions.add(new SendTextAction(update.chatId(), "Lựa chọn không hợp lệ hoặc đã hết hạn."));
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            actions.add(new SendTextAction(update.chatId(), "Lựa chọn không hợp lệ hoặc nội dung không còn khả dụng."));
        }
        return actions;
    }

    private List<BotAction> search(long chatId, String keyword, int page) {
        if (!StringUtils.hasText(keyword)) {
            return List.of(new SendTextAction(chatId, "Nhập tên phim sau lệnh /find. Ví dụ: /find bố già"));
        }
        List<MovieSummary> results = catalog.searchMovies(keyword, (page + 1) * PAGE_SIZE + 1);
        int start = Math.min(page * PAGE_SIZE, results.size());
        int end = Math.min(start + PAGE_SIZE, results.size());
        if (start >= end) {
            return List.of(new SendTextAction(chatId,
                    "Không tìm thấy phim phù hợp. /find chỉ hiển thị phim đã publish."));
        }
        List<List<InlineButton>> keyboard = new ArrayList<>();
        for (MovieSummary movie : results.subList(start, end)) {
            keyboard.add(List.of(new InlineButton(movie.vietnameseName(), callbacks.movie(movie.id()))));
        }
        String text = "Kết quả tìm kiếm:";
        if (results.size() > end) {
            String pageCallback = callbacks.movieSearchPage(keyword, Math.min(page + 1, 999));
            if (!pageCallback.isBlank()) {
                keyboard.add(List.of(new InlineButton("Xem thêm kết quả", pageCallback)));
            }
        }
        return List.of(new SendTextAction(chatId, text, keyboard));
    }

    private void showSeasons(long chatId, long movieId, int page, List<BotAction> actions) {
        MovieDetails movie = catalog.findMovie(movieId).filter(item -> "PUBLISHED".equals(item.status())).orElse(null);
        if (movie == null) {
            actions.add(new SendTextAction(chatId, "Không tìm thấy phim đã phát hành."));
            return;
        }

        List<SeasonSummary> seasons = catalog.findPublishedSeasons(movieId);
        if (seasons.isEmpty()) {
            if (page == 0) {
                actions.addAll(MovieDetailsPresenter.present(chatId, movie, null,
                        "📚 Phim chưa có mùa được phát hành.", List.of()));
            } else {
                actions.add(new SendTextAction(chatId, "Không còn season trong trang này."));
            }
            return;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, seasons.size());
        if (start >= end) {
            actions.add(new SendTextAction(chatId, "Không còn season trong trang này."));
            return;
        }
        List<List<InlineButton>> keyboard = new ArrayList<>();
        for (SeasonSummary season : seasons.subList(start, end)) {
            String label = "Season " + season.seasonNumber() + " (" + season.originalEpisodeCount() + " tập gốc)";
            keyboard.add(List.of(new InlineButton(label, callbacks.season(season.id()))));
        }
        if (seasons.size() > end) {
            keyboard.add(List.of(new InlineButton("Trang tiếp theo", callbacks.seasonsPage(movieId, page + 1))));
        }

        if (page == 0) {
            actions.addAll(MovieDetailsPresenter.present(chatId, movie, null, "📚 Chọn mùa:", keyboard));
        } else {
            actions.add(new SendTextAction(chatId,
                    "🎬 " + movie.vietnameseName() + "\n\n📚 Chọn mùa (trang " + (page + 1) + "):", keyboard));
        }
    }

    private void showEpisodes(long chatId, long seasonId, int page, List<BotAction> actions) {
        List<EpisodeSummary> episodes = catalog.findPublishedEpisodes(seasonId);
        if (episodes.isEmpty()) {
            actions.add(new SendTextAction(chatId, "Season này chưa có phần phim được phát hành."));
            return;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, episodes.size());
        if (start >= end) {
            actions.add(new SendTextAction(chatId, "Không còn phần phim trong trang này."));
            return;
        }
        List<List<InlineButton>> keyboard = new ArrayList<>();
        for (EpisodeSummary episode : episodes.subList(start, end)) {
            String label = "Phần " + episode.partNumber();
            keyboard.add(List.of(new InlineButton(label, callbacks.episode(episode.id()))));
        }
        if (episodes.size() > end) {
            keyboard.add(List.of(new InlineButton("Trang tiếp theo", callbacks.episodesPage(seasonId, page + 1))));
        }
        actions.add(new SendTextAction(chatId, "Chọn phần phim:", keyboard));
    }

    private void sendEpisode(long chatId, long episodeId, List<BotAction> actions) {
        catalog.findEpisodeMedia(episodeId).ifPresentOrElse(
                media -> actions.add(new SendVideoAction(chatId, media.providerFileId(),
                        media.movieName() + " - Season " + media.seasonNumber() + " - Phần " + media.partNumber())),
                () -> actions.add(new SendTextAction(chatId, "Video của phần phim này chưa khả dụng.")));
    }
}

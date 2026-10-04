package com.acme.moviebot.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.moviebot.catalog.CatalogCommands.AttachLinkCommand;
import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateMovieCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateSeasonCommand;
import com.acme.moviebot.catalog.CatalogConflictException;
import com.acme.moviebot.catalog.CatalogManagement;
import com.acme.moviebot.catalog.CatalogQuery;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaType;
import com.acme.moviebot.catalog.CatalogViews.EpisodeMediaView;
import com.acme.moviebot.catalog.internal.persistence.MediaAssetRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(showSql = false, properties = {"spring.config.import=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CatalogManagementService.class, CatalogQueryService.class,
        CatalogEpisodeContentIntegrationTest.LegacyCatalogMigration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class CatalogEpisodeContentIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private CatalogManagement catalog;
    @Autowired private CatalogQuery query;
    @Autowired private MediaAssetRepository mediaAssets;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesThePublishedVideoThatExistedBeforeLinksWereSupported() {
        assertThat(query.findEpisodeMedia(1L)).contains(
                new EpisodeMediaView(1L, "Phim cũ", 1, 1, EpisodeMediaType.VIDEO, "legacy-video", null));
    }

    @Test
    void linkIsPreviewableInDraftAndPublicOnlyWhileThePartAndParentsArePublished() {
        long movieId = catalog.createMovie(new CreateMovieCommand("Phim", null, null, null, false));
        long seasonId = catalog.createSeason(new CreateSeasonCommand(movieId, 2, 20));
        long episodeId = catalog.createEpisode(new CreateEpisodeCommand(seasonId));
        String url = "https://t.me/c/123/456?single";
        long mediaId = catalog.attachLink(new AttachLinkCommand(episodeId, url));
        EpisodeMediaView view = new EpisodeMediaView(episodeId, "Phim", 2, 1, EpisodeMediaType.LINK, null, url);

        assertThat(mediaId).isPositive();
        assertThat(query.findEpisodeMediaForAdmin(episodeId)).contains(view);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
        catalog.publishEpisode(episodeId);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
        catalog.publishMovie(movieId);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
        catalog.publishSeason(seasonId);
        assertThat(query.findEpisodeMedia(episodeId)).contains(view);
        catalog.unpublishSeason(seasonId);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
        catalog.publishSeason(seasonId);
        catalog.unpublishEpisode(episodeId);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
        assertThat(query.findEpisodeMediaForAdmin(episodeId)).contains(view);
        assertThat(mediaAssets.findById(mediaId).orElseThrow().getStatus().name()).isEqualTo("DRAFT");
        catalog.publishEpisode(episodeId);
        assertThat(query.findEpisodeMedia(episodeId)).contains(view);
        catalog.archiveMovie(movieId);
        assertThat(query.findEpisodeMedia(episodeId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPartRejectsASecondSourceAndKeepsItsOriginalContent(boolean linkFirst) {
        long episodeId = newPart();
        if (linkFirst) catalog.attachLink(link(episodeId));
        else catalog.attachMedia(video(episodeId));

        assertThatThrownBy(() -> {
            if (linkFirst) catalog.attachMedia(video(episodeId));
            else catalog.attachLink(link(episodeId));
        }).isInstanceOf(CatalogConflictException.class).hasMessageContaining("đã có video hoặc link");

        var original = query.findEpisodeMediaForAdmin(episodeId).orElseThrow();
        assertThat(original.mediaType()).isEqualTo(linkFirst ? EpisodeMediaType.LINK : EpisodeMediaType.VIDEO);
        assertThat(original.providerFileId()).isEqualTo(linkFirst ? null : "video-file");
        assertThat(original.externalUrl()).isEqualTo(linkFirst ? "https://example.com/part" : null);
    }

    @Test
    void concurrentAttachmentsWithExistingReadSnapshotsStillStoreOnlyOneSource() throws Exception {
        long episodeId = newPart();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attachConcurrently(episodeId, true, ready, start));
            var second = executor.submit(() -> attachConcurrently(episodeId, false, ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("select count(*) from media_asset where episode_id = ?", Long.class, episodeId))
                .isEqualTo(1L);
    }

    @Test
    void databaseRejectsContentWithBothVideoAndLinkPayloads() {
        long episodeId = newPart();
        assertThatThrownBy(() -> jdbc.update("""
                insert into media_asset (episode_id, status, provider, media_type, provider_file_id,
                    external_url, created_at, updated_at)
                values (?, 'DRAFT', 'EXTERNAL', 'LINK', 'video-file', 'https://example.com/part', now(), now())
                """, episodeId)).isInstanceOf(DataAccessException.class).hasMessageContaining("chk_media_asset_content");
    }

    private boolean attachConcurrently(long episodeId, boolean link, CountDownLatch ready, CountDownLatch start) {
        try {
            return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
                // Simulate a caller that already read its admin session before attaching content.
                mediaAssets.count();
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Attachment gate timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                if (link) catalog.attachLink(link(episodeId));
                else catalog.attachMedia(video(episodeId));
                return true;
            }));
        } catch (CatalogConflictException exception) {
            return false;
        }
    }

    private long newPart() {
        long movieId = catalog.createMovie(new CreateMovieCommand("Phim", null, null, null, false));
        long seasonId = catalog.createSeason(new CreateSeasonCommand(movieId, 1, 20));
        return catalog.createEpisode(new CreateEpisodeCommand(seasonId));
    }

    private AttachLinkCommand link(long episodeId) {
        return new AttachLinkCommand(episodeId, "https://example.com/part");
    }

    private AttachMediaCommand video(long episodeId) {
        return new AttachMediaCommand(episodeId, "video-file", "unique-file", 7L, 99L,
                "part.mp4", "video/mp4", 100L, 10, 640, 480);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacyCatalogMigration {

        @Bean
        FlywayMigrationStrategy upgradeAnExistingCatalog() {
            return flyway -> {
                Flyway.configure().configuration(flyway.getConfiguration()).target("9").load().migrate();
                JdbcTemplate database = new JdbcTemplate(flyway.getConfiguration().getDataSource());
                database.update("""
                        insert into movie (id, vietnamese_name, search_name, status, created_at, updated_at)
                        values (1, 'Phim cũ', 'phim cu', 'PUBLISHED', now(), now())
                        """);
                database.update("""
                        insert into season (id, movie_id, season_number, original_episode_count, status, created_at, updated_at)
                        values (1, 1, 1, 20, 'PUBLISHED', now(), now())
                        """);
                database.update("""
                        insert into episode (id, season_id, status, created_at, updated_at)
                        values (1, 1, 'PUBLISHED', now(), now())
                        """);
                database.update("""
                        insert into media_asset (episode_id, status, provider, media_type, provider_file_id, created_at, updated_at)
                        values (1, 'PUBLISHED', 'TELEGRAM', 'VIDEO', 'legacy-video', now(), now())
                        """);
                flyway.migrate();
            };
        }
    }
}

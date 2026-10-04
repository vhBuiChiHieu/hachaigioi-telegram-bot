package com.acme.moviebot.catalog;

import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.AttachLinkCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateMovieCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateSeasonCommand;
import com.acme.moviebot.catalog.CatalogCommands.UpdateMovieDetailsCommand;

public interface CatalogManagement {

    long createMovie(CreateMovieCommand command);

    void updateMovieDetails(UpdateMovieDetailsCommand command);

    void updateMovieThumbnail(long movieId, String thumbnailFileId);

    void setMovieFull(long movieId, boolean full);

    long createSeason(CreateSeasonCommand command);

    long createEpisode(CreateEpisodeCommand command);

    long attachMedia(AttachMediaCommand command);

    long attachLink(AttachLinkCommand command);

    void publishMovie(long movieId);

    void publishSeason(long seasonId);

    void unpublishSeason(long seasonId);

    void archiveSeason(long seasonId);

    void publishEpisode(long episodeId);

    void unpublishEpisode(long episodeId);

    void archiveMovie(long movieId);
}

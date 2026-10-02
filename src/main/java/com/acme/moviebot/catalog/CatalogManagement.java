package com.acme.moviebot.catalog;

import com.acme.moviebot.catalog.CatalogCommands.AttachMediaCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateEpisodeCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateMovieCommand;
import com.acme.moviebot.catalog.CatalogCommands.CreateSeasonCommand;

public interface CatalogManagement {

    long createMovie(CreateMovieCommand command);

    long createSeason(CreateSeasonCommand command);

    long createEpisode(CreateEpisodeCommand command);

    long attachMedia(AttachMediaCommand command);

    void publishMovie(long movieId);

    void publishSeason(long seasonId);

    void publishEpisode(long episodeId);

    void archiveMovie(long movieId);
}

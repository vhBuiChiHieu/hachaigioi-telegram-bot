package com.acme.moviebot.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "season")
public class Season extends CatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "season_number", nullable = false)
    private int seasonNumber;

    @Column(name = "original_episode_count", nullable = false)
    private int originalEpisodeCount;

    protected Season() {
    }

    public Season(Movie movie, int seasonNumber, int originalEpisodeCount) {
        this.movie = movie;
        this.seasonNumber = seasonNumber;
        this.originalEpisodeCount = originalEpisodeCount;
    }

    public Long getId() { return id; }
    public Movie getMovie() { return movie; }
    public int getSeasonNumber() { return seasonNumber; }
    public int getOriginalEpisodeCount() { return originalEpisodeCount; }
}

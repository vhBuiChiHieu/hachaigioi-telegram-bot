package com.acme.moviebot.catalog.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "search_name", nullable = false, length = 255)
    private String searchName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "poster_file_id", length = 512)
    private String posterFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MovieStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MovieAlias> aliases = new ArrayList<>();

    protected Movie() {
    }

    public Movie(String slug, String name, String originalName, String searchName, String description) {
        this.slug = slug;
        this.name = name;
        this.originalName = originalName;
        this.searchName = searchName;
        this.description = description;
        this.status = MovieStatus.DRAFT;
    }

    public void publish() {
        status = MovieStatus.PUBLISHED;
    }

    public void archive() {
        status = MovieStatus.ARCHIVED;
    }

    public void addAlias(String alias, String normalizedAlias) {
        aliases.add(new MovieAlias(this, alias, normalizedAlias));
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getOriginalName() { return originalName; }
    public String getSearchName() { return searchName; }
    public String getDescription() { return description; }
    public MovieStatus getStatus() { return status; }
}

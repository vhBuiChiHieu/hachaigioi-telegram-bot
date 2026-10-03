package com.acme.moviebot.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movie")
public class Movie extends CatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vietnamese_name", nullable = false, length = 255)
    private String vietnameseName;

    @Column(name = "chinese_name", length = 255)
    private String chineseName;

    @Column(name = "search_name", nullable = false, length = 512)
    private String searchName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_file_id", length = 1024)
    private String thumbnailFileId;

    @Column(name = "is_full", nullable = false)
    private boolean full;

    protected Movie() {
    }

    public Movie(String vietnameseName, String chineseName, String searchName, String thumbnailFileId,
                 String description, boolean full) {
        this.vietnameseName = vietnameseName;
        this.chineseName = chineseName;
        this.searchName = searchName;
        this.thumbnailFileId = thumbnailFileId;
        this.description = description;
        this.full = full;
    }

    public void updateDetails(String chineseName, String vietnameseName, String searchName, String description) {
        this.chineseName = chineseName;
        this.vietnameseName = vietnameseName;
        this.searchName = searchName;
        this.description = description;
    }

    public void updateThumbnail(String thumbnailFileId) {
        this.thumbnailFileId = thumbnailFileId;
    }

    public void setFull(boolean full) {
        this.full = full;
    }

    public Long getId() { return id; }
    public String getVietnameseName() { return vietnameseName; }
    public String getChineseName() { return chineseName; }
    public String getSearchName() { return searchName; }
    public String getThumbnailFileId() { return thumbnailFileId; }
    public String getDescription() { return description; }
    public boolean isFull() { return full; }
}

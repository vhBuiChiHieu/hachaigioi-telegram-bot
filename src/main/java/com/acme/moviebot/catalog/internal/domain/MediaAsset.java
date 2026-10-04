package com.acme.moviebot.catalog.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_asset")
public class MediaAsset extends CatalogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 32)
    private MediaType mediaType;

    @Column(name = "provider_file_id", length = 1024)
    private String providerFileId;

    @Column(name = "external_url", length = 2048)
    private String externalUrl;

    @Column(name = "provider_unique_file_id", length = 512)
    private String providerUniqueFileId;

    @Column(name = "source_chat_id")
    private Long sourceChatId;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Integer width;
    private Integer height;

    protected MediaAsset() {
    }

    public MediaAsset(Episode episode, String fileId, String uniqueFileId, long chatId, long messageId,
                      String fileName, String mimeType, Long fileSize, Integer durationSeconds, Integer width, Integer height) {
        this.episode = episode;
        this.provider = MediaProvider.TELEGRAM;
        this.mediaType = MediaType.VIDEO;
        this.providerFileId = fileId;
        this.providerUniqueFileId = uniqueFileId;
        this.sourceChatId = chatId;
        this.sourceMessageId = messageId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
    }

    public Long getId() { return id; }
    public Episode getEpisode() { return episode; }
    public MediaType getMediaType() { return mediaType; }
    public String getProviderFileId() { return providerFileId; }
    public String getExternalUrl() { return externalUrl; }

    public static MediaAsset externalLink(Episode episode, String url) {
        MediaAsset asset = new MediaAsset();
        asset.episode = episode;
        asset.provider = MediaProvider.EXTERNAL;
        asset.mediaType = MediaType.LINK;
        asset.externalUrl = new ExternalLink(url).url();
        return asset;
    }
}

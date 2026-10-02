package com.acme.moviebot.bot.internal.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_session")
public class AdminSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private long chatId;

    @Column(nullable = false, length = 64)
    private String flow;

    @Column(nullable = false, length = 64)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "JSON")
    private Map<String, Object> contextJson;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AdminSession() {
    }

    public AdminSession(long telegramUserId, long chatId, AdminFlow flow, AdminSessionState state, Map<String, Object> contextJson,
                        LocalDateTime expiresAt) {
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        update(flow, state, contextJson, expiresAt);
    }

    public void update(AdminFlow flow, AdminSessionState state, Map<String, Object> contextJson, LocalDateTime expiresAt) {
        this.flow = flow.name();
        this.state = state.name();
        this.contextJson = contextJson;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public AdminFlow getFlow() { return AdminFlow.valueOf(flow); }
    public AdminSessionState getState() { return AdminSessionState.valueOf(state); }
    public Map<String, Object> getContextJson() { return contextJson; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}

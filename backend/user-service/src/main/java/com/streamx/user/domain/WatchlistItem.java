package com.streamx.user.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_watchlist_profile_title", columnNames = {"profile_id", "title_id"}),
        indexes = @Index(name = "idx_watchlist_profile", columnList = "profile_id"))
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "title_id", nullable = false)
    private UUID titleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "title_type", nullable = false)
    private TitleType titleType;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public WatchlistItem() {
    }

    public WatchlistItem(UUID profileId, UUID titleId, TitleType titleType) {
        this.profileId = profileId;
        this.titleId = titleId;
        this.titleType = titleType;
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getTitleId() {
        return titleId;
    }

    public void setTitleId(UUID titleId) {
        this.titleId = titleId;
    }

    public TitleType getTitleType() {
        return titleType;
    }

    public void setTitleType(TitleType titleType) {
        this.titleType = titleType;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}

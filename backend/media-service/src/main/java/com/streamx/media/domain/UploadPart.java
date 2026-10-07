package com.streamx.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "upload_parts", uniqueConstraints = @UniqueConstraint(columnNames = {"upload_id", "part_number"}))
public class UploadPart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Column(nullable = false)
    private long sizeBytes;

    private LocalDateTime receivedAt;

    public UploadPart() {
    }

    public UploadPart(UUID uploadId, int partNumber, long sizeBytes) {
        this.uploadId = uploadId;
        this.partNumber = partNumber;
        this.sizeBytes = sizeBytes;
        this.receivedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}

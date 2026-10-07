package com.team.blog.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * 사진({@code image}, 51의 14개 컬럼). 003은 프로필 용도만 만든다 — 썸네일 없음, 업로드 완료 전에는 {@code width} NULL.
 * 상태: presign → TEMP(width NULL) → complete 통과 → TEMP(width=256) → [저장] → ATTACHED → 교체·기본으로 → detached_at 기록.
 */
@Entity
@Table(name = "image")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private Long uploaderId;

    @Column(name = "storage_key", nullable = false, length = 255, updatable = false)
    private String storageKey;

    @Column(name = "thumb_storage_key", length = 255)
    private String thumbStorageKey;

    @Column(name = "original_name", nullable = false, length = 255, updatable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "thumb_size_bytes")
    private Integer thumbSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20, updatable = false)
    private ImagePurpose purpose;

    @Column(name = "detached_at")
    private Instant detachedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Image() {
        // JPA
    }

    private Image(long uploaderId, String storageKey, String originalName, ImageFormat format, int sizeBytes,
                  ImagePurpose purpose, Instant now) {
        this.uploaderId = uploaderId;
        this.storageKey = Objects.requireNonNull(storageKey);
        this.originalName = Objects.requireNonNull(originalName);
        this.contentType = format.contentType();
        this.sizeBytes = sizeBytes;
        this.status = ImageStatus.TEMP;
        this.purpose = Objects.requireNonNull(purpose);
        this.createdAt = Objects.requireNonNull(now);
    }

    /** 프로필 업로드 승인(presign) 시점의 행. 크기는 신고 크기. */
    public static Image profileUpload(long uploaderId, String storageKey, String originalName, ImageFormat format,
                                      int declaredSize, Instant now) {
        return new Image(uploaderId, storageKey, originalName, format, declaredSize, ImagePurpose.PROFILE, now);
    }

    /** complete 통과: 실제 크기와 해상도 기록. */
    public void complete(int width, int height, int actualSize) {
        this.width = width;
        this.height = height;
        this.sizeBytes = actualSize;
    }

    public boolean isCompleted() {
        return width != null;
    }

    /** 프로필 연결: ATTACHED, 연결 해제 시각을 지운다. */
    public void attach() {
        this.status = ImageStatus.ATTACHED;
        this.detachedAt = null;
    }

    /** 연결 해제: 7일 뒤 정리 대상(상태는 그대로, 04 §4-4). */
    public void detach(Instant now) {
        this.detachedAt = Objects.requireNonNull(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getThumbStorageKey() {
        return thumbStorageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public ImageStatus getStatus() {
        return status;
    }

    public ImagePurpose getPurpose() {
        return purpose;
    }

    public Instant getDetachedAt() {
        return detachedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

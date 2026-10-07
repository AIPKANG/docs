package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * 회원 정지 이력 ({@code member_suspension}). 이 기능은 읽기와 기간이 끝난 정지의 자동 해제만 한다(정지 등록은 43).
 * 현재 정지 = {@code lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now)}.
 */
@Entity
@Table(name = "member_suspension")
public class MemberSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "reason", nullable = false, length = 200, updatable = false)
    private String reason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** NULL = 영구 정지. */
    @Column(name = "ends_at", updatable = false)
    private Instant endsAt;

    @Column(name = "suspended_by", nullable = false, updatable = false)
    private Long suspendedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by")
    private Long liftedBy;

    protected MemberSuspension() {
        // JPA
    }

    public boolean isCurrent(Instant now) {
        return liftedAt == null && (endsAt == null || endsAt.isAfter(now));
    }

    public boolean isExpiredButNotLifted(Instant now) {
        return liftedAt == null && endsAt != null && !endsAt.isAfter(now);
    }

    /** 기간이 끝난 정지의 자동 해제: {@code lifted_at = now}, {@code lifted_by = NULL}(시스템). */
    public void liftAutomatically(Instant now) {
        this.liftedAt = Objects.requireNonNull(now);
        this.liftedBy = null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Long getSuspendedBy() {
        return suspendedBy;
    }

    public Instant getLiftedAt() {
        return liftedAt;
    }

    public Long getLiftedBy() {
        return liftedBy;
    }
}

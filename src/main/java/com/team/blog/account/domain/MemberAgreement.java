package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 회원 동의 ({@code member_agreement}, 복합 PK {@code member_id}+{@code type}). 필수 동의 여부는 Service 책임(51 §4). */
@Entity
@Table(name = "member_agreement")
public class MemberAgreement {

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "member_id", nullable = false)
        private Long memberId;

        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false, length = 20)
        private AgreementType type;

        protected Id() {
        }

        public Id(long memberId, AgreementType type) {
            this.memberId = memberId;
            this.type = Objects.requireNonNull(type);
        }

        public Long getMemberId() {
            return memberId;
        }

        public AgreementType getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && Objects.equals(memberId, other.memberId) && type == other.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, type);
        }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private Instant agreedAt;

    protected MemberAgreement() {
        // JPA
    }

    public MemberAgreement(long memberId, AgreementType type, Instant agreedAt) {
        this.id = new Id(memberId, type);
        this.agreedAt = Objects.requireNonNull(agreedAt);
    }

    public Id getId() {
        return id;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }
}

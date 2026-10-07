package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 로그인 수단 ({@code auth_identity}, 51 §2의 9개 컬럼). 회원 하나에 정확히 하나({@code uq_auth_identity_member}).
 *
 * <ul>
 *   <li>LOCAL: {@code provider_user_id = email = 소문자·trim 이메일}, {@code password_hash} 필수({@code ck_auth_local_email},
 *       {@code ck_auth_password})</li>
 *   <li>GOOGLE: {@code provider_user_id = sub}, GITHUB: 숫자 ID 문자열. 비밀번호 없음</li>
 * </ul>
 */
@Entity
@Table(name = "auth_identity")
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private Provider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255, updatable = false)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;

    /** {@code {bcrypt}} 접두 해시. LOCAL만. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AuthIdentity() {
        // JPA
    }

    private AuthIdentity(long memberId, Provider provider, String providerUserId, String email, String passwordHash,
                         Instant emailVerifiedAt, Instant now) {
        this.memberId = memberId;
        this.provider = Objects.requireNonNull(provider);
        this.providerUserId = Objects.requireNonNull(providerUserId);
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = Objects.requireNonNull(now);
    }

    /** 이메일 가입(미인증). {@code normalizedEmail}은 trim·소문자 정규화된 값이어야 한다. */
    public static AuthIdentity local(long memberId, String normalizedEmail, String passwordHash, Instant now) {
        Objects.requireNonNull(normalizedEmail);
        if (!normalizedEmail.equals(normalizedEmail.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("email must be normalized");
        }
        return new AuthIdentity(memberId, Provider.LOCAL, normalizedEmail, normalizedEmail,
                Objects.requireNonNull(passwordHash), null, now);
    }

    /**
     * 소셜 가입. {@code emailVerifiedAt}은 공급자가 인증한 이메일이면 가입 시각, 사용자가 입력한 이메일이면 null.
     */
    public static AuthIdentity social(long memberId, Provider provider, String providerUserId, String email,
                                      Instant emailVerifiedAt, Instant now) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("social identity must not be LOCAL");
        }
        return new AuthIdentity(memberId, provider, providerUserId,
                email == null ? null : email.strip().toLowerCase(Locale.ROOT), null, emailVerifiedAt, now);
    }

    /** 인증 완료(이미 인증됐으면 시각을 바꾸지 않는다). */
    public void markVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Objects.requireNonNull(now);
        }
    }

    public void changePasswordHash(String newHash) {
        if (provider != Provider.LOCAL) {
            throw new IllegalStateException("only LOCAL identity has a password");
        }
        this.passwordHash = Objects.requireNonNull(newHash);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now);
    }

    public boolean isVerified() {
        return emailVerifiedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}

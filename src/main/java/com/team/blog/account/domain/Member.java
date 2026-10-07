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
import java.util.Objects;

/**
 * 회원 ({@code member}, 51 §2의 14개 컬럼). 001-auth와 공유하는 엔터티다.
 *
 * <p>{@code handle}은 생성자에서만 설정하고 setter·변경 메서드가 없다. JPA 매핑도 {@code updatable = false}라
 * 엔터티 필드가 바뀌어도 UPDATE 문에 들어가지 않는다(FR-011).
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handle", nullable = false, length = 39, updatable = false)
    private String handle;

    /** NULL은 익명 처리 후만({@code ck_member_nickname_null}). */
    @Column(name = "nickname", length = 10)
    private String nickname;

    /** 가입 시 NULL(가입 닉네임은 변경으로 세지 않음). */
    @Column(name = "nickname_changed_at")
    private Instant nicknameChangedAt;

    @Column(name = "bio", length = 200)
    private String bio;

    @Column(name = "profile_image_id")
    private Long profileImageId;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "default_visibility", nullable = false, length = 20)
    private String defaultVisibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Member() {
        // JPA
    }

    /** 가입용 생성자. 주소는 여기서만 정한다. */
    public Member(Handle handle, Nickname nickname, Instant now) {
        this.handle = Objects.requireNonNull(handle).toString();
        this.nickname = Objects.requireNonNull(nickname).value();
        this.nicknameChangedAt = null;
        this.role = Role.USER;
        this.status = MemberStatus.ACTIVE;
        this.defaultVisibility = "PUBLIC";
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getHandle() {
        return handle;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getNicknameChangedAt() {
        return nicknameChangedAt;
    }

    public String getBio() {
        return bio;
    }

    public Long getProfileImageId() {
        return profileImageId;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Role getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public String getDefaultVisibility() {
        return defaultVisibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

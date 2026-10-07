package com.team.blog.shared.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 회원의 principal. <b>이름 = memberId 문자열</b>(research R-3) — 세션 principal 인덱스·{@link CurrentUserProvider}가
 * 이 값을 쓴다. 권한은 {@code ROLE_USER}/{@code ROLE_ADMIN}. 세션(Redis)에 직렬화되므로 비밀번호 해시는 인증 후 지운다.
 */
public final class MemberPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long memberId;
    private final String role;
    private String passwordHash;

    private MemberPrincipal(long memberId, String role, String passwordHash) {
        this.memberId = memberId;
        this.role = role == null ? "USER" : role;
        this.passwordHash = passwordHash;
    }

    /** 세션 확립용(비밀번호 없음). */
    public static MemberPrincipal of(long memberId, String role) {
        return new MemberPrincipal(memberId, role, null);
    }

    /** 폼 로그인 비밀번호 비교용. */
    public static MemberPrincipal withPassword(long memberId, String role, String passwordHash) {
        return new MemberPrincipal(memberId, role, passwordHash);
    }

    public long memberId() {
        return memberId;
    }

    public String role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return String.valueOf(memberId);
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "MemberPrincipal[" + memberId + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MemberPrincipal other && other.memberId == memberId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(memberId);
    }
}

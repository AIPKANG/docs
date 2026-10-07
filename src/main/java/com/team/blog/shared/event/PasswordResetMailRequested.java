package com.team.blog.shared.event;

import java.util.List;

/**
 * 비밀번호 찾기 메일 요청(contracts/account-service.md §3). 원문 토큰은 메일 본문에만 쓴다(FR-014).
 *
 * @param kind           {@code LOCAL_LINK}(재설정 링크) / {@code SOCIAL_ONLY}(링크 없는 안내)
 * @param rawToken       {@code LOCAL_LINK}일 때만
 * @param otherProviders 같은 이메일의 소셜 수단 이름({@code GOOGLE}/{@code GITHUB})
 */
public record PasswordResetMailRequested(String email, Kind kind, String rawToken, List<String> otherProviders)
        implements DomainEvent {

    public enum Kind { LOCAL_LINK, SOCIAL_ONLY }

    @Override
    public String toString() {
        return "PasswordResetMailRequested[email=" + DomainEvent.maskEmail(email) + ", kind=" + kind
                + ", rawToken=" + DomainEvent.mask(rawToken) + ", otherProviders=" + otherProviders + "]";
    }
}

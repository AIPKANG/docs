package com.team.blog.shared.security;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SecurityContext의 인증 정보로 {@link CurrentUser}를 만든다.
 * 규칙: 인증 principal 이름 = {@code memberId} 문자열(001 research R-3). 권한 {@code ROLE_X} → role {@code X}.
 */
@Component
public class CurrentUserProvider {

    private static final String ROLE_PREFIX = "ROLE_";

    public Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        long memberId;
        try {
            memberId = Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse("USER");
        return Optional.of(new CurrentUser(memberId, role));
    }
}

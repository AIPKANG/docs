package com.team.blog.shared.security;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 로그인 후 이동 주소 검사(FR-027, research R-12): 우리 사이트 안의 상대 경로만 허용하고, 아니면 {@code /}.
 * {@code /}로 시작, {@code //}·{@code /\}로 시작하지 않음, 역슬래시·제어 문자 없음, 스킴·호스트 없음.
 */
public final class RedirectTargetValidator {

    public static final String FALLBACK = "/";

    private RedirectTargetValidator() {
    }

    public static String sanitize(String target) {
        return isSafe(target) ? target : FALLBACK;
    }

    public static boolean isSafe(String target) {
        if (target == null || target.isEmpty() || target.length() > 2000) {
            return false;
        }
        if (target.charAt(0) != '/' || target.startsWith("//") || target.indexOf('\\') >= 0) {
            return false;
        }
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c < 0x20 || c == 0x7f || Character.isWhitespace(c)) {
                return false;
            }
        }
        try {
            URI uri = new URI(target);
            return uri.getScheme() == null && uri.getHost() == null && uri.getRawAuthority() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}

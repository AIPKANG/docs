package com.team.blog.share.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.application.visibility.VisibilityRule;
import com.team.blog.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code LINK}(029, 강성찬 개인 확장): 주소의 열쇠({@code ?key=})가 맞으면 누구나(비회원 포함) 읽는다. 어떤 공용 목록에도 넣지 않는다.
 * 글 화면에서 부르는 API(댓글·좋아요·조회)는 같은 출처의 Referer에 열쇠가 실려 온다(Referrer-Policy strict-origin-when-cross-origin).
 */
@Component
@ConditionalOnProperty(name = "blog.share.link.enabled", havingValue = "true", matchIfMissing = true)
public class LinkVisibilityRule implements VisibilityRule {

    public static final String VALUE = "LINK";

    private final LinkShareService shares;

    public LinkVisibilityRule(LinkShareService shares) {
        this.shares = shares;
    }

    @Override
    public String visibility() {
        return VALUE;
    }

    @Override
    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        return currentKey().map(key -> shares.matches(post.id(), key)).orElse(false);
    }

    @Override
    public String listCondition(String postAlias) {
        return null;
    }

    /** 요청의 {@code key}, 없으면 같은 출처 Referer 주소의 {@code key}. */
    static Optional<String> currentKey() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        HttpServletRequest request = attrs.getRequest();
        String key = request.getParameter("key");
        if (key != null && !key.isEmpty()) {
            return Optional.of(key);
        }
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return Optional.empty();
            }
            return Optional.ofNullable(UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("key"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

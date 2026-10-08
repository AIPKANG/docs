package com.team.blog.post.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.application.visibility.VisibilityRule;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.security.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 글 읽기 판정을 한곳에(헌법 III, 42 §3·§5-1, 006 R-1).
 * 휴지통(조회에서 제외) → 작성자가 탈퇴 유예·익명이면 아무도 못 봄 → 작성자 본인은 봄 → 관리자 숨김이면 아무도 못 봄(022)
 * → 임시글은 작성자만 → 공개 범위 규칙. 관리자 예외는 없다. 목록은 {@link #publicListingCondition}만 쓴다.
 */
@Component
public class PostAccessPolicy {

    private final Map<String, VisibilityRule> rules;

    public PostAccessPolicy(List<VisibilityRule> rules) {
        Map<String, VisibilityRule> byValue = new LinkedHashMap<>();
        rules.forEach(rule -> byValue.put(rule.visibility(), rule));
        this.rules = Map.copyOf(byValue);
    }

    public boolean canRead(Optional<CurrentUser> viewer, PostFacts post) {
        if (post.authorWithdrawn()) {
            return false;
        }
        boolean author = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (author) {
            return true;
        }
        if (post.hidden()) {
            return false;
        }
        if (post.status() != PostStatus.PUBLISHED) {
            return false;
        }
        VisibilityRule rule = rules.get(post.visibility());
        return rule != null && rule.canRead(viewer, post);
    }

    /** 지원하는 공개 범위 값(검사용). */
    public Set<String> supportedVisibilities() {
        return rules.keySet();
    }

    /**
     * 작성자가 아닌 사람에게 보이는 공용 목록 조건(006 R-2). 작성자 본인의 블로그 목록도 이 조건을 쓴다(비공개 글 안 보임).
     *
     * @param postAlias   {@code post} 별칭
     * @param memberAlias 작성자 {@code member} 별칭(조인 필요)
     */
    public String publicListingCondition(String postAlias, String memberAlias) {
        String visibility = rules.values().stream()
                .map(rule -> rule.listCondition(postAlias))
                .filter(Objects::nonNull)
                .map(c -> "(" + c + ")")
                .collect(Collectors.joining(" OR "));
        if (visibility.isEmpty()) {
            visibility = "FALSE";
        }
        return base(postAlias, memberAlias) + " AND (" + visibility + ")";
    }

    /**
     * 친구가 보는 개인 블로그 목록 조건(025, 강성찬 개인 확장, 06 §6-3): 공용 조건에 친구 공개 글을 더한다. 다른 목록에는 쓰지 않는다.
     */
    public String friendBlogCondition(String postAlias, String memberAlias) {
        return base(postAlias, memberAlias) + " AND " + postAlias + ".visibility IN ('PUBLIC', 'FRIENDS')";
    }

    private static String base(String postAlias, String memberAlias) {
        return postAlias + ".status = 'PUBLISHED' AND " + postAlias + ".deleted_at IS NULL AND " + postAlias
                + ".hidden_at IS NULL AND " + memberAlias + ".withdrawn_at IS NULL";
    }
}

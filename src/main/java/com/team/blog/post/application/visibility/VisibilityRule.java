package com.team.blog.post.application.visibility;

import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;

/**
 * 공개 범위 값 하나의 규칙(006 R-1, 06 §7 R-3). 새 공개 범위(친구 공개·개인 확장)는 이 인터페이스의 Bean을 <b>추가</b>해
 * 붙인다 — 기존 규칙을 고치지 않는다(헌법 III).
 */
public interface VisibilityRule {

    /** {@code post.visibility} 값. */
    String visibility();

    /** 작성자가 아닌 사람이 발행된 이 공개 범위의 글을 읽을 수 있는지. */
    boolean canRead(Optional<CurrentUser> viewer, PostFacts post);

    /**
     * 작성자가 아닌 사람에게 보이는 공용 목록(홈·블로그·태그·검색·sitemap)에 넣을 조건. 넣지 않으면 {@code null}.
     *
     * @param postAlias 글 테이블 별칭
     */
    String listCondition(String postAlias);
}

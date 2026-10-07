package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 글 읽기 판정을 한곳에(헌법 III, 42 §5-1). 발행 + 전체 공개는 누구나, 발행 + 비공개와 임시글은 작성자만.
 * 휴지통·없는 글은 조회 단계에서 이미 빠진다. 공개 범위 확장(006·친구 공개)·관리자 숨김(022)은 여기에 규칙을 더한다.
 * 목록(009)은 같은 규칙의 SQL 조건({@link #PUBLIC_LISTING_CONDITION})만 쓴다.
 */
@Component
public class PostAccessPolicy {

    /** 누구에게나 보이는 글의 조건(목록용, 별칭 {@code p}). */
    public static final String PUBLIC_LISTING_CONDITION =
            "p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL";

    public boolean canRead(Optional<CurrentUser> viewer, long authorId, PostStatus status, String visibility) {
        boolean author = viewer.map(v -> v.memberId() == authorId).orElse(false);
        if (author) {
            return true;
        }
        return status == PostStatus.PUBLISHED && "PUBLIC".equals(visibility);
    }
}

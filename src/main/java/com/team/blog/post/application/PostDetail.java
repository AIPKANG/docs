package com.team.blog.post.application;

import com.team.blog.account.application.AuthorDisplay;
import com.team.blog.post.domain.PostStatus;
import java.time.Instant;
import java.util.List;

/**
 * 글 상세 한 건(010). {@code contentHtml}은 007 렌더러가 정화한 결과뿐, Markdown 원문은 읽지 않는다.
 *
 * @param editingSavedAt 작성자에게만: 고치는 중인 내용의 저장 시각(없으면 null)
 */
public record PostDetail(long id, long authorId, PostStatus status, String visibility, String title, String contentHtml,
                         String excerpt, Instant publishedAt, Instant firstPublicAt, Instant editedAt, long viewCount,
                         int likeCount, int commentCount, List<String> tags, Author author, boolean editing,
                         Instant editingSavedAt, String hiddenReason) {

    /** 관리자가 숨긴 글(022). 이 값이 있는 상세는 작성자만 본다. */
    public boolean hidden() {
        return hiddenReason != null;
    }

    public record Author(String handle, String nickname, String profileImageUrl, String bio) {

        public AuthorDisplay display() {
            return AuthorDisplay.of(handle, nickname, null, profileImageUrl);
        }
    }

    public boolean isPublic() {
        return status == PostStatus.PUBLISHED && "PUBLIC".equals(visibility);
    }

    public boolean isPrivate() {
        return "PRIVATE".equals(visibility);
    }

    public String url() {
        return "/@" + author.handle() + "/posts/" + id;
    }

    /** 표시 날짜: 공개 글은 처음 공개된 시각(목록과 같음), 작성자가 보는 비공개 글은 발행 시각(010 FR-008). */
    public Instant displayDate() {
        return firstPublicAt != null && isPublic() ? firstPublicAt : publishedAt;
    }

    /** 미리보기 설명: 요약 앞 160자. */
    public String description() {
        if (excerpt == null) {
            return "";
        }
        return excerpt.codePointCount(0, excerpt.length()) <= 160 ? excerpt
                : excerpt.substring(0, excerpt.offsetByCodePoints(0, 160));
    }

    /** 미리보기 이미지: 정화된 본문의 첫 이미지(우리 저장소 주소만 남아 있음). 없으면 null. */
    public String firstImageUrl() {
        if (contentHtml == null) {
            return null;
        }
        int at = contentHtml.indexOf("<img src=\"");
        if (at < 0) {
            return null;
        }
        int start = at + "<img src=\"".length();
        int end = contentHtml.indexOf('"', start);
        return end > start ? contentHtml.substring(start, end).replace("&#61;", "=").replace("&amp;", "&") : null;
    }
}

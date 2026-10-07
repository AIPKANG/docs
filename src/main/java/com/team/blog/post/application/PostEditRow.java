package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import java.time.Instant;

/**
 * 휴지통 밖 글 하나의 편집 관점 DB 상태({@code post} + {@code post_draft}).
 *
 * @param draftVersion 작업본이 없으면 {@code null}
 */
public record PostEditRow(long id, long authorId, PostStatus status, String title, String contentMd, long postVersion,
                          Instant updatedAt, Long draftVersion, String draftTitle, String draftContentMd,
                          Instant draftUpdatedAt, String visibility) {

    /** 공개 범위를 모르는 곳(단위 테스트 등)용. */
    public PostEditRow(long id, long authorId, PostStatus status, String title, String contentMd, long postVersion,
                       Instant updatedAt, Long draftVersion, String draftTitle, String draftContentMd, Instant draftUpdatedAt) {
        this(id, authorId, status, title, contentMd, postVersion, updatedAt, draftVersion, draftTitle, draftContentMd,
                draftUpdatedAt, "PUBLIC");
    }

    /** 작업본이 지금 발행본보다 새 버전인지. */
    public boolean hasWorkingCopy() {
        return status == PostStatus.PUBLISHED && draftVersion != null && draftVersion > postVersion;
    }

    /** DB 쪽 현재 버전(research R-2): 임시글은 {@code post}, 발행 글은 작업본과 발행본 중 큰 쪽. */
    public long dbVersion() {
        return hasWorkingCopy() ? draftVersion : postVersion;
    }

    /** DB 쪽 현재 내용. */
    public EditingContent dbContent() {
        if (hasWorkingCopy()) {
            return new EditingContent(id, status, draftTitle, draftContentMd, draftVersion, draftUpdatedAt, true);
        }
        return new EditingContent(id, status, title, contentMd, postVersion, updatedAt, false);
    }
}

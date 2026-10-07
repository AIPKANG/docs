package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostEditStore;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 내 글 최소 목록(research R-11, U-3). 011(내 글 관리·휴지통)이 탭·페이지·삭제를 넓힌다.
 * "수정 중"은 작업본 또는 발행본보다 새 버퍼가 있는 발행 글(FR-023).
 */
@Service
public class PostManageQuery {

    static final int LIMIT = 200;

    private final AccountGuard accountGuard;
    private final PostEditStore store;
    private final PostDraftService draftService;

    public PostManageQuery(AccountGuard accountGuard, PostEditStore store, PostDraftService draftService) {
        this.accountGuard = accountGuard;
        this.store = store;
        this.draftService = draftService;
    }

    public List<MyPostRow> myPosts(Optional<CurrentUser> currentUser) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        return store.listByAuthor(user.memberId(), LIMIT).stream()
                .map(row -> row.status() == PostStatus.PUBLISHED && !row.editing() ? withBufferCheck(row) : row)
                .toList();
    }

    private MyPostRow withBufferCheck(MyPostRow row) {
        boolean editing = store.findActive(row.id()).map(draftService::isEditing).orElse(false);
        return editing ? new MyPostRow(row.id(), row.title(), row.status(), true, row.updatedAt(), row.visibility()) : row;
    }
}

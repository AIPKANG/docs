package com.team.blog.interaction.web;

import com.team.blog.interaction.application.CommentPage;
import com.team.blog.interaction.application.CommentQuery;
import com.team.blog.post.application.CardDates;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * 글 상세의 댓글 영역(014 FR-018~FR-020): 첫 20개를 화면과 함께 보낸다. {@code ?comment=}이면 그 댓글부터,
 * {@code ?commentCursor=}이면 다음 20개(스크립트 없는 [댓글 더 보기]).
 */
@Component
public class CommentDetailSection implements PostDetailSection {

    private final CommentQuery commentQuery;
    private final Clock clock;
    private final com.team.blog.shared.security.AccountGuard accountGuard;

    public CommentDetailSection(CommentQuery commentQuery, Clock clock,
                                com.team.blog.shared.security.AccountGuard accountGuard) {
        this.commentQuery = commentQuery;
        this.clock = clock;
        this.accountGuard = accountGuard;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        if (post.status() != PostStatus.PUBLISHED) {
            return;
        }
        Long around = null;
        try {
            around = params.get("comment") == null ? null : Long.parseLong(params.get("comment"));
        } catch (NumberFormatException e) {
            around = null;
        }
        CommentPage page;
        try {
            page = commentQuery.pageOfReadable(viewer, post.id(), post.authorId(), params.get("commentCursor"), null, around);
        } catch (PostContentException e) {
            page = commentQuery.pageOfReadable(viewer, post.id(), post.authorId(), null, null, null);
        }
        Map<Long, String> dates = new HashMap<>();
        page.items().forEach(c -> {
            dates.put(c.id(), CardDates.label(c.createdAt(), clock.instant()));
            if (c.replies() != null) {
                c.replies().forEach(r -> dates.put(r.id(), CardDates.label(r.createdAt(), clock.instant())));
            }
        });
        String writeState = "GUEST";
        if (viewer.isPresent()) {
            try {
                accountGuard.requireWritable(viewer);
                writeState = "OK";
            } catch (RuntimeException e) {
                writeState = "UNVERIFIED";
            }
        }
        model.addAttribute("commentWriteState", writeState);
        model.addAttribute("comments", page);
        model.addAttribute("commentDates", dates);
        model.addAttribute("commentTarget", around);
        model.addAttribute("commentContinued", params.get("commentCursor") != null);
    }
}

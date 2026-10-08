package com.team.blog.share.web;

import static com.team.blog.shared.web.Redirects.seeOther;

import com.team.blog.share.application.LinkShareService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.view.RedirectView;

/** 링크 공개 글의 [새 링크 만들기](029): 글쓴이만, 예전 주소는 바로 막힌다. */
@Controller
public class LinkShareController {

    private final LinkShareService shares;
    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;

    public LinkShareController(LinkShareService shares, JdbcTemplate jdbc, AccountGuard accountGuard,
                               CurrentUserProvider currentUserProvider) {
        this.shares = shares;
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/posts/{id}/share-link")
    public RedirectView regenerate(@PathVariable("id") long id) {
        CurrentUser me = accountGuard.requireLoggedIn(currentUserProvider.current());
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.handle FROM post p JOIN member m ON m.id = p.author_id
                WHERE p.id = ? AND p.author_id = ? AND p.deleted_at IS NULL
                """, id, me.memberId());
        if (rows.isEmpty()) {
            throw new NotFoundException();
        }
        shares.regenerate(id);
        return seeOther("/@" + rows.get(0).get("handle") + "/posts/" + id + "#share-link");
    }
}

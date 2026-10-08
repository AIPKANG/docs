package com.team.blog.discovery.web;

import com.team.blog.discovery.application.AuthorStatsQuery;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 작성자 통계 화면(032, 강성찬 개인 확장): 본인만. {@code blog.stats.enabled=false}면 화면이 없다. */
@Controller
@ConditionalOnProperty(name = "blog.stats.enabled", havingValue = "true", matchIfMissing = true)
public class AuthorStatsController {

    private final AuthorStatsQuery stats;
    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;

    public AuthorStatsController(AuthorStatsQuery stats, AccountGuard accountGuard, CurrentUserProvider currentUserProvider) {
        this.stats = stats;
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/manage/stats")
    public String page(Model model) {
        long me = accountGuard.requireLoggedIn(currentUserProvider.current()).memberId();
        model.addAttribute("stats", stats.stats(me));
        model.addAttribute("pageNoindex", true);
        return "post/stats";
    }
}

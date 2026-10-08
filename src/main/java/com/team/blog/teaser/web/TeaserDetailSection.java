package com.team.blog.teaser.web;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.teaser.application.TeaserService;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 글 화면(035): 글쓴이에게 지금 티저와 [AI로 만들기]·직접 쓰기. */
@Component
public class TeaserDetailSection implements PostDetailSection {

    private final TeaserService teasers;
    private final AiConsentService consent;

    public TeaserDetailSection(TeaserService teasers, AiConsentService consent) {
        this.teasers = teasers;
        this.consent = consent;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        boolean author = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (!teasers.enabled() || !author || post.hidden()) {
            return;
        }
        model.addAttribute("teaserBox", true);
        model.addAttribute("currentTeaser", teasers.current(post.id()).orElse(""));
        model.addAttribute("teaserConsent", consent.hasConsent(post.authorId()));
    }
}

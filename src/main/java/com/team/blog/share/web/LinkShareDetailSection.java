package com.team.blog.share.web;

import com.team.blog.post.application.PostDetail;
import com.team.blog.post.markdown.MarkdownProperties;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.share.application.LinkShareService;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 링크 공개 글(029): 글쓴이에게 공유 주소와 [새 링크 만들기]를 보여 준다. */
@Component
public class LinkShareDetailSection implements PostDetailSection {

    private final LinkShareService shares;
    private final String origin;

    public LinkShareDetailSection(LinkShareService shares, MarkdownProperties markdownProperties) {
        this.shares = shares;
        this.origin = markdownProperties.siteOrigin().replaceAll("/+$", "");
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        boolean author = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (author && "LINK".equals(post.visibility())) {
            model.addAttribute("shareLinkUrl", origin + post.url() + "?key=" + shares.token(post.id()));
        }
    }
}

package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 개인 블로그({@code GET /@{handle}}, 009 FR-016~FR-020): 프로필 머리말 + 공개 글 수 + 그 회원의 공개 글 카드.
 * 작성자 본인이 봐도 비공개 글은 나오지 않는다. 없는·형식 밖·탈퇴 주소는 404, 대문자 주소는 필터가 301.
 */
@Controller
public class BlogPageController {

    private final BlogOwnerResolver blogOwnerResolver;
    private final PostListQuery listQuery;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final com.team.blog.post.application.TagListingQuery tagListingQuery;

    public BlogPageController(BlogOwnerResolver blogOwnerResolver, PostListQuery listQuery,
                              CurrentUserProvider currentUserProvider, Clock clock,
                              com.team.blog.post.application.TagListingQuery tagListingQuery) {
        this.blogOwnerResolver = blogOwnerResolver;
        this.listQuery = listQuery;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.tagListingQuery = tagListingQuery;
    }

    @GetMapping("/@{handle}")
    public Object blog(@PathVariable("handle") String handle,
                       @RequestParam(value = "cursor", required = false) String cursor,
                       @RequestParam(value = "tag", required = false) String tagRaw, Model model) {
        BlogOwner owner = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        // 013 FR-026: 블로그 안 태그 필터. 정리되지 않은 값은 정리된 주소로 301, 형식 밖이면 필터 없이
        String tag = null;
        if (tagRaw != null && !tagRaw.isEmpty()) {
            java.util.Optional<String> normalized = com.team.blog.tag.domain.TagNormalizer.lookupName(tagRaw);
            if (normalized.isPresent() && !normalized.get().equals(tagRaw)) {
                org.springframework.web.servlet.view.RedirectView redirect = new org.springframework.web.servlet.view.RedirectView(
                        "/@" + owner.handle() + "?tag=" + org.springframework.web.util.UriUtils.encodeQueryParam(
                                normalized.get(), java.nio.charset.StandardCharsets.UTF_8));
                redirect.setStatusCode(org.springframework.http.HttpStatus.MOVED_PERMANENTLY);
                return redirect;
            }
            tag = normalized.orElse(null);
        }
        java.util.Optional<Long> tagId = tag == null ? java.util.Optional.empty() : tagListingQuery.tagId(tag);
        CardPage page;
        String requestedCursor = cursor;
        try {
            page = tag == null ? listQuery.blog(owner.memberId(), requestedCursor)
                    : tagId.map(id -> listQuery.blogByTag(owner.memberId(), id, requestedCursor)).orElse(new CardPage(java.util.List.of(), null));
        } catch (PostContentException e) {
            page = tag == null ? listQuery.blog(owner.memberId(), null)
                    : tagId.map(id -> listQuery.blogByTag(owner.memberId(), id, null)).orElse(new CardPage(java.util.List.of(), null));
            cursor = null;
        }
        model.addAttribute("filterTag", tag);
        model.addAttribute("blogTags", tagListingQuery.blogTags(owner.memberId(), 50));
        model.addAttribute("owner", owner);
        model.addAttribute("author", owner.toAuthorDisplay());
        model.addAttribute("publicCount", listQuery.publicCount(owner.memberId()));
        model.addAttribute("isOwner", currentUserProvider.current().map(CurrentUser::memberId)
                .map(id -> id == owner.memberId()).orElse(false));
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", "/api/members/" + owner.handle() + "/posts"
                + (tag == null ? "" : "?tag=" + org.springframework.web.util.UriUtils.encodeQueryParam(tag, java.nio.charset.StandardCharsets.UTF_8)));
        CardModel.fill(model, page, clock.instant());
        return "blog/home";
    }
}

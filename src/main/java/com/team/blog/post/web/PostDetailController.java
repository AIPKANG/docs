package com.team.blog.post.web;

import com.team.blog.post.application.CardDates;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.application.PostDetailQuery;
import com.team.blog.post.application.ViewCountFormat;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.markdown.MarkdownProperties;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.KoreanDateFormatter;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 글 상세({@code GET /@{handle}/posts/{id}}, 010 R-1). 처리 순서: 대문자 주소 301(필터) → 숫자가 아니면 404 → 볼 수 있는지(006)
 * → 주소의 블로그가 작성자와 다르면 301 → 작성자 본인의 임시글이면 에디터로 302 → 표시.
 * 캐시: 공개 글 {@code private, no-cache}, 그 밖 {@code private, no-store}(FR-026).
 */
@Controller
public class PostDetailController {

    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
            .withZone(KoreanDateFormatter.SEOUL);

    private final PostDetailQuery detailQuery;
    private final CurrentUserProvider currentUserProvider;
    private final MarkdownProperties markdownProperties;
    private final Clock clock;
    private final java.util.List<PostDetailSection> sections;

    public PostDetailController(PostDetailQuery detailQuery, CurrentUserProvider currentUserProvider,
                                MarkdownProperties markdownProperties, Clock clock, java.util.List<PostDetailSection> sections) {
        this.detailQuery = detailQuery;
        this.currentUserProvider = currentUserProvider;
        this.markdownProperties = markdownProperties;
        this.clock = clock;
        this.sections = sections;
    }

    @GetMapping("/@{handle}/posts/{postId}")
    public Object detail(@PathVariable("handle") String handle, @PathVariable("postId") String postId, Model model,
                         HttpServletResponse response,
                         @org.springframework.web.bind.annotation.RequestParam java.util.Map<String, String> params) {
        long id = parseId(postId);
        Optional<CurrentUser> viewer = currentUserProvider.current();
        PostDetail post = detailQuery.find(viewer, id).orElseThrow(NotFoundException::new);
        if (!post.author().handle().equals(handle)) {
            RedirectView redirect = new RedirectView(post.url());
            redirect.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            return redirect;
        }
        boolean isAuthor = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (isAuthor && post.status() == PostStatus.DRAFT) {
            RedirectView redirect = new RedirectView("/write/" + post.id());
            redirect.setStatusCode(HttpStatus.FOUND);
            return redirect;
        }
        response.setHeader("Cache-Control", post.isPublic() ? "private, no-cache" : "private, no-store");

        boolean authenticated = viewer.isPresent();
        model.addAttribute("post", post);
        model.addAttribute("author", post.author().display());
        model.addAttribute("viewerIsAuthor", isAuthor);
        model.addAttribute("canReact", authenticated && !isAuthor);
        model.addAttribute("loginRedirect", "/login?redirect=" + post.url());
        model.addAttribute("displayDate", CardDates.label(post.displayDate(), clock.instant()));
        model.addAttribute("editedDate", post.editedAt() == null ? null : KoreanDateFormatter.monthDay(post.editedAt()));
        model.addAttribute("editingSavedAt", post.editingSavedAt() == null ? null : SAVED_AT.format(post.editingSavedAt()));
        model.addAttribute("viewCount", ViewCountFormat.format(post.viewCount()));
        String origin = markdownProperties.siteOrigin().replaceAll("/+$", "");
        model.addAttribute("canonicalUrl", origin + post.url());
        String image = post.firstImageUrl();
        model.addAttribute("ogImage", image != null ? (image.startsWith("/") ? origin + image : image)
                : origin + "/images/og-default.png");
        if (post.isPublic()) {
            model.addAttribute("pageMeta", new com.team.blog.shared.web.PageMeta(post.title(), post.description(),
                    origin + post.url(), (String) model.getAttribute("ogImage"), String.valueOf(post.firstPublicAt()),
                    post.editedAt() == null ? null : post.editedAt().toString()));
        } else {
            model.addAttribute("pageNoindex", true);
        }
        for (PostDetailSection section : sections) {
            section.contribute(model, post, viewer, params);
        }
        model.addAttribute("recordView", post.isPublic() && authenticatedRole(viewer) && !isAuthor);
        return "post/detail";
    }

    /** 조회 기록 대상: 비회원·일반 회원(관리자 제외, 010 FR-021). */
    private static boolean authenticatedRole(Optional<CurrentUser> viewer) {
        return viewer.map(v -> !"ADMIN".equals(v.role())).orElse(true);
    }

    private static long parseId(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,18}")) {
            throw new NotFoundException();
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new NotFoundException();
        }
    }
}

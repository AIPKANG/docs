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
    private final com.team.blog.interaction.application.FollowQuery followQuery;
    private final SearchController searchController;
    private final com.team.blog.friend.application.FriendQuery friendQuery;
    private final com.team.blog.discovery.application.ActivityQuery activityQuery;

    public BlogPageController(BlogOwnerResolver blogOwnerResolver, PostListQuery listQuery,
                              CurrentUserProvider currentUserProvider, Clock clock,
                              com.team.blog.post.application.TagListingQuery tagListingQuery,
                              com.team.blog.interaction.application.FollowQuery followQuery,
                              SearchController searchController,
                              com.team.blog.friend.application.FriendQuery friendQuery,
                              com.team.blog.discovery.application.ActivityQuery activityQuery) {
        this.activityQuery = activityQuery;
        this.friendQuery = friendQuery;
        this.followQuery = followQuery;
        this.searchController = searchController;
        this.blogOwnerResolver = blogOwnerResolver;
        this.listQuery = listQuery;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.tagListingQuery = tagListingQuery;
    }

    @GetMapping("/@{handle}")
    public Object blog(@PathVariable("handle") String handle,
                       @RequestParam(value = "cursor", required = false) String cursor,
                       @RequestParam(value = "tag", required = false) String tagRaw,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "sort", required = false) String sort,
                       jakarta.servlet.http.HttpServletRequest request, Model model) {
        BlogOwner owner = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        // 020 FR-017: 블로그 안 검색
        if (q != null) {
            return searchController.blog(owner, q, sort, cursor, request, model);
        }
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
        // 025 강성찬 개인 확장: 보는 사람 기준 친구 상태, 친구면 친구 공개 글도 목록에
        com.team.blog.friend.application.FriendQuery.Status friendStatus = currentUserProvider.current()
                .map(v -> friendQuery.status(v.memberId(), owner.memberId()))
                .orElse(com.team.blog.friend.application.FriendQuery.Status.NONE);
        boolean withFriends = friendStatus == com.team.blog.friend.application.FriendQuery.Status.FRIENDS;
        // 030: 친구면 그 사람이 든 그룹의 그룹 공개 글도
        Long friendViewer = withFriends ? currentUserProvider.current().map(CurrentUser::memberId).orElse(null) : null;
        try {
            page = blogPage(owner.memberId(), friendViewer, tag, tagId, requestedCursor);
        } catch (PostContentException e) {
            page = blogPage(owner.memberId(), friendViewer, tag, tagId, null);
            cursor = null;
        }
        model.addAttribute("filterTag", tag);
        model.addAttribute("blogTags", tagListingQuery.blogTags(owner.memberId(), 50));
        model.addAttribute("owner", owner);
        model.addAttribute("author", owner.toAuthorDisplay());
        model.addAttribute("publicCount", listQuery.publicCount(owner.memberId()));
        model.addAttribute("isOwner", currentUserProvider.current().map(CurrentUser::memberId)
                .map(id -> id == owner.memberId()).orElse(false));
        // 018: 팔로워·팔로잉 수와 보는 사람 기준 팔로우 상태
        model.addAttribute("followCounts", followQuery.counts(owner.memberId()));
        model.addAttribute("followingOwner", currentUserProvider.current().map(CurrentUser::memberId)
                .filter(id -> id != owner.memberId()).map(id -> followQuery.isFollowing(id, owner.memberId())).orElse(false));
        model.addAttribute("friendStatus", friendStatus.name());
        // 031 강성찬 개인 확장: 잔디·스트릭(첫 화면에서만)
        if (activityQuery.enabled() && (cursor == null || cursor.isEmpty()) && tag == null) {
            model.addAttribute("grass", activityQuery.grass(owner.memberId()));
        }
        model.addAttribute("loginRedirect", "/login?redirect=/@" + owner.handle());
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", "/api/members/" + owner.handle() + "/posts"
                + (tag == null ? "" : "?tag=" + org.springframework.web.util.UriUtils.encodeQueryParam(tag, java.nio.charset.StandardCharsets.UTF_8)));
        CardModel.fill(model, page, clock.instant());
        return "blog/home";
    }

    private CardPage blogPage(long ownerId, Long friendViewer, String tag, java.util.Optional<Long> tagId, String cursor) {
        if (tag != null && tagId.isEmpty()) {
            return new CardPage(java.util.List.of(), null);
        }
        Long tid = tagId.orElse(null);
        if (friendViewer != null) {
            return listQuery.blogForFriend(ownerId, friendViewer, tid, cursor);
        }
        return tid == null ? listQuery.blog(ownerId, cursor) : listQuery.blogByTag(ownerId, tid, cursor);
    }
}

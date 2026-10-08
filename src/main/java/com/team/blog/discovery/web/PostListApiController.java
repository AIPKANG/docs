package com.team.blog.discovery.web;

import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** [더 보기] API(10 §4-2). {@code size}는 받지만 무시하고 9개로 고정한다. 잘못된 커서는 400 {@code INVALID_CURSOR}. */
@RestController
public class PostListApiController {

    private final PostListQuery listQuery;
    private final BlogOwnerResolver blogOwnerResolver;
    private final com.team.blog.post.application.TagListingQuery tagListingQuery;
    private final com.team.blog.shared.security.CurrentUserProvider currentUserProvider;
    private final com.team.blog.friend.application.FriendQuery friendQuery;

    public PostListApiController(PostListQuery listQuery, BlogOwnerResolver blogOwnerResolver,
                                 com.team.blog.post.application.TagListingQuery tagListingQuery,
                                 com.team.blog.shared.security.CurrentUserProvider currentUserProvider,
                                 com.team.blog.friend.application.FriendQuery friendQuery) {
        this.currentUserProvider = currentUserProvider;
        this.friendQuery = friendQuery;
        this.listQuery = listQuery;
        this.blogOwnerResolver = blogOwnerResolver;
        this.tagListingQuery = tagListingQuery;
    }

    @GetMapping("/api/posts")
    public CardPage feed(@RequestParam(value = "cursor", required = false) String cursor) {
        return listQuery.feed(cursor);
    }

    @GetMapping("/api/members/{handle}/posts")
    public CardPage blog(@PathVariable("handle") String handle,
                         @RequestParam(value = "cursor", required = false) String cursor,
                         @RequestParam(value = "tag", required = false) String tag) {
        long ownerId = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new).memberId();
        // 025 강성찬 개인 확장: 친구면 친구 공개 글도(블로그 화면과 같은 규칙)
        Long friendViewer = currentUserProvider.current().map(v -> v.memberId())
                .filter(v -> friendQuery.areFriends(v, ownerId)).orElse(null);
        if (tag == null || tag.isEmpty()) {
            return friendViewer != null ? listQuery.blogForFriend(ownerId, friendViewer, null, cursor) : listQuery.blog(ownerId, cursor);
        }
        return com.team.blog.tag.domain.TagNormalizer.lookupName(tag).flatMap(tagListingQuery::tagId)
                .map(id -> friendViewer != null ? listQuery.blogForFriend(ownerId, friendViewer, id, cursor)
                        : listQuery.blogByTag(ownerId, id, cursor))
                .orElse(new CardPage(java.util.List.of(), null));
    }

    /** 013 FR-025: 블로그 태그 줄. */
    @GetMapping("/api/members/{handle}/tags")
    public java.util.List<com.team.blog.post.application.TagListingQuery.TagCount> blogTags(@PathVariable("handle") String handle) {
        long ownerId = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new).memberId();
        return tagListingQuery.blogTags(ownerId, 100);
    }
}

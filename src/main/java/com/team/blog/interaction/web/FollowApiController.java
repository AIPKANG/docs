package com.team.blog.interaction.web;

import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.interaction.application.FollowQuery;
import com.team.blog.interaction.application.FollowService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 팔로우 API(24 §3). 목록은 누구나, 팔로우·언팔로우는 로그인(인증 전 포함). */
@RestController
public class FollowApiController {

    private final FollowService followService;
    private final FollowQuery followQuery;
    private final BlogOwnerResolver ownerResolver;
    private final CurrentUserProvider currentUserProvider;
    private final ClientIpResolver clientIpResolver;

    public FollowApiController(FollowService followService, FollowQuery followQuery, BlogOwnerResolver ownerResolver,
                               CurrentUserProvider currentUserProvider, ClientIpResolver clientIpResolver) {
        this.followService = followService;
        this.followQuery = followQuery;
        this.ownerResolver = ownerResolver;
        this.currentUserProvider = currentUserProvider;
        this.clientIpResolver = clientIpResolver;
    }

    @PutMapping("/api/members/{handle}/follow")
    public FollowService.FollowResult follow(@PathVariable("handle") String handle, HttpServletRequest request) {
        return followService.set(currentUserProvider.current(), handle, true, clientIpResolver.resolve(request));
    }

    @DeleteMapping("/api/members/{handle}/follow")
    public FollowService.FollowResult unfollow(@PathVariable("handle") String handle, HttpServletRequest request) {
        return followService.set(currentUserProvider.current(), handle, false, clientIpResolver.resolve(request));
    }

    @GetMapping("/api/members/{handle}/followers")
    public FollowQuery.FollowPage followers(@PathVariable("handle") String handle,
                                            @RequestParam(value = "cursor", required = false) String cursor) {
        return list(handle, true, cursor);
    }

    @GetMapping("/api/members/{handle}/following")
    public FollowQuery.FollowPage following(@PathVariable("handle") String handle,
                                            @RequestParam(value = "cursor", required = false) String cursor) {
        return list(handle, false, cursor);
    }

    private FollowQuery.FollowPage list(String handle, boolean followers, String cursor) {
        long owner = ownerResolver.resolve(handle).orElseThrow(NotFoundException::new).memberId();
        Long viewer = currentUserProvider.current().map(CurrentUser::memberId).orElse(null);
        return followQuery.page(owner, followers, viewer, cursor);
    }
}

package com.team.blog.interaction.web;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.account.application.MemberSummaryQuery;
import com.team.blog.interaction.application.FollowQuery;
import com.team.blog.interaction.application.FollowService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** 팔로워·팔로잉 화면(24 §2-2), 스크립트 없는 팔로우 폼, 알림의 "내 팔로워 목록" 이동. */
@Controller
public class FollowPageController {

    private final FollowService followService;
    private final FollowQuery followQuery;
    private final BlogOwnerResolver ownerResolver;
    private final MemberSummaryQuery memberSummaryQuery;
    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;
    private final ClientIpResolver clientIpResolver;

    public FollowPageController(FollowService followService, FollowQuery followQuery, BlogOwnerResolver ownerResolver,
                                MemberSummaryQuery memberSummaryQuery, AccountGuard accountGuard,
                                CurrentUserProvider currentUserProvider, ClientIpResolver clientIpResolver) {
        this.followService = followService;
        this.followQuery = followQuery;
        this.ownerResolver = ownerResolver;
        this.memberSummaryQuery = memberSummaryQuery;
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/@{handle}/followers")
    public String followers(@PathVariable("handle") String handle, @RequestParam(value = "cursor", required = false) String cursor,
                            Model model) {
        return page(handle, true, cursor, model);
    }

    @GetMapping("/@{handle}/following")
    public String following(@PathVariable("handle") String handle, @RequestParam(value = "cursor", required = false) String cursor,
                            Model model) {
        return page(handle, false, cursor, model);
    }

    private String page(String handle, boolean followers, String cursor, Model model) {
        BlogOwner owner = ownerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        Long viewer = currentUserProvider.current().map(CurrentUser::memberId).orElse(null);
        FollowQuery.FollowPage page;
        try {
            page = followQuery.page(owner.memberId(), followers, viewer, cursor);
        } catch (PostContentException e) {
            page = followQuery.page(owner.memberId(), followers, viewer, null);
            cursor = null;
        }
        FollowQuery.Counts counts = followQuery.counts(owner.memberId());
        String base = "/@" + owner.handle() + (followers ? "/followers" : "/following");
        model.addAttribute("owner", owner);
        model.addAttribute("followers", followers);
        model.addAttribute("count", followers ? counts.followers() : counts.following());
        model.addAttribute("page", page);
        model.addAttribute("base", base);
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("loginRedirect", "/login?redirect=" + base);
        return "follow/list";
    }

    /** 스크립트 없는 버튼: 처리 뒤 원래 화면(같은 사이트 경로만)으로. */
    @PostMapping("/@{handle}/follow")
    public RedirectView form(@PathVariable("handle") String handle,
                             @RequestParam(value = "following", defaultValue = "true") boolean following,
                             @RequestParam(value = "back", required = false) String back, HttpServletRequest request) {
        followService.set(currentUserProvider.current(), handle, following, clientIpResolver.resolve(request));
        String target = back != null && back.startsWith("/") && !back.startsWith("//") && !back.contains("\\")
                ? back : "/@" + handle;
        return seeOther(target);
    }

    /** 017 새 팔로워 알림의 이동 위치. */
    @GetMapping("/me/followers")
    public RedirectView myFollowers() {
        CurrentUser user = accountGuard.requireLoggedIn(currentUserProvider.current());
        String handle = memberSummaryQuery.findByIds(Set.of(user.memberId())).get(user.memberId()).handle();
        return seeOther("/@" + handle + "/followers");
    }

    private static RedirectView seeOther(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }
}

package com.team.blog.post.web;

import com.team.blog.post.application.CardDates;
import com.team.blog.post.application.ManagePage;
import com.team.blog.post.application.ManageRow;
import com.team.blog.post.application.ManageTab;
import com.team.blog.post.application.PostManageQuery;
import com.team.blog.post.application.PostTrashService;
import com.team.blog.post.application.ViewCountFormat;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.KoreanDateFormatter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 내 글 관리({@code GET /manage/posts}, 011 R-1). 세 탭·20개·개수·필터. 스크립트가 없으면 줄의 폼이
 * {@code POST /manage/posts/{id}/trash|restore|purge}로 처리한 뒤 같은 탭으로 303(R-4).
 */
@Controller
public class ManagePostsController {

    private final PostManageQuery manageQuery;
    private final PostTrashService trashService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ManagePostsController(PostManageQuery manageQuery, PostTrashService trashService,
                                 CurrentUserProvider currentUserProvider, Clock clock) {
        this.manageQuery = manageQuery;
        this.trashService = trashService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/manage/posts")
    public String myPosts(@RequestParam(value = "tab", required = false) String tabParam,
                          @RequestParam(value = "visibility", required = false) String visibility,
                          @RequestParam(value = "cursor", required = false) String cursor, Model model) {
        ManageTab tab = ManageTab.of(tabParam);
        ManagePage page;
        try {
            page = manageQuery.page(currentUserProvider.current(), tab, visibility, cursor);
        } catch (PostContentException e) {
            page = manageQuery.page(currentUserProvider.current(), tab, visibility, null);
            cursor = null;
        }
        Map<String, Long> counts = page.counts() != null ? page.counts()
                : manageQuery.counts(currentUserProvider.current().orElseThrow().memberId());
        Instant now = clock.instant();
        Map<Long, String> savedLabels = new HashMap<>();
        Map<Long, String> dates = new HashMap<>();
        Map<Long, String> views = new HashMap<>();
        Map<Long, Long> daysLeft = new HashMap<>();
        for (ManageRow row : page.items()) {
            savedLabels.put(row.id(), CardDates.label(row.updatedAt(), now));
            dates.put(row.id(), tab == ManageTab.TRASH ? KoreanDateFormatter.monthDay(row.deletedAt())
                    : CardDates.label(row.publishedAt(), now));
            views.put(row.id(), ViewCountFormat.format(row.viewCount()));
            if (row.purgeAt() != null) {
                daysLeft.put(row.id(), Math.max(0, (Duration.between(now, row.purgeAt()).toHours() + 23) / 24));
            }
        }
        model.addAttribute("tab", tab.param());
        model.addAttribute("visibility", visibility == null ? "" : visibility.toLowerCase(java.util.Locale.ROOT));
        model.addAttribute("rows", page.items());
        model.addAttribute("counts", counts);
        model.addAttribute("nextCursor", page.nextCursor());
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("savedLabels", savedLabels);
        model.addAttribute("dates", dates);
        model.addAttribute("views", views);
        model.addAttribute("daysLeft", daysLeft);
        return "post/manage";
    }

    @PostMapping("/manage/posts/{postId}/trash")
    public RedirectView trash(@PathVariable long postId, @RequestParam(value = "tab", required = false) String tab) {
        trashService.trash(currentUserProvider.current(), postId);
        return back(tab);
    }

    @PostMapping("/manage/posts/{postId}/restore")
    public RedirectView restore(@PathVariable long postId, @RequestParam(value = "tab", required = false) String tab) {
        trashService.restore(currentUserProvider.current(), postId);
        return back(tab);
    }

    @PostMapping("/manage/posts/{postId}/purge")
    public RedirectView purge(@PathVariable long postId, @RequestParam(value = "tab", required = false) String tab) {
        trashService.purge(currentUserProvider.current(), postId);
        return back(tab);
    }

    private static RedirectView back(String tab) {
        RedirectView view = new RedirectView("/manage/posts?tab=" + ManageTab.of(tab).param());
        view.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
        return view;
    }
}

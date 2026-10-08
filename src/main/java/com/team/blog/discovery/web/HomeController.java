package com.team.blog.discovery.web;

import com.team.blog.discovery.application.TrendingService;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.SnapshotExpiredException;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 홈({@code GET /}): 전체 공개 글 목록 첫 9개를 서버가 완성해서 보낸다(009 FR-008). 스크립트가 없으면 [더 보기]가
 * {@code /?cursor=…} 링크로 다음 9개를 보여 준다. 잘못된 커서면 처음부터 보여 준다(API는 400).
 * 019: {@code ?tab=trending}이면 트렌딩(스냅샷 커서). 보던 순위가 사라졌으면 "순위가 새로 바뀌었어요"와 함께 처음부터.
 * 로그아웃 직후에는 1회용 플래시 {@code logoutCleanupMemberId}가 들어와 브라우저 임시 데이터 정리를 한 번 더 실행한다(001).
 */
@Controller
public class HomeController {

    /** 시안(10-08) 소개 사진: static/images/hero.jpg가 있으면 배경으로, 없으면 그린 고양이 그림. */
    private static final boolean HERO_PHOTO = new org.springframework.core.io.ClassPathResource("static/images/hero.jpg").exists();

    private final PostListQuery listQuery;
    private final TrendingService trendingService;
    private final Clock clock;

    public HomeController(PostListQuery listQuery, TrendingService trendingService, Clock clock) {
        this.listQuery = listQuery;
        this.trendingService = trendingService;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(@RequestParam(value = "cursor", required = false) String cursor,
                       @RequestParam(value = "tab", required = false) String tab,
                       @RequestParam(value = "expired", required = false) String expired, Model model) {
        boolean trending = "trending".equals(tab);
        boolean snapshotExpired = expired != null;
        CardPage page;
        try {
            page = trending ? trendingService.page(cursor) : listQuery.feed(cursor);
        } catch (PostContentException | SnapshotExpiredException e) {
            snapshotExpired = snapshotExpired || e instanceof SnapshotExpiredException;
            page = trending ? trendingService.page(null) : listQuery.feed(null);
            cursor = null;
        }
        CardModel.fill(model, page, clock.instant());
        model.addAttribute("tab", trending ? "trending" : "latest");
        model.addAttribute("snapshotExpired", trending && snapshotExpired);
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", trending ? "/api/posts/trending" : "/api/posts");
        model.addAttribute("moreLinkBase", trending ? "?tab=trending&cursor=" : "?cursor=");
        model.addAttribute("heroPhoto", HERO_PHOTO);
        return "home";
    }

    @GetMapping("/api/posts/trending")
    @ResponseBody
    public CardPage trending(@RequestParam(value = "cursor", required = false) String cursor) {
        return trendingService.page(cursor);
    }
}

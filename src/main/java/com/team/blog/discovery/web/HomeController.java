package com.team.blog.discovery.web;

import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.shared.error.PostContentException;
import java.time.Clock;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 홈({@code GET /}): 전체 공개 글 목록 첫 9개를 서버가 완성해서 보낸다(009 FR-008). 스크립트가 없으면 [더 보기]가
 * {@code /?cursor=…} 링크로 다음 9개를 보여 준다. 잘못된 커서면 처음부터 보여 준다(API는 400).
 * 로그아웃 직후에는 1회용 플래시 {@code logoutCleanupMemberId}가 들어와 브라우저 임시 데이터 정리를 한 번 더 실행한다(001).
 */
@Controller
public class HomeController {

    private final PostListQuery listQuery;
    private final Clock clock;

    public HomeController(PostListQuery listQuery, Clock clock) {
        this.listQuery = listQuery;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(@RequestParam(value = "cursor", required = false) String cursor, Model model) {
        CardPage page;
        try {
            page = listQuery.feed(cursor);
        } catch (PostContentException e) {
            page = listQuery.feed(null);
            cursor = null;
        }
        CardModel.fill(model, page, clock.instant());
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", "/api/posts");
        return "home";
    }
}

package com.team.blog.discovery.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 홈({@code GET /}) 자리표시. 글 목록·피드는 이후 기능이 채운다. 로그아웃 직후에는 1회용 플래시
 * {@code logoutCleanupMemberId}가 모델에 들어와 브라우저 임시 데이터 정리를 한 번 더 실행한다(001 research R-11).
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}

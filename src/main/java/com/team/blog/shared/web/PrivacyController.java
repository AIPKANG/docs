package com.team.blog.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 개인정보 처리방침(016 FR-018, 021 FR-035). 누구나 볼 수 있는 정적 화면. */
@Controller
public class PrivacyController {

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }
}

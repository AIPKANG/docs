package com.team.blog.account.web;

import com.team.blog.account.application.ProfileService;
import com.team.blog.account.application.ProfileView;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.KoreanDateFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 설정 화면({@code GET /settings}, 11 §2). 비회원은 {@code ProfileService}가 {@code LoginRequiredException}을 던져
 * 로그인 화면으로 303 이동한다(FR-001). 저장은 화면 JS가 JSON API를 부른다.
 */
@Controller
public class SettingsController {

    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public SettingsController(ProfileService profileService, CurrentUserProvider currentUserProvider) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        ProfileView profile = profileService.view(currentUserProvider.current());
        model.addAttribute("profile", profile);
        model.addAttribute("avatar", profile.avatar());
        model.addAttribute("nicknameNextAllowed", profile.nicknameNextAllowedAt() == null ? null
                : KoreanDateFormatter.monthDay(profile.nicknameNextAllowedAt()));
        return "settings/settings";
    }
}

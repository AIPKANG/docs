package com.team.blog.account.web;

import com.team.blog.account.application.ProfileService;
import com.team.blog.account.application.ProfileView;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.KoreanDateFormatter;
import com.team.blog.shared.web.Redirects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private final AccountGuard accountGuard;

    public SettingsController(ProfileService profileService, CurrentUserProvider currentUserProvider,
                              AccountGuard accountGuard) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
        this.accountGuard = accountGuard;
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

    /**
     * 소셜 가입 직후 사진 복사 화면(FR-021, research R-11). 세션에 남긴 거른 사진 주소를 <b>한 번</b> 꺼내 지우고 화면에 넘긴다.
     * 브라우저가 사진을 받아 256×256으로 바꿔 일반 업로드 흐름으로 올리고 프로필에 연결한다. 값이 없으면 홈으로.
     */
    @GetMapping("/settings/social-picture")
    public Object socialPicture(HttpServletRequest request, Model model) {
        accountGuard.requireLoggedIn(currentUserProvider.current());
        HttpSession session = request.getSession(false);
        Object url = session == null ? null : session.getAttribute(SocialSignupController.PENDING_PROFILE_PICTURE);
        if (!(url instanceof String pictureUrl)) {
            return Redirects.seeOther("/");
        }
        session.removeAttribute(SocialSignupController.PENDING_PROFILE_PICTURE);
        model.addAttribute("pictureUrl", pictureUrl);
        return "settings/social-picture";
    }
}

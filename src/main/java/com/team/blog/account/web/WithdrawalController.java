package com.team.blog.account.web;

import com.team.blog.account.application.WithdrawalService;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.security.LogoutCleanupSuccessHandler;
import com.team.blog.shared.security.RestoreOnlySessionFilter;
import com.team.blog.shared.web.KoreanDateFormatter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 탈퇴 화면·신청(023 FR-001~FR-018)과 복구(FR-019~FR-023). 신청이 끝나면 이 기기도 로그아웃하고(임시 글 정리 플래시 포함)
 * 완료 화면으로 보낸다. 복구 화면은 복구 전용 세션에서만, [복구하기]를 눌러야 복구된다.
 */
@Controller
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    public WithdrawalController(WithdrawalService withdrawalService, CurrentUserProvider currentUserProvider, Clock clock) {
        this.withdrawalService = withdrawalService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/settings/withdraw")
    public String page(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        WithdrawalService.Overview overview = withdrawalService.overview(currentUserProvider.current());
        model.addAttribute("overview", overview);
        model.addAttribute("deadline", KoreanDateFormatter.dateTime(overview.restoreDeadline()));
        return "settings/withdraw";
    }

    @PostMapping("/settings/withdraw")
    public RedirectView form(@RequestParam(value = "confirm", required = false) String confirm,
                             @RequestParam(value = "verification", required = false) String verification,
                             HttpServletRequest request, HttpServletResponse response) {
        Optional<CurrentUser> user = currentUserProvider.current();
        Instant deadline = withdrawalService.request(user, confirm != null, verification);
        logoutHere(user, request, response, deadline);
        return seeOther("/withdrawn");
    }

    @PostMapping("/api/me/withdrawal")
    @ResponseBody
    public Map<String, Object> api(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        Optional<CurrentUser> user = currentUserProvider.current();
        Instant deadline = withdrawalService.request(user, Boolean.TRUE.equals(body.get("confirm")),
                body.get("verification") == null ? null : String.valueOf(body.get("verification")));
        logoutHere(user, request, response, deadline);
        return Map.of("restoreDeadline", deadline);
    }

    /** 이 기기 세션도 끝낸다(다른 기기는 커밋 뒤 리스너가). 완료 화면에 기한, 홈에 임시 글 정리 플래시. */
    private void logoutHere(Optional<CurrentUser> user, HttpServletRequest request, HttpServletResponse response, Instant deadline) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        FlashMap flash = new FlashMap();
        flash.put("withdrawDeadline", KoreanDateFormatter.dateTime(deadline));
        user.ifPresent(u -> flash.put(LogoutCleanupSuccessHandler.FLASH_MEMBER_ID, u.memberId()));
        flash.setTargetRequestPath("/withdrawn");
        flashMapManager.saveOutputFlashMap(flash, request, response);
    }

    @GetMapping("/withdrawn")
    public String done() {
        return "auth/withdrawn";
    }

    @PostMapping(RestoreOnlySessionFilter.RESTORE_PATH)
    public RedirectView restore(HttpServletRequest request, HttpServletResponse response) {
        CurrentUser user = currentUserProvider.current().orElse(null);
        if (user == null || !RestoreOnlySessionFilter.isRestoreOnly(request)) {
            return seeOther("/");
        }
        withdrawalService.restore(user.memberId());
        request.getSession().removeAttribute(RestoreOnlySessionFilter.RESTORE_ONLY);
        FlashMap flash = new FlashMap();
        flash.put("welcomeBack", true);
        flash.setTargetRequestPath("/");
        flashMapManager.saveOutputFlashMap(flash, request, response);
        return seeOther("/");
    }

    /** 복구 화면의 기한·남은 일수(FR-019). */
    public void restoreModel(Model model, long memberId) {
        withdrawalService.restoreDeadline(memberId).ifPresent(d -> {
            model.addAttribute("restoreDeadline", KoreanDateFormatter.dateTime(d));
            model.addAttribute("restoreDaysLeft", Math.max(0, Duration.between(clock.instant(), d).toDays()));
        });
    }

    private static RedirectView seeOther(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }
}

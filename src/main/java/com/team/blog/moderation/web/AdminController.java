package com.team.blog.moderation.web;

import com.team.blog.moderation.application.ModerationQuery;
import com.team.blog.moderation.application.ModerationService;
import com.team.blog.moderation.application.ReportReason;
import com.team.blog.moderation.application.SuspensionService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 관리자 화면(43 §4·§5): 신고 관리(대기·처리됨, 처리), 회원 관리(정지·해제). 관리자가 아니면 404, 비회원은 로그인.
 * 화면은 스크립트 없는 폼, 같은 동작의 JSON API도 둔다.
 */
@Controller
public class AdminController {

    private final ModerationQuery query;
    private final ModerationService moderation;
    private final SuspensionService suspension;
    private final CurrentUserProvider currentUserProvider;

    public AdminController(ModerationQuery query, ModerationService moderation, SuspensionService suspension,
                           CurrentUserProvider currentUserProvider) {
        this.query = query;
        this.moderation = moderation;
        this.suspension = suspension;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/admin/reports")
    public String reports(@RequestParam(value = "tab", defaultValue = "pending") String tab, Model model,
                          HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        boolean done = "done".equals(tab);
        model.addAttribute("tab", done ? "done" : "pending");
        model.addAttribute("cases", done ? query.done(currentUserProvider.current()) : query.pending(currentUserProvider.current()));
        model.addAttribute("pageNoindex", true);
        return "admin/reports";
    }

    @GetMapping("/admin/reports/{caseId}")
    public String report(@PathVariable long caseId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("detail", query.detail(currentUserProvider.current(), caseId).orElseThrow(NotFoundException::new));
        model.addAttribute("reasons", ReportReason.values());
        model.addAttribute("pageNoindex", true);
        return "admin/report";
    }

    /** 숨기기(+ 선택: 작성자 정지). */
    @PostMapping("/admin/reports/{caseId}/hide")
    public RedirectView hideForm(@PathVariable long caseId, @RequestParam("reason") String reason,
                                 @RequestParam(value = "suspendPeriod", required = false) String period,
                                 @RequestParam(value = "suspendReason", required = false) String suspendReason,
                                 @RequestParam(value = "authorId", required = false) Long authorId) {
        moderation.hide(currentUserProvider.current(), caseId, reason);
        if (period != null && !period.isEmpty() && authorId != null) {
            suspension.suspend(currentUserProvider.current(), authorId, period, suspendReason);
        }
        return seeOther("/admin/reports");
    }

    @PostMapping("/admin/reports/{caseId}/reject")
    public RedirectView rejectForm(@PathVariable long caseId) {
        moderation.reject(currentUserProvider.current(), caseId);
        return seeOther("/admin/reports");
    }

    @PostMapping("/admin/reports/{caseId}/unhide")
    public RedirectView unhideForm(@PathVariable long caseId) {
        moderation.unhide(currentUserProvider.current(), caseId);
        return seeOther("/admin/reports?tab=done");
    }

    @GetMapping("/admin/members")
    public String members(@RequestParam(value = "q", required = false) String q, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("members", query.members(currentUserProvider.current(), q));
        model.addAttribute("pageNoindex", true);
        return "admin/members";
    }

    @PostMapping("/admin/members/{memberId}/suspend")
    public RedirectView suspendForm(@PathVariable long memberId, @RequestParam("period") String period,
                                    @RequestParam("reason") String reason) {
        suspension.suspend(currentUserProvider.current(), memberId, period, reason);
        return seeOther("/admin/members");
    }

    @PostMapping("/admin/members/{memberId}/lift")
    public RedirectView liftForm(@PathVariable long memberId) {
        suspension.lift(currentUserProvider.current(), memberId);
        return seeOther("/admin/members");
    }

    // ----- JSON API -----

    @PostMapping("/api/admin/reports/{caseId}/hide")
    @ResponseBody
    public ResponseEntity<Void> hide(@PathVariable long caseId, @RequestBody Map<String, String> body) {
        moderation.hide(currentUserProvider.current(), caseId, body.get("reason"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/reports/{caseId}/reject")
    @ResponseBody
    public ResponseEntity<Void> reject(@PathVariable long caseId) {
        moderation.reject(currentUserProvider.current(), caseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/reports/{caseId}/unhide")
    @ResponseBody
    public ResponseEntity<Void> unhide(@PathVariable long caseId) {
        moderation.unhide(currentUserProvider.current(), caseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/members/{memberId}/suspension")
    @ResponseBody
    public ResponseEntity<Void> suspend(@PathVariable long memberId, @RequestBody Map<String, String> body) {
        suspension.suspend(currentUserProvider.current(), memberId, body.get("period"), body.get("reason"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/members/{memberId}/suspension")
    @ResponseBody
    public ResponseEntity<Void> lift(@PathVariable long memberId) {
        suspension.lift(currentUserProvider.current(), memberId);
        return ResponseEntity.noContent().build();
    }

    private static RedirectView seeOther(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }
}

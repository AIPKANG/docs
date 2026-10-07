package com.team.blog.account.web;

import com.team.blog.account.application.AccountSettingsService;
import com.team.blog.account.application.PasswordChangeCommand;
import com.team.blog.account.application.PasswordChangeService;
import com.team.blog.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 계정 API(contracts/web-routes.md §4): 비밀번호 변경, 새 글 기본 공개 범위. */
@RestController
@RequestMapping("/api/me")
public class AccountApiController {

    public record PasswordChangeRequest(String currentPassword, String newPassword, String newPasswordConfirm) {

        @Override
        public String toString() {
            return "PasswordChangeRequest[***]";
        }
    }

    public record SettingsResponse(String defaultVisibility) {
    }

    private final PasswordChangeService passwordChangeService;
    private final AccountSettingsService accountSettingsService;
    private final CurrentUserProvider currentUserProvider;

    public AccountApiController(PasswordChangeService passwordChangeService,
                                AccountSettingsService accountSettingsService, CurrentUserProvider currentUserProvider) {
        this.passwordChangeService = passwordChangeService;
        this.accountSettingsService = accountSettingsService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 성공하면 다른 기기 세션은 서비스가 커밋 후 지우고(지금 세션은 남김), 여기서 지금 세션 ID를 새로 발급한다(FR-027).
     * 다른 세션을 지울 때 남길 ID는 바꾸기 전 ID다 — Spring Session은 응답을 마칠 때 이전 ID의 저장소 키를 새 ID로 옮긴다.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String currentSessionId = session == null ? null : session.getId();
        passwordChangeService.change(currentUserProvider.current(),
                new PasswordChangeCommand(body.currentPassword(), body.newPassword(), body.newPasswordConfirm()),
                currentSessionId);
        if (session != null) {
            request.changeSessionId();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/settings")
    public SettingsResponse changeSettings(@RequestBody JsonNode body) {
        JsonNode value = body == null ? null : body.get("defaultVisibility");
        String visibility = value != null && value.isString() ? value.stringValue() : null;
        return new SettingsResponse(accountSettingsService.changeDefaultVisibility(currentUserProvider.current(), visibility));
    }
}

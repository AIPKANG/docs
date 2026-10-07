package com.team.blog.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * 로그아웃 성공 → {@code 303 /}. 홈 화면에 1회용 플래시(memberId)를 넘겨 브라우저 임시 데이터 정리를 한 번 더 실행하게 한다
 * (research R-11: 버튼 스크립트 실패 대비).
 */
public class LogoutCleanupSuccessHandler implements LogoutSuccessHandler {

    public static final String FLASH_MEMBER_ID = "logoutCleanupMemberId";

    private final SessionFlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            try {
                long memberId = Long.parseLong(authentication.getName());
                FlashMap flash = new FlashMap();
                flash.put(FLASH_MEMBER_ID, memberId);
                flash.setTargetRequestPath("/");
                flashMapManager.saveOutputFlashMap(flash, request, response);
            } catch (NumberFormatException ignored) {
                // principal 이름이 memberId가 아니면 정리할 범위가 없다
            }
        }
        AuthResponses.seeOther(request, response, "/");
    }
}

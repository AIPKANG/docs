package com.team.blog.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 소셜 로그인 뒤 공급자 API를 다시 부르지 않으므로 액세스 토큰을 세션·메모리에 보관하지 않는다(FR-014 취지).
 */
public class NoStoredAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                                                                     Authentication principal,
                                                                     HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        // 보관하지 않는다
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                       HttpServletRequest request, HttpServletResponse response) {
        // 보관한 것이 없다
    }
}

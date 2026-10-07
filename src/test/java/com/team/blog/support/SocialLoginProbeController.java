package com.team.blog.support;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import com.team.blog.account.infra.SocialOAuth2User;
import com.team.blog.shared.security.SocialLoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용(001 T152·T153): 실제 공급자 없이 "소셜 인증 성공" 직후를 재현한다. 공급자가 돌려준 것과 같은 모양의 principal로
 * 실제 {@link SocialLoginSuccessHandler}를 실제 요청(세션 = Spring Session Redis) 안에서 부른다.
 */
@RestController
public class SocialLoginProbeController {

    private final SocialLoginSuccessHandler successHandler;

    public SocialLoginProbeController(SocialLoginSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @PostMapping("/test/social-login")
    public void login(@RequestParam("provider") Provider provider, @RequestParam("id") String id,
                      @RequestParam(name = "email", required = false) String email,
                      @RequestParam(name = "name", required = false) String name,
                      @RequestParam(name = "picture", required = false) String picture,
                      HttpServletRequest request, HttpServletResponse response) throws Exception {
        SocialProfile profile = new SocialProfile(provider, id, email, name, picture);
        SocialOAuth2User principal = new SocialOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("id", id), "id", profile);
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, principal.getAuthorities(),
                provider.name().toLowerCase());
        successHandler.onAuthenticationSuccess(request, response, token);
    }
}

package com.team.blog.account.infra;

import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialProfile;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * GitHub 사용자 정리(research R-7, 002 research R-13): 숫자 {@code id}로 식별(로그인 이름 변경과 무관),
 * {@code /user/emails}의 {@code primary && verified} 이메일만 사용, 표시 이름은 {@code name}이 비면 {@code login}.
 */
@Component
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2UserService.class);
    static final String EMAILS_URI = "https://api.github.com/user/emails";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final Function<OAuth2UserRequest, List<Map<String, Object>>> emailsFetcher;

    public GitHubOAuth2UserService() {
        this(new DefaultOAuth2UserService(), GitHubOAuth2UserService::fetchEmails);
    }

    /** 테스트용: 공급자 호출을 고정 응답으로 바꾼다. */
    public GitHubOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
                                   Function<OAuth2UserRequest, List<Map<String, Object>>> emailsFetcher) {
        this.delegate = delegate;
        this.emailsFetcher = emailsFetcher;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = delegate.loadUser(userRequest);
        SocialProfile profile = toProfile(user.getAttributes(), emailsFetcher.apply(userRequest));
        return new SocialOAuth2User(user.getAuthorities(), user.getAttributes(), "id", profile);
    }

    public static SocialProfile toProfile(Map<String, Object> attributes, List<Map<String, Object>> emails) {
        Object id = attributes.get("id");
        if (id == null) {
            throw new IllegalArgumentException("GitHub user has no id");
        }
        String providerUserId = id instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(id);
        String name = attributes.get("name") instanceof String s && !s.isBlank() ? s : (String) attributes.get("login");
        String email = null;
        if (emails != null) {
            for (Map<String, Object> entry : emails) {
                if (Boolean.TRUE.equals(entry.get("primary")) && Boolean.TRUE.equals(entry.get("verified"))
                        && entry.get("email") instanceof String e && !e.isBlank()) {
                    email = e.strip().toLowerCase(Locale.ROOT);
                    break;
                }
            }
        }
        String picture = attributes.get("avatar_url") instanceof String a ? a : null;
        return new SocialProfile(Provider.GITHUB, providerUserId, email, name, picture);
    }

    private static List<Map<String, Object>> fetchEmails(OAuth2UserRequest request) {
        try {
            return RestClient.create().get()
                    .uri(EMAILS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        } catch (RuntimeException e) {
            // 이메일을 못 받아도 가입 마무리 화면에서 이메일을 입력받는다
            log.warn("GitHub 이메일 조회 실패: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }
}

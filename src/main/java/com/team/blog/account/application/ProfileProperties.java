package com.team.blog.account.application;

import com.team.blog.account.domain.Provider;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 003-profile 수치(헌법 II). 기본값은 {@code application.yml}의 {@code blog.profile.*}와 같다.
 *
 * @param bio           소개 길이·줄 수(11 §3)
 * @param socialPicture 소셜 사진 허용 호스트·요청 크기(11 §4-2)
 */
@ConfigurationProperties("blog.profile")
public record ProfileProperties(@DefaultValue Bio bio, @DefaultValue SocialPicture socialPicture) {

    /** @param maxLength 코드 포인트 수 상한 @param maxLines 줄 수 상한 */
    public record Bio(@DefaultValue("200") int maxLength, @DefaultValue("4") int maxLines) {
    }

    /**
     * @param size         소셜 서비스에 요청할 사진 크기(px)
     * @param allowedHosts 공급자별 허용 호스트(정확히 일치, HTTPS만)
     */
    public record SocialPicture(@DefaultValue("256") int size, Map<String, String> allowedHosts) {

        public SocialPicture {
            if (allowedHosts == null || allowedHosts.isEmpty()) {
                allowedHosts = Map.of("GOOGLE", "lh3.googleusercontent.com", "GITHUB", "avatars.githubusercontent.com");
            }
            allowedHosts = Map.copyOf(allowedHosts);
        }

        public Map<Provider, String> hostsByProvider() {
            Map<Provider, String> result = new EnumMap<>(Provider.class);
            allowedHosts.forEach((provider, host) -> result.put(Provider.valueOf(provider), host));
            return result;
        }
    }
}

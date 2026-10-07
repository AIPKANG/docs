package com.team.blog.account.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 소셜 사진 주소 거르기(11 §4-2, FR-020, 003 research R-11). 순수 함수.
 *
 * <p>HTTPS, userinfo 없음, 포트 없음(또는 443), 호스트가 공급자별 허용 호스트와 <b>정확히</b> 같을 때만 통과한다.
 * 통과한 주소는 256 크기로 요청하도록 바꾼다 — Google: 경로 끝 크기 매개변수({@code =s96-c} 등)를 {@code =s256-c}로,
 * GitHub: {@code s} 쿼리를 {@code s=256}으로. 서버는 이 주소로 요청하지 않고 저장하지도 않는다(화면 전달용).
 */
public final class SocialPictureUrlPolicy {

    private static final Pattern GOOGLE_SIZE = Pattern.compile("=[swh]\\d+[^/=]*$");

    private final Map<Provider, String> allowedHosts;
    private final int size;

    public SocialPictureUrlPolicy(Map<Provider, String> allowedHosts, int size) {
        this.allowedHosts = Map.copyOf(allowedHosts);
        this.size = size;
    }

    public Optional<String> sanitize(Provider provider, String raw) {
        if (provider == null || raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String allowed = allowedHosts.get(provider);
        if (allowed == null) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return Optional.empty();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(allowed)) {
            return Optional.empty();
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return Optional.of(switch (provider) {
            case GOOGLE -> "https://" + host + GOOGLE_SIZE.matcher(path).replaceFirst("") + "=s" + size + "-c";
            case GITHUB -> "https://" + host + path + "?" + githubQuery(uri.getRawQuery());
            case LOCAL -> throw new IllegalStateException("LOCAL has no social picture");
        });
    }

    private String githubQuery(String rawQuery) {
        List<String> parts = new ArrayList<>();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String part : rawQuery.split("&")) {
                String name = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
                if (!part.isEmpty() && !name.equals("s") && !name.equals("size")) {
                    parts.add(part);
                }
            }
        }
        parts.add("s=" + size);
        return String.join("&", parts);
    }
}

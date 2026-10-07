package com.team.blog.shared.web;

import com.team.blog.account.application.HandleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

/**
 * {@code /@{handle}}, {@code /@{handle}/**}의 handle 부분에 대문자가 있으면 그 부분만 소문자로 바꿔 301로 보낸다
 * (나머지 경로·쿼리 유지, 08 §6). 존재 여부를 보기 전에, 가장 앞 순서로 실행한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HandlePathCanonicalizer extends OncePerRequestFilter {

    private static final String PREFIX = "/@";

    private final HandleService handleService;

    public HandlePathCanonicalizer(HandleService handleService) {
        this.handleService = handleService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        if (path.startsWith(PREFIX)) {
            int end = path.indexOf('/', PREFIX.length());
            String rawSegment = end < 0 ? path.substring(PREFIX.length()) : path.substring(PREFIX.length(), end);
            String rest = end < 0 ? "" : path.substring(end);
            String handle;
            try {
                handle = UriUtils.decode(rawSegment, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                handle = rawSegment;
            }
            Optional<String> canonical = handleService.canonicalPath(handle);
            if (canonical.isPresent()) {
                String query = request.getQueryString();
                String location = contextPath + PREFIX + UriUtils.encodePathSegment(canonical.get(), StandardCharsets.UTF_8)
                        + rest + (query == null ? "" : "?" + query);
                response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                response.setHeader(HttpHeaders.LOCATION, location);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

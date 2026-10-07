package com.team.blog.post.web;

import com.team.blog.post.application.PostProperties;
import com.team.blog.shared.error.ErrorResponse;
import com.team.blog.shared.error.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글 저장 요청 본문 1MB 제한(FR-008) → 413 {@code PAYLOAD_TOO_LARGE}. {@code Content-Length}가 있으면 그것으로, 없으면
 * 최대 한도+1바이트까지만 읽어 판정하고 읽은 본문을 그대로 다음 단계에 넘긴다.
 */
@Component
public class AutosaveBodyLimitFilter extends OncePerRequestFilter {

    private final PostProperties properties;
    private final MessageSource messageSource;
    private final JsonMapper jsonMapper;

    public AutosaveBodyLimitFilter(PostProperties properties, MessageSource messageSource, JsonMapper jsonMapper) {
        this.properties = properties;
        this.messageSource = messageSource;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        return !(path.startsWith("/api/posts") && ("PUT".equals(method) || "POST".equals(method)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long max = properties.autosave().maxBodyBytes();
        long declared = request.getContentLengthLong();
        if (declared > max) {
            reject(response);
            return;
        }
        if (declared >= 0) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
        if (body.length > max) {
            reject(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        String message = messageSource.getMessage("error." + PayloadTooLargeException.CODE, null,
                PayloadTooLargeException.CODE, Locale.KOREAN);
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(PayloadTooLargeException.CODE, message)));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}

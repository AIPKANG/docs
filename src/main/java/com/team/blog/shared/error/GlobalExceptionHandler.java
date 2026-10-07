package com.team.blog.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.team.blog.shared.web.KoreanDateFormatter;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 매핑. 요청이 {@code /api/**}이거나 {@code Accept: application/json}이면 {@link ErrorResponse} JSON,
 * 아니면 SSR 화면으로 응답한다. 각 스토리가 자기 예외 매핑을 이 클래스에 추가한다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public Object notFound(HttpServletRequest request) {
        if (isApi(request)) {
            return json(HttpStatus.NOT_FOUND, error(NotFoundException.CODE));
        }
        ModelAndView view = new ModelAndView("error/404");
        view.setStatus(HttpStatus.NOT_FOUND);
        return view;
    }

    @ExceptionHandler(LoginRequiredException.class)
    public Object loginRequired(HttpServletRequest request) {
        if (isApi(request)) {
            return json(HttpStatus.UNAUTHORIZED, error(LoginRequiredException.CODE));
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/login?redirect=" + URLEncoder.encode(currentRelativePath(request), StandardCharsets.UTF_8))
                .build();
    }

    // ----- 001 US1: 계정 상태(42 §3·§4) -----

    @ExceptionHandler(AccountStatusException.class)
    public Object accountStatus(AccountStatusException e, HttpServletRequest request) {
        String code = e.getReason().name();
        if (isApi(request)) {
            return json(HttpStatus.FORBIDDEN, error(code));
        }
        return switch (e.getReason()) {
            case EMAIL_NOT_VERIFIED -> {
                ModelAndView view = new ModelAndView("auth/forbidden-unverified");
                view.addObject("message", message(code));
                view.setStatus(HttpStatus.FORBIDDEN);
                yield view;
            }
            case ACCOUNT_WITHDRAWN -> seeOther("/account/restore");
            case ACCOUNT_SUSPENDED -> seeOther("/login?suspended");
        };
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> rateLimited(RateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(error(RateLimitedException.CODE));
    }

    // ----- 002 US1: 블로그 주소 -----

    @ExceptionHandler(HandleViolationException.class)
    public ResponseEntity<ErrorResponse> handleViolation(HandleViolationException e) {
        return json(HttpStatus.BAD_REQUEST, error(e.getCode().name()).withSuggestion(e.getSuggestion()));
    }

    @ExceptionHandler(HandleTakenException.class)
    public ResponseEntity<ErrorResponse> handleTaken(HandleTakenException e) {
        return json(HttpStatus.CONFLICT, error(HandleTakenException.CODE, e.getSuggestion()).withSuggestion(e.getSuggestion()));
    }

    // ----- 002 US2: 닉네임 -----

    @ExceptionHandler(NicknameViolationException.class)
    public ResponseEntity<ErrorResponse> nicknameViolation(NicknameViolationException e) {
        String code = e.getCode().name();
        if (e.isConcurrent()) {
            return json(HttpStatus.CONFLICT, ErrorResponse.of(code, message(code + "_CONCURRENT")));
        }
        return json(HttpStatus.BAD_REQUEST, error(code));
    }

    // ----- 002 US3: 닉네임 변경 30일 -----

    @ExceptionHandler(NicknameChangeTooSoonException.class)
    public ResponseEntity<ErrorResponse> nicknameChangeTooSoon(NicknameChangeTooSoonException e) {
        String date = KoreanDateFormatter.monthDay(e.getNextAllowedAt());
        return json(HttpStatus.CONFLICT, error(NicknameChangeTooSoonException.CODE, date).withNextAllowedAt(e.getNextAllowedAt()));
    }

    // ----- 003: 프로필·계정 설정 칸별 오류 -----

    @ExceptionHandler(ProfileValidationException.class)
    public ResponseEntity<ErrorResponse> profileValidation(ProfileValidationException e) {
        java.util.List<ErrorResponse.Item> items = e.getErrors().stream()
                .map(error -> new ErrorResponse.Item(error.field(), error.code(), fieldMessage(error), error.nextAllowedAt()))
                .toList();
        HttpStatus status = e.isConflictOnly() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return json(status, error(ProfileValidationException.CODE).withErrors(items));
    }

    private String fieldMessage(FieldError error) {
        Object[] args = error.nextAllowedAt() == null ? new Object[0]
                : new Object[] {KoreanDateFormatter.monthDay(error.nextAllowedAt())};
        return messageSource.getMessage(error.messageKey(), args, error.code(), Locale.KOREAN);
    }

    // ----- 도우미 -----

    protected static ResponseEntity<Void> seeOther(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location).build();
    }

    protected ErrorResponse error(String code, Object... args) {
        return ErrorResponse.of(code, message(code, args));
    }

    protected String message(String code, Object... args) {
        return messageSource.getMessage("error." + code, args, code, Locale.KOREAN);
    }

    protected static ResponseEntity<ErrorResponse> json(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    static boolean isApi(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = uri.substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private static String currentRelativePath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        return query == null ? path : path + "?" + query;
    }
}

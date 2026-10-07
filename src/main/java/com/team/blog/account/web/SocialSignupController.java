package com.team.blog.account.web;

import com.team.blog.account.application.AgreementRequiredException;
import com.team.blog.account.application.AuthProperties;
import com.team.blog.account.application.HandleService;
import com.team.blog.account.application.InvalidEmailException;
import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.account.application.PendingExpiredException;
import com.team.blog.account.application.ProfileProperties;
import com.team.blog.account.application.SocialLoginService;
import com.team.blog.account.application.SocialSignupService;
import com.team.blog.account.domain.HandlePrefix;
import com.team.blog.account.domain.PendingSocialSignup;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.SocialPictureUrlPolicy;
import com.team.blog.shared.error.HandleTakenException;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.security.LoginSessionEstablisher;
import com.team.blog.shared.web.Redirects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 소셜 가입 마무리 화면(contracts/web-routes.md §3, FR-021~FR-023, FR-033).
 * 대기 정보는 세션 속성 {@link PendingSocialSignup#SESSION_ATTRIBUTE}에만 있고 10분이 지나면 무효다.
 */
@Controller
public class SocialSignupController {

    /**
     * 프로필 사진 복사 단계(003 {@code /settings/social-picture})로 넘길 사진 주소. {@link SocialPictureUrlPolicy}를 통과한
     * 256 크기 주소만 담고, 그 화면이 한 번 꺼내 지운다(DB에 저장하지 않음).
     */
    public static final String PENDING_PROFILE_PICTURE = "PENDING_PROFILE_PICTURE_URL";

    private final SocialSignupService socialSignupService;
    private final SocialLoginService socialLoginService;
    private final HandleService handleService;
    private final NicknamePolicy nicknamePolicy;
    private final LoginSessionEstablisher loginSessionEstablisher;
    private final AuthProperties properties;
    private final MessageSource messageSource;
    private final Clock clock;
    private final SocialPictureUrlPolicy pictureUrlPolicy;

    public SocialSignupController(SocialSignupService socialSignupService, SocialLoginService socialLoginService,
                                  HandleService handleService, NicknamePolicy nicknamePolicy,
                                  LoginSessionEstablisher loginSessionEstablisher, AuthProperties properties,
                                  MessageSource messageSource, Clock clock, ProfileProperties profileProperties) {
        this.socialSignupService = socialSignupService;
        this.socialLoginService = socialLoginService;
        this.handleService = handleService;
        this.nicknamePolicy = nicknamePolicy;
        this.loginSessionEstablisher = loginSessionEstablisher;
        this.properties = properties;
        this.messageSource = messageSource;
        this.clock = clock;
        this.pictureUrlPolicy = new SocialPictureUrlPolicy(profileProperties.socialPicture().hostsByProvider(),
                profileProperties.socialPicture().size());
    }

    @GetMapping("/signup/social")
    public Object form(HttpServletRequest request) {
        PendingSocialSignup pending = currentPending(request);
        if (pending == null) {
            return Redirects.seeOther("/login?social");
        }
        String prefix = HandlePrefix.of(pending.provider()).value();
        String prefilled = handleService.prefill(pending.verifiedEmail(), pending.provider());
        String nickname = nicknamePolicy.suggestFromSocialName(pending.displayName()).orElse("");
        SocialSignupForm form = new SocialSignupForm(nickname, stripPrefix(prefilled, prefix), false, false, true, null);
        ModelAndView view = page(pending, form);
        if (nickname.isEmpty()) {
            view.addObject("nicknameHint", "닉네임을 입력해 주세요");
        }
        return view;
    }

    @PostMapping("/signup/social")
    public Object complete(@ModelAttribute SocialSignupForm form, HttpServletRequest request,
                           HttpServletResponse response) {
        PendingSocialSignup pending = currentPending(request);
        if (pending == null) {
            return Redirects.seeOther("/login?social");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String handleSuggestion = null;
        try {
            SocialSignupService.Result result = socialSignupService.complete(pending, form.toCommand());
            loginSessionEstablisher.establish(result.memberId(), "USER", request, response);
            HttpSession session = request.getSession();
            session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
            // 003: 사진 사용 + 허용 호스트 사진 + 공급자가 인증한 이메일(인증 전 회원은 사진을 올릴 수 없음, research U-6)
            Optional<String> picture = picture(pending);
            if (result.created() && Boolean.TRUE.equals(form.useSocialPicture()) && picture.isPresent()
                    && pending.hasVerifiedEmail()) {
                session.setAttribute(PENDING_PROFILE_PICTURE, picture.get());
                return Redirects.seeOther("/settings/social-picture");
            }
            return Redirects.seeOther("/");
        } catch (PendingExpiredException e) {
            clearPending(request);
            return Redirects.seeOther("/login?social");
        } catch (AgreementRequiredException e) {
            errors.put("agreements", message(AgreementRequiredException.CODE));
        } catch (InvalidEmailException e) {
            errors.put("email", message(InvalidEmailException.CODE));
        } catch (HandleViolationException e) {
            errors.put("handle", message(e.getCode().name()));
            handleSuggestion = e.getSuggestion();
        } catch (HandleTakenException e) {
            status = HttpStatus.CONFLICT;
            errors.put("handle", message(HandleTakenException.CODE, e.getSuggestion()));
            handleSuggestion = e.getSuggestion();
        } catch (NicknameViolationException e) {
            String code = e.getCode().name();
            if (e.isConcurrent()) {
                status = HttpStatus.CONFLICT;
                errors.put("nickname", message(code + "_CONCURRENT"));
            } else {
                errors.put("nickname", message(code));
            }
        }
        ModelAndView view = page(pending, form);
        view.setStatus(status);
        view.addObject("errors", errors);
        view.addObject("handleSuggestion", handleSuggestion);
        return view;
    }

    /** [기존 계정으로 로그인]: 대기 정보를 지우고 로그인 화면으로(계정 생성 없음, FR-033). */
    @PostMapping("/signup/social/cancel")
    public Object cancel(HttpServletRequest request) {
        clearPending(request);
        return Redirects.seeOther("/login");
    }

    // ----- 도우미 -----

    private ModelAndView page(PendingSocialSignup pending, SocialSignupForm form) {
        ModelAndView view = new ModelAndView("auth/social-signup");
        List<Provider> sameEmail = socialLoginService.findSameEmailAccounts(pending);
        view.addObject("form", form);
        view.addObject("errors", Map.of());
        view.addObject("providerName", providerName(pending.provider()));
        view.addObject("handlePrefix", HandlePrefix.of(pending.provider()).value());
        view.addObject("needsEmail", !pending.hasVerifiedEmail());
        view.addObject("pictureUrl", picture(pending).orElse(null));
        view.addObject("sameEmailProviders", sameEmail.stream().map(SocialSignupController::providerName).toList());
        return view;
    }

    /** 허용 호스트·HTTPS인 소셜 사진만 256 크기 주소로(FR-020). 그 밖은 화면에 넘기지 않는다. */
    private Optional<String> picture(PendingSocialSignup pending) {
        return pictureUrlPolicy.sanitize(pending.provider(), pending.pictureUrl());
    }

    private PendingSocialSignup currentPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        if (!(session.getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE) instanceof PendingSocialSignup pending)) {
            return null;
        }
        if (pending.isExpired(clock.instant(), properties.pendingSocialTtl())) {
            session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
            return null;
        }
        return pending;
    }

    private static void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        }
    }

    private static String stripPrefix(String handle, String prefix) {
        return handle != null && !prefix.isEmpty() && handle.startsWith(prefix) ? handle.substring(prefix.length()) : handle;
    }

    static String providerName(Provider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case GITHUB -> "GitHub";
            case LOCAL -> "이메일";
        };
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage("error." + code, args, code, Locale.KOREAN);
    }
}

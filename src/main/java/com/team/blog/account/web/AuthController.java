package com.team.blog.account.web;

import com.team.blog.account.application.AgreementRequiredException;
import com.team.blog.account.application.DuplicateEmailException;
import com.team.blog.account.application.EmailSignupService;
import com.team.blog.account.application.EmailVerificationService;
import com.team.blog.account.application.InvalidEmailException;
import com.team.blog.account.application.InvalidPasswordException;
import com.team.blog.account.application.PasswordMismatchException;
import com.team.blog.account.application.WithdrawnAccountExistsException;
import com.team.blog.account.domain.PasswordPolicyViolation;
import com.team.blog.shared.error.HandleTakenException;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.shared.error.LoginRequiredException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.security.LoginSessionEstablisher;
import com.team.blog.shared.security.RedirectTargetValidator;
import com.team.blog.shared.web.Redirects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * 이메일 가입·인증 화면(contracts/web-routes.md §1). 로그인 화면은 US2에서 이 컨트롤러에 더한다.
 */
@Controller
public class AuthController {

    private final EmailSignupService emailSignupService;
    private final EmailVerificationService emailVerificationService;
    private final LoginSessionEstablisher loginSessionEstablisher;
    private final CurrentUserProvider currentUserProvider;
    private final MessageSource messageSource;

    public AuthController(EmailSignupService emailSignupService, EmailVerificationService emailVerificationService,
                          LoginSessionEstablisher loginSessionEstablisher, CurrentUserProvider currentUserProvider,
                          MessageSource messageSource) {
        this.emailSignupService = emailSignupService;
        this.emailVerificationService = emailVerificationService;
        this.loginSessionEstablisher = loginSessionEstablisher;
        this.currentUserProvider = currentUserProvider;
        this.messageSource = messageSource;
    }

    // ----- 가입 -----

    @GetMapping("/signup")
    public ModelAndView signupForm() {
        ModelAndView view = new ModelAndView("auth/signup");
        view.addObject("form", SignupForm.empty());
        view.addObject("errors", Map.of());
        view.addObject("passwordErrors", List.of());
        return view;
    }

    @PostMapping("/signup")
    public Object signup(@ModelAttribute SignupForm form, HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> passwordErrors = List.of();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String handleSuggestion = null;
        boolean emailDuplicate = false;
        try {
            long memberId = emailSignupService.signUp(form.toCommand());
            loginSessionEstablisher.establish(memberId, "USER", request, response);
            return Redirects.seeOther("/signup/verify-sent");
        } catch (AgreementRequiredException e) {
            errors.put("agreements", message(AgreementRequiredException.CODE));
        } catch (InvalidEmailException e) {
            errors.put("email", message(InvalidEmailException.CODE));
        } catch (DuplicateEmailException e) {
            errors.put("email", message(DuplicateEmailException.CODE));
            emailDuplicate = true;
        } catch (WithdrawnAccountExistsException e) {
            errors.put("email", message(WithdrawnAccountExistsException.CODE));
        } catch (InvalidPasswordException e) {
            passwordErrors = passwordMessages(e.getViolations());
            errors.put("password", String.join(" ", passwordErrors));
        } catch (PasswordMismatchException e) {
            errors.put("passwordConfirm", message(PasswordMismatchException.CODE));
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
        ModelAndView view = new ModelAndView("auth/signup");
        view.setStatus(status);
        view.addObject("form", form.withoutPasswords());
        view.addObject("errors", errors);
        view.addObject("passwordErrors", passwordErrors);
        view.addObject("handleSuggestion", handleSuggestion);
        view.addObject("emailDuplicate", emailDuplicate);
        return view;
    }

    @GetMapping("/signup/verify-sent")
    public ModelAndView verifySent() {
        requireUser();
        return new ModelAndView("auth/verify-sent");
    }

    // ----- 로그인 (US2) -----

    static final String SUSPENDED_NOTICE = "LOGIN_SUSPENDED_NOTICE";

    @GetMapping("/login")
    public ModelAndView loginForm(@RequestParam(name = "redirect", required = false) String redirect,
                                  HttpServletRequest request) {
        ModelAndView view = new ModelAndView("auth/login");
        view.addObject("redirect", redirect != null && RedirectTargetValidator.isSafe(redirect) ? redirect : null);
        String message = null;
        if (hasFlag(request, "error")) {
            message = "social".equals(request.getParameter("error"))
                    ? "소셜 로그인에 실패했어요. 다시 시도해 주세요" : "이메일 또는 비밀번호가 올바르지 않아요";
        } else if (hasFlag(request, "locked")) {
            message = "잠시 후 다시 시도해 주세요(약 15분)";
        } else if (hasFlag(request, "suspended")) {
            HttpSession session = request.getSession(false);
            Object notice = session == null ? null : session.getAttribute(SUSPENDED_NOTICE);
            if (session != null) {
                session.removeAttribute(SUSPENDED_NOTICE);
            }
            message = notice instanceof String text ? text : "정지된 계정이에요";
        } else if (hasFlag(request, "social")) {
            message = "다시 소셜 로그인해 주세요";
        }
        view.addObject("message", message);
        view.addObject("notice", hasFlag(request, "reset") ? "비밀번호를 바꿨어요. 다시 로그인해 주세요" : null);
        return view;
    }

    /** {@code ?error}처럼 값 없는 표시 파라미터도 인식한다. */
    private static boolean hasFlag(HttpServletRequest request, String name) {
        if (request.getParameter(name) != null) {
            return true;
        }
        String query = request.getQueryString();
        if (query == null) {
            return false;
        }
        for (String part : query.split("&")) {
            if (part.equals(name) || part.startsWith(name + "=")) {
                return true;
            }
        }
        return false;
    }

    // ----- 인증 -----

    @GetMapping("/auth/verify")
    public ModelAndView verify(@RequestParam(name = "token", required = false) String token) {
        EmailVerificationService.VerifyResult result = emailVerificationService.verify(token);
        ModelAndView view = new ModelAndView("auth/verify-result");
        view.addObject("verified", result == EmailVerificationService.VerifyResult.VERIFIED);
        view.addObject("loggedIn", currentUserProvider.current().isPresent());
        return view;
    }

    @PostMapping("/auth/verify/resend")
    public ModelAndView resend() {
        CurrentUser user = requireUser();
        EmailVerificationService.ResendResult result = emailVerificationService.resend(user.memberId());
        ModelAndView view = new ModelAndView("auth/verify-sent");
        view.addObject("notice", switch (result) {
            case SENT -> "인증 메일을 다시 보냈어요";
            case RATE_LIMITED -> "잠시 후 다시 시도해 주세요";
            case ALREADY_VERIFIED -> "이미 인증된 계정이에요";
        });
        view.addObject("resendResult", result.name());
        return view;
    }

    // ----- 도우미 -----

    private CurrentUser requireUser() {
        return currentUserProvider.current().orElseThrow(LoginRequiredException::new);
    }

    private List<String> passwordMessages(List<PasswordPolicyViolation> violations) {
        return violations.stream()
                .map(v -> messageSource.getMessage("password." + v.name(), null, v.name(), Locale.KOREAN))
                .toList();
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage("error." + code, args, code, Locale.KOREAN);
    }
}

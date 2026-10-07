package com.team.blog.account.application;

import com.team.blog.account.domain.PasswordPolicyViolation;
import java.util.List;

/** 비밀번호 규칙 위반(FR-012, FR-013). 원문은 담지 않는다. */
public class InvalidPasswordException extends RuntimeException {

    private final List<PasswordPolicyViolation> violations;

    public InvalidPasswordException(List<PasswordPolicyViolation> violations) {
        super("PASSWORD_POLICY");
        this.violations = List.copyOf(violations);
    }

    public List<PasswordPolicyViolation> getViolations() {
        return violations;
    }
}

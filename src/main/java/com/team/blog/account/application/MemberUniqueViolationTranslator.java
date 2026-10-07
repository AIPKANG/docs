package com.team.blog.account.application;

import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.shared.error.HandleTakenException;
import com.team.blog.shared.error.NicknameViolationException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 동시 가입·변경에서 DB UNIQUE 제약에 진 쪽을 사용자 안내 예외로 바꾼다(research R-7).
 *
 * <p><b>호출 위치는 트랜잭션 경계 밖</b>이어야 한다. PostgreSQL은 제약 위반(23505) 뒤 같은 트랜잭션에서 조회할 수 없으므로,
 * 가입 Service의 트랜잭션 메서드를 감싸는 바깥 메서드나 표현 계층에서 호출한다. 대안 주소는 새 읽기 트랜잭션에서 계산한다.
 */
@Component
public class MemberUniqueViolationTranslator {

    public static final String UQ_MEMBER_HANDLE = "uq_member_handle";
    public static final String UQ_MEMBER_NICKNAME = "uq_member_nickname";
    public static final String UQ_AUTH_IDENTITY = "uq_auth_identity";

    /**
     * 가입 문맥.
     *
     * @param provider       가입 수단
     * @param providerUserId 소셜 가입이면 소셜 계정 식별값, 이메일 가입이면 null
     * @param handle         제출한 주소(대안 계산의 기준)
     */
    public record SignupContext(Provider provider, String providerUserId, Handle handle) {

        public SignupContext {
            Objects.requireNonNull(provider);
        }

        public static SignupContext email(Handle handle) {
            return new SignupContext(Provider.LOCAL, null, handle);
        }

        public static SignupContext social(Provider provider, String providerUserId, Handle handle) {
            return new SignupContext(provider, providerUserId, handle);
        }

        boolean isSocial() {
            return provider != Provider.LOCAL && providerUserId != null;
        }
    }

    private final HandleService handleService;
    private final AuthIdentityRepository authIdentityRepository;

    public MemberUniqueViolationTranslator(HandleService handleService, AuthIdentityRepository authIdentityRepository) {
        this.handleService = handleService;
        this.authIdentityRepository = authIdentityRepository;
    }

    /**
     * 가입 제출 경합 번역.
     *
     * @return 소셜 가입이고 같은 소셜 계정이 이미 생겼으면 {@link ExistingSocialAccount}(그 회원으로 로그인)
     * @throws HandleTakenException {@code uq_member_handle} 위반(대안 주소 포함)
     * @throws NicknameViolationException {@code uq_member_nickname} 위반({@code NICKNAME_DUPLICATE}, concurrent)
     * @throws DataIntegrityViolationException 그 밖의 위반은 원래 예외를 그대로 던진다
     */
    public ExistingSocialAccount translate(DataIntegrityViolationException e, SignupContext context) {
        if (context.isSocial()) {
            // 001 T161: 같은 소셜 계정이 이미 생겼는지 로그인 수단 저장소로 확인(새 읽기 트랜잭션)
            Optional<AuthIdentity> existing = authIdentityRepository.findByProviderAndProviderUserId(
                    context.provider(), context.providerUserId());
            if (existing.isPresent()) {
                return new ExistingSocialAccount(existing.get().getMemberId());
            }
        }
        String constraint = constraintName(e);
        if (UQ_MEMBER_HANDLE.equals(constraint) && context.handle() != null) {
            throw new HandleTakenException(handleService.suggestAlternative(context.handle()));
        }
        if (UQ_MEMBER_NICKNAME.equals(constraint)) {
            throw new NicknameViolationException(NicknameViolation.NICKNAME_DUPLICATE, true);
        }
        throw e;
    }

    /**
     * 닉네임 변경 경합 번역(트랜잭션 밖에서 호출).
     *
     * @throws NicknameViolationException {@code uq_member_nickname} 위반({@code NICKNAME_DUPLICATE}, concurrent)
     * @throws DataIntegrityViolationException 그 밖의 위반은 원래 예외를 그대로 던진다
     */
    public void translate(DataIntegrityViolationException e) {
        if (UQ_MEMBER_NICKNAME.equals(constraintName(e))) {
            throw new NicknameViolationException(NicknameViolation.NICKNAME_DUPLICATE, true);
        }
        throw e;
    }

    /** 위반 제약 이름(소문자). Hibernate {@link ConstraintViolationException}에서 읽고, 없으면 메시지에서 찾는다. */
    static String constraintName(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName().toLowerCase(Locale.ROOT);
            }
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            for (String name : new String[] {UQ_MEMBER_HANDLE, UQ_MEMBER_NICKNAME, UQ_AUTH_IDENTITY}) {
                if (message.contains("\"" + name + "\"")) {
                    return name;
                }
            }
        }
        return null;
    }
}

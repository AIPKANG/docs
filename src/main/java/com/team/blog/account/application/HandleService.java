package com.team.blog.account.application;

import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.HandlePrefix;
import com.team.blog.account.domain.HandleRules;
import com.team.blog.account.domain.HandleViolation;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.HandleViolationException;
import com.team.blog.shared.text.BannedWordFilter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 주소 공개 Service (contracts/account-identity-service.md §1).
 *
 * <p><b>기존 회원의 주소를 바꾸는 연산은 없다</b>(FR-011). 주소는 가입 때 {@code Member} 생성자에서만 정해진다.
 */
@Service
public class HandleService {

    private final MemberRepository memberRepository;
    private final BannedWordFilter bannedWordFilter;
    private final AccountIdentityProperties.HandleSettings settings;
    private final Set<String> reserved;
    private final RandomGenerator random = new SecureRandom();

    public HandleService(MemberRepository memberRepository, BannedWordFilter bannedWordFilter,
                         AccountIdentityProperties properties) {
        this.memberRepository = memberRepository;
        this.bannedWordFilter = bannedWordFilter;
        this.settings = properties.handle();
        Set<String> words = new HashSet<>();
        for (String word : settings.reserved()) {
            words.add(word.strip().toLowerCase(Locale.ROOT));
        }
        this.reserved = Set.copyOf(words);
    }

    /** 08 §3 1~10단계로 미리 채울 주소. 결과는 그 순간 비어 있는 주소일 뿐 확정이 아니다. 이메일이 없으면 {@code user_}부터. */
    @Transactional(readOnly = true)
    public String prefill(String email, Provider provider) {
        return prefill(email, provider, random);
    }

    /** {@link #prefill(String, Provider)}와 같고 난수원만 주입한다(8단계 재현용). */
    @Transactional(readOnly = true)
    public String prefill(String email, Provider provider, RandomGenerator randomGenerator) {
        String body = HandleRules.bodyFromEmail(email, settings.prefillMaxBodyLength(), randomGenerator);
        Handle base = Handle.of(HandlePrefix.of(provider), body);
        return firstAvailable(base, true);
    }

    /**
     * 가입 요청의 주소 검사(research R-5). 정리 → 접두어 해석·수단 부착 → 접두어 일치 → 형식 → 예약어(+대안)
     * → 금칙어 → 중복(+대안). 호출자의 가입 트랜잭션 안에서 불러도 된다. 최종 보장은 {@code uq_member_handle}.
     *
     * @throws HandleViolationException 규칙 위반
     */
    @Transactional(readOnly = true)
    public Handle validateForSignup(String rawHandle, Provider provider) {
        String value = HandleRules.normalizeInput(rawHandle);
        HandlePrefix expected = HandlePrefix.of(provider);
        HandlePrefix prefix;
        String body;
        int dash = value.indexOf('-');
        if (dash >= 0) {
            prefix = HandlePrefix.fromLabel(value.substring(0, dash));
            if (prefix == null) {
                throw new HandleViolationException(HandleViolation.HANDLE_INVALID_FORMAT);
            }
            if (prefix != expected) {
                throw new HandleViolationException(HandleViolation.HANDLE_PREFIX_MISMATCH);
            }
            body = value.substring(dash + 1);
        } else {
            prefix = expected;
            body = value;
        }
        Violation violation = evaluate(prefix, body);
        if (violation != null) {
            throw new HandleViolationException(violation.code(), violation.suggestion());
        }
        return Handle.of(prefix, body);
    }

    /** 사용 가능 여부(접두어와 가입 수단의 일치 검사만 뺀다). */
    @Transactional(readOnly = true)
    public HandleCheckResult checkAvailability(String rawHandle) {
        String value = HandleRules.normalizeInput(rawHandle);
        HandlePrefix prefix = HandlePrefix.NONE;
        String body = value;
        int dash = value.indexOf('-');
        if (dash >= 0) {
            prefix = HandlePrefix.fromLabel(value.substring(0, dash));
            if (prefix == null) {
                return unavailable(HandleViolation.HANDLE_INVALID_FORMAT, null);
            }
            body = value.substring(dash + 1);
        }
        Violation violation = evaluate(prefix, body);
        return violation == null ? HandleCheckResult.ok() : unavailable(violation.code(), violation.suggestion());
    }

    /**
     * 대안 주소: {@code base}(예약어면 제외), {@code base_2}, {@code base_3} … 중 비어 있는 첫 값.
     * 후보를 {@code suggestion-batch-size}개씩 {@code handle IN (...)} 한 번으로 조회한다.
     * 경합 번역기는 중단된 트랜잭션 밖에서 부르므로 새 읽기 트랜잭션을 연다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String suggestAlternative(Handle base) {
        return firstAvailable(base, true);
    }

    /** {@code /@{handle}} 정규화: 대문자가 있으면 소문자 값, 없으면 empty. 존재 여부는 보지 않는다. */
    public Optional<String> canonicalPath(String pathHandle) {
        if (pathHandle == null) {
            return Optional.empty();
        }
        String lower = pathHandle.toLowerCase(Locale.ROOT);
        return lower.equals(pathHandle) ? Optional.empty() : Optional.of(lower);
    }

    // ----- 내부 -----

    private record Violation(HandleViolation code, String suggestion) {
    }

    private Violation evaluate(HandlePrefix prefix, String body) {
        if (!HandleRules.isValidFormat(prefix.value() + body)) {
            return new Violation(HandleViolation.HANDLE_INVALID_FORMAT, null);
        }
        Handle handle = Handle.of(prefix, body);
        if (isReserved(body)) {
            return new Violation(HandleViolation.HANDLE_RESERVED, firstAvailable(handle, false));
        }
        if (bannedWordFilter.containsBannedInHandleBody(body)) {
            return new Violation(HandleViolation.HANDLE_BANNED_WORD, null);
        }
        if (memberRepository.existsByHandle(handle.toString())) {
            return new Violation(HandleViolation.HANDLE_DUPLICATE, firstAvailable(handle, false));
        }
        return null;
    }

    private boolean isReserved(String body) {
        return reserved.contains(body);
    }

    private static HandleCheckResult unavailable(HandleViolation code, String suggestion) {
        return new HandleCheckResult(false, code.reason(), suggestion);
    }

    /** 08 §3 10단계. {@code includeBase}가 참이면 {@code base} 자체도 후보(예약어가 아닐 때). */
    private String firstAvailable(Handle base, boolean includeBase) {
        int batchSize = Math.max(1, settings.suggestionBatchSize());
        boolean baseCandidate = includeBase && !isReserved(base.body());
        int next = 2;
        while (true) {
            List<String> candidates = new ArrayList<>(batchSize + 1);
            if (baseCandidate) {
                candidates.add(base.toString());
                baseCandidate = false;
            }
            for (int i = 0; i < batchSize; i++, next++) {
                candidates.add(base.prefix().value() + HandleRules.withNumber(base.body(), next));
            }
            Set<String> taken = new HashSet<>(memberRepository.findHandlesIn(new LinkedHashSet<>(candidates)));
            for (String candidate : candidates) {
                if (!taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
    }
}

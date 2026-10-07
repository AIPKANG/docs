package com.team.blog.account.application;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * 회원의 모든 로그인 세션 삭제("모든 기기 로그아웃", FR-019, 42 P-7). Spring Session principal 인덱스(이름 = memberId)로
 * 그 회원의 세션만 찾아 지운다. 정지(43)·비밀번호 변경(003)도 이 Service를 쓴다.
 */
@Service
public class SessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** @return 삭제한 세션 수 */
    public int revokeAll(long memberId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(String.valueOf(memberId));
        found.keySet().forEach(sessions::deleteById);
        log.info("세션 전부 삭제: memberId={} count={}", memberId, found.size());
        return found.size();
    }

    /**
     * 003 비밀번호 변경: {@code keepSessionId} 세션만 남기고 그 회원의 다른 세션을 모두 지운다(다른 기기 로그아웃).
     * 지금 기기의 세션 ID 재발급은 호출자(표현 계층)가 이 호출 뒤에 한다.
     *
     * @return 삭제한 세션 수
     */
    public int revokeAllExcept(long memberId, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(String.valueOf(memberId));
        int count = 0;
        for (String id : found.keySet()) {
            if (!id.equals(keepSessionId)) {
                sessions.deleteById(id);
                count++;
            }
        }
        log.info("다른 기기 세션 삭제: memberId={} count={}", memberId, count);
        return count;
    }
}

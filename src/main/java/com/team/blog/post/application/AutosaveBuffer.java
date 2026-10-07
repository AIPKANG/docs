package com.team.blog.post.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 서버 쪽 자동 저장 버퍼(04 §2-3, research R-3). 구현은 Redis({@code RedisAutosaveBuffer}).
 * 저장소 접근 실패는 {@link org.springframework.dao.DataAccessException}으로 던진다 — 호출자가 DB 경로로 바꾼다(R-6).
 */
public interface AutosaveBuffer {

    /** 판정 결과. {@code version}은 받아들였으면 새 버전, 충돌이면 현재 버전. */
    record SaveOutcome(Kind kind, long version) {

        public enum Kind { ACCEPTED, CONFLICT, NOT_OWNER }
    }

    /**
     * 현재 버전 = max(버퍼 버전, {@code dbVersion})이 {@code baseVersion}과 같을 때만 저장하고 +1, 영구 반영 대기 목록에 넣는다.
     * 확인과 저장은 원자적이다(FR-016).
     */
    SaveOutcome save(long postId, long memberId, long baseVersion, long dbVersion, String title, String contentMd,
                     Instant savedAt);

    Optional<BufferedContent> read(long postId);

    boolean exists(long postId);

    /** 반영 대기 목록에서 최대 {@code max}개. */
    List<Long> dirtyBatch(int max);

    /** 버퍼 버전이 아직 {@code flushedVersion} 이하일 때만 반영 대기 목록에서 뺀다(반영 중 들어온 새 버전 보호). */
    void markFlushed(long postId, long flushedVersion);

    /** 버퍼 내용과 반영 대기 표시를 지운다(변경 취소·발행 커밋 후). */
    void evict(long postId);
}

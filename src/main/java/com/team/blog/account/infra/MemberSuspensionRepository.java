package com.team.blog.account.infra;

import com.team.blog.account.domain.MemberSuspension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 정지 이력(읽기·자동 해제). {@code ix_member_suspension_member} 사용. */
public interface MemberSuspensionRepository extends JpaRepository<MemberSuspension, Long> {

    /** 현재 정지: {@code lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now)} — 가장 늦게 끝나는 것 먼저(영구 우선). */
    @Query("select s from MemberSuspension s where s.memberId = :memberId and s.liftedAt is null"
            + " and (s.endsAt is null or s.endsAt > :now) order by s.endsAt desc nulls first")
    List<MemberSuspension> findCurrentAll(@Param("memberId") long memberId, @Param("now") Instant now);

    default Optional<MemberSuspension> findCurrent(long memberId, Instant now) {
        return findCurrentAll(memberId, now).stream().findFirst();
    }

    /** 기간이 끝났지만 아직 해제 기록이 없는 정지. */
    @Query("select s from MemberSuspension s where s.memberId = :memberId and s.liftedAt is null"
            + " and s.endsAt is not null and s.endsAt <= :now")
    List<MemberSuspension> findExpiredNotLifted(@Param("memberId") long memberId, @Param("now") Instant now);
}

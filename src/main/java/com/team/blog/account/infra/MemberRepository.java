package com.team.blog.account.infra;

import com.team.blog.account.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 저장소(001과 공유). account 모듈 안에서만 쓴다 — 다른 모듈은 account.application의 공개 Service로만 접근한다(헌법 I).
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByHandle(String handle);

    /** 후보 주소 중 이미 있는 것. {@code handle IN (...)} 한 번(uq_member_handle 인덱스) — research R-3. */
    @Query("select m.handle from Member m where m.handle in :handles")
    List<String> findHandlesIn(@Param("handles") Collection<String> handles);

    /** {@code lower(nickname) = lower(:n)} — uq_member_nickname 함수 인덱스 사용. {@code excludeMemberId}가 null이면 제외 없음. */
    @Query("select count(m) > 0 from Member m where lower(m.nickname) = lower(:nickname)"
            + " and (:excludeMemberId is null or m.id <> :excludeMemberId)")
    boolean existsByNicknameIgnoreCaseExcluding(@Param("nickname") String normalized,
                                               @Param("excludeMemberId") Long excludeMemberId);

    /** 행 잠금({@code SELECT … FOR UPDATE}). 같은 회원의 동시 닉네임 변경을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") long id);

    /** 003: 이 사진들 중 회원 프로필이 참조 중인 것(정리 작업 방어, media SPI). */
    @Query("select m.profileImageId from Member m where m.profileImageId in :imageIds")
    List<Long> findProfileImageIdsIn(@Param("imageIds") Collection<Long> imageIds);

    /** 탈퇴(유예·익명 처리 모두)가 아닌 회원만. */
    @Query("select m from Member m where m.handle = :handle"
            + " and m.status <> com.team.blog.account.domain.MemberStatus.WITHDRAWN")
    Optional<Member> findActiveByHandle(@Param("handle") String handle);
}

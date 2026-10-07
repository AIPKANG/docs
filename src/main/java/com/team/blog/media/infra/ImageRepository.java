package com.team.blog.media.infra;

import com.team.blog.media.domain.Image;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사진 저장소. media 모듈 안에서만 쓴다(헌법 I). */
public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByIdAndUploaderId(long id, long uploaderId);

    /** 행 잠금({@code SELECT … FOR UPDATE}) — 연결과 정리 작업의 경합을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Image i where i.id = :id")
    Optional<Image> findByIdForUpdate(@Param("id") long id);

    /** 정리 후보: TEMP로 보관 시간이 지난 것, 연결이 끊긴 지 보관 시간이 지난 것(부분 인덱스 2개). */
    @Query("select i.id from Image i where (i.status = com.team.blog.media.domain.ImageStatus.TEMP and i.createdAt < :tempBefore)"
            + " or (i.detachedAt is not null and i.detachedAt < :detachedBefore) order by i.id")
    List<Long> findCleanupCandidates(@Param("tempBefore") Instant tempBefore,
                                     @Param("detachedBefore") Instant detachedBefore, Pageable page);
}

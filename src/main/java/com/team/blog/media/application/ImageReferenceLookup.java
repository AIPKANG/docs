package com.team.blog.media.application;

import java.util.Collection;
import java.util.Set;

/**
 * 다른 모듈이 참조 중인 사진을 media에 알려 주는 SPI(헌법 I: media는 다른 모듈 테이블을 읽지 않는다).
 * 정리 작업은 여기서 참조 중이라고 한 사진을 건너뛴다. 003: account(회원 프로필), 008: post(글 사진)가 구현을 더한다.
 */
public interface ImageReferenceLookup {

    /** @return {@code imageIds} 중 참조 중인 것 */
    Set<Long> referencedIds(Collection<Long> imageIds);
}

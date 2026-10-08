package com.team.blog.media.application;

import java.util.Optional;

/**
 * 사진 저장소 포트(02 §3, 04 §4-1). 사진 데이터는 앱 서버를 거치지 않고 브라우저가 {@link #prepareUpload}의 주소로 직접 올린다.
 * 구현: {@code media.infra.S3ImageStorage}(MinIO·S3 호환, SigV4). 008이 저장소를 띄울 수 없는 환경용
 * {@code LocalImageStorage}를 같은 인터페이스로 더한다.
 *
 * <p>모든 메서드는 외부 호출이다 — DB 트랜잭션 안에서 부르지 않는다(헌법 V).
 */
public interface ImageStorage {

    /** 사진 주소는 바뀌지 않으므로 1년 변경 없는 캐시(04 §4-2, 008 FR-007). 업로드 서명에 넣어 저장소가 그대로 돌려준다. */
    String CACHE_CONTROL = "public, max-age=31536000, immutable";

    /** 업로드 주소 발급(서명에 {@code Content-Type} 포함, 유효 시간은 설정값). */
    UploadTarget prepareUpload(String key, String contentType, long size);

    /** 존재·실제 크기·Content-Type·앞부분 바이트(최대 {@link StoredObject#HEAD_BYTES}). 없으면 empty. */
    Optional<StoredObject> inspect(String key);

    /** 파일 전체(최대 {@code maxBytes}바이트, GIF 프레임 검사용). 없으면 empty. 더 크면 앞 {@code maxBytes}만. */
    Optional<byte[]> read(String key, long maxBytes);

    /** 사진을 보여줄 공개 주소. */
    String publicUrl(String key);

    /** 삭제. 없는 키도 성공으로 본다. 실패하면 예외. */
    void delete(String key);
}

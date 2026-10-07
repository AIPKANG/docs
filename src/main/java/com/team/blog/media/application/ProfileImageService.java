package com.team.blog.media.application;

import com.team.blog.media.domain.Image;
import com.team.blog.media.domain.ImagePurpose;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.shared.error.InvalidProfileImageException;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 이미지 연결·해제(11 §4-4, 003 research R-9). account {@code ProfileService}가 자기 트랜잭션 안에서 부른다
 * ({@code REQUIRED}) — 회원 행 변경과 사진 상태 변경이 한 트랜잭션이다(FR-016).
 *
 * <p>연결할 수 있는 사진: 본인이 올렸고, 프로필 용도이고, 업로드가 끝났고(width 있음), 연결이 끊겨 삭제를 기다리지 않는 것.
 * 그 밖(없음 포함)은 구분 없이 {@code INVALID_PROFILE_IMAGE}(SC-003).
 */
@Service
public class ProfileImageService {

    private final ImageRepository imageRepository;
    private final ImageStorage storage;
    private final Clock clock;

    public ProfileImageService(ImageRepository imageRepository, ImageStorage storage, Clock clock) {
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean isValidCandidate(long memberId, long imageId) {
        return imageRepository.findById(imageId).filter(image -> usable(image, memberId)).isPresent();
    }

    /**
     * 사진 행을 잠그고 다시 판정한 뒤 ATTACHED로 바꾼다.
     *
     * @return 공개 주소({@code member.profile_image_url}에 복사)
     * @throws InvalidProfileImageException 연결할 수 없음
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String attach(long memberId, long imageId) {
        Image image = imageRepository.findByIdForUpdate(imageId)
                .filter(candidate -> usable(candidate, memberId))
                .orElseThrow(InvalidProfileImageException::new);
        image.attach();
        return storage.publicUrl(image.getStorageKey());
    }

    /** 이전 프로필 사진의 연결 해제 시각 기록(7일 뒤 정리). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void detach(long imageId) {
        imageRepository.findByIdForUpdate(imageId).ifPresent(image -> image.detach(clock.instant()));
    }

    private static boolean usable(Image image, long memberId) {
        return image.getUploaderId() == memberId
                && image.getPurpose() == ImagePurpose.PROFILE
                && image.isCompleted()
                && image.getDetachedAt() == null;
    }
}

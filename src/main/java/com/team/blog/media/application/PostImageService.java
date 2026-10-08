package com.team.blog.media.application;

import com.team.blog.media.infra.PostImageStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글과 사진의 연결(008 research R-3, 04 §4-4). post 모듈이 본문에서 뽑은 우리 저장소 주소를 넘기면, 그 글 작성자가 올린 글 용도
 * 사진만 연결하고(ATTACHED), 빠진 사진은 다른 글에도 없으면 끊긴 시각을 기록한다. 카드 썸네일·GIF 정지 장면 주소도 여기서 준다.
 */
@Service
public class PostImageService {

    private final PostImageStore store;
    private final ImageStorage storage;

    public PostImageService(PostImageStore store, ImageStorage storage) {
        this.store = store;
        this.storage = storage;
    }

    /** 우리 저장소 주소 → 저장 키({@code images/…}). 아니면 빈 값. */
    public Optional<String> keyOf(String url) {
        String prefix = storage.publicUrl("");
        if (url == null || !url.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = url.substring(prefix.length());
        return key.startsWith("images/") && !key.contains("..") ? Optional.of(key) : Optional.empty();
    }

    private List<String> keysOf(List<String> urls) {
        Set<String> keys = new LinkedHashSet<>();
        urls.forEach(url -> keyOf(url).ifPresent(keys::add));
        return new ArrayList<>(keys);
    }

    /** 이 글의 연결을 {@code urls}의 내 글 사진으로 맞춘다. 남의 사진·없는 사진 주소는 연결하지 않는다. */
    @Transactional
    public void sync(long postId, long authorId, List<String> urls, Instant now) {
        List<Long> wanted = store.ownedPostImages(authorId, keysOf(urls)).stream().map(PostImageStore.OwnedImage::id).toList();
        List<Long> current = store.linkedImageIds(postId);
        store.link(postId, wanted);
        List<Long> removed = current.stream().filter(id -> !wanted.contains(id)).toList();
        store.unlink(postId, removed, now);
    }

    /** 글을 영구 삭제할 때(011·023) 모든 연결을 끊는다. */
    @Transactional
    public void detachAll(long postId, Instant now) {
        store.unlink(postId, store.linkedImageIds(postId), now);
    }

    /** {@code urls} 중 우리 저장소 주소인데 이 회원이 올린 글 사진이 아닌 것(발행 거부, FR-010). */
    @Transactional(readOnly = true)
    public List<String> foreignImageUrls(long authorId, List<String> urls) {
        List<String> keys = keysOf(urls);
        Set<String> owned = new java.util.HashSet<>();
        store.ownedPostImages(authorId, keys).forEach(img -> owned.add(img.storageKey()));
        List<String> foreign = new ArrayList<>();
        for (String url : urls) {
            keyOf(url).filter(key -> !owned.contains(key)).ifPresent(key -> foreign.add(url));
        }
        return foreign;
    }

    /** 카드 썸네일(FR-020): 썸네일이 있으면 썸네일 주소, 없으면 원본 주소. */
    @Transactional(readOnly = true)
    public String cardThumbnailOf(String url) {
        return thumbnailOf(url).orElse(url);
    }

    /** 썸네일 주소(없으면 빈 값). */
    @Transactional(readOnly = true)
    public Optional<String> thumbnailOf(String url) {
        Optional<String> key = keyOf(url);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> thumbs = store.thumbnails(List.of(key.get()));
        return Optional.ofNullable(thumbs.get(key.get())).map(storage::publicUrl);
    }
}

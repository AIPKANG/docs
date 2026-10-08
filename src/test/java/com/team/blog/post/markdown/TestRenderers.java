package com.team.blog.post.markdown;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.application.StoredObject;
import com.team.blog.media.application.UploadTarget;
import java.time.Duration;
import java.util.Optional;

/** 단위 테스트용 렌더러(스프링 없이). 저장소 공개 주소는 {@link #CDN}. */
final class TestRenderers {

    static final String CDN = "https://cdn.devlog.example/blog-images/images/";
    static final String SITE = "https://devlog.example";

    private TestRenderers() {
    }

    static ContentRenderer create() {
        return create(20, Duration.ofSeconds(1));
    }

    static ContentRenderer create(int maxNesting, Duration timeout) {
        MarkdownProperties properties = new MarkdownProperties(maxNesting, timeout, SITE, 60,
                new MarkdownProperties.Rerender(false, "0 10 5 * * *", 100));
        ImageStorage storage = new ImageStorage() {
            @Override
            public UploadTarget prepareUpload(String key, String contentType, long size) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<StoredObject> inspect(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<byte[]> read(String key, long maxBytes) {
                return Optional.empty();
            }

            @Override
            public String publicUrl(String key) {
                return "https://cdn.devlog.example/blog-images/" + key;
            }

            @Override
            public void delete(String key) {
            }
        };
        return new ContentRenderer(properties, storage);
    }
}

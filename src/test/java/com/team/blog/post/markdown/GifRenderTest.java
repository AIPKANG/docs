package com.team.blog.post.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 008 T811: 우리 저장소 GIF는 정지 장면을 원본 GIF 링크로 감싼다(23 §5-2, FR-032). */
class GifRenderTest {

    private static final String CDN = TestRenderers.CDN;

    @Test
    void ownGifBecomesLinkedStillImage() {
        ContentRenderer renderer = new ContentRenderer(
                new MarkdownProperties(20, Duration.ofSeconds(1), TestRenderers.SITE, 60,
                        new MarkdownProperties.Rerender(false, "0 10 5 * * *", 100)),
                stubStorage(), gif -> Optional.of(gif.replace(".gif", "_thumb.webp")));
        RenderedContent r = renderer.render("![로딩 화면](" + CDN + "2026/10/a.gif)");
        assertThat(r.html()).contains("href=\"" + CDN + "2026/10/a.gif\" title=\"움직이는 이미지 재생\"")
                .contains("target=\"_blank\"")
                .contains("<img src=\"" + CDN + "2026/10/a_thumb.webp\" alt=\"로딩 화면\" loading=\"lazy\" decoding=\"async\"");
        assertThat(r.imageUrls()).containsExactly(CDN + "2026/10/a.gif");
        assertThat(DangerousHtmlChecker.problems(r.html())).isEmpty();
    }

    @Test
    void gifWithoutThumbnailStaysAsIsAndEmptyAltIsKept() {
        RenderedContent r = TestRenderers.create().render("![](" + CDN + "2026/10/b.gif)");
        assertThat(r.html()).contains("<img src=\"" + CDN + "2026/10/b.gif\" alt=\"\"").doesNotContain("<a ");
    }

    private static com.team.blog.media.application.ImageStorage stubStorage() {
        return new com.team.blog.media.application.ImageStorage() {
            public com.team.blog.media.application.UploadTarget prepareUpload(String k, String c, long s) {
                throw new UnsupportedOperationException();
            }

            public Optional<com.team.blog.media.application.StoredObject> inspect(String k) {
                return Optional.empty();
            }

            public Optional<byte[]> read(String k, long m) {
                return Optional.empty();
            }

            public String publicUrl(String k) {
                return "https://cdn.devlog.example/blog-images/" + k;
            }

            public void delete(String k) {
            }
        };
    }
}

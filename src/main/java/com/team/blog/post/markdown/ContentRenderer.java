package com.team.blog.post.markdown;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.shared.error.ContentTooComplexException;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * 본문 렌더러 — 시스템에서 HTML을 만드는 유일한 곳(12 §2, FR-003).
 * ① CommonMark + GFM 파싱 → ② AST 변환({@link MarkdownTransformer}) → ③ {@code escapeHtml}·{@code sanitizeUrls} 렌더링 →
 * ④ OWASP 허용 목록 정화. ③에 결함이 있어도 ④가 다시 막는다. 전체를 전용 스레드에서 시간 제한(기본 1초)으로 돌린다.
 */
@Component
public class ContentRenderer {

    /** 렌더링 규칙 버전. 렌더러·변환·정화 규칙을 바꾸면 올리고 다시 렌더링 배치를 돌린다(12 §7-7). */
    public static final int RENDER_VERSION = 1;

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(),
            TaskListItemsExtension.create(), AutolinkExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final MarkdownProperties properties;
    private final LinkRules linkRules;
    private final PolicyFactory policy;
    private final ThreadPoolExecutor executor;

    public ContentRenderer(MarkdownProperties properties, ImageStorage imageStorage) {
        this.properties = properties;
        this.linkRules = new LinkRules(properties.siteOrigin(), imageStorage.publicUrl("images/"));
        this.policy = SanitizePolicy.create(linkRules);
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
            Thread t = new Thread(r, "content-render-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** 렌더링. 중첩 한도·시간 한도를 넘으면 {@link ContentTooComplexException}. */
    public RenderedContent render(String contentMd) {
        String source = contentMd == null ? "" : contentMd;
        Future<RenderedContent> future;
        try {
            future = executor.submit(() -> renderNow(source));
        } catch (RejectedExecutionException e) {
            throw new ContentTooComplexException();
        }
        try {
            return future.get(properties.renderTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ContentTooComplexException();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ContentTooComplexException();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof MarkdownTransformer.ContentTooComplexSignal) {
                throw new ContentTooComplexException();
            }
            if (e.getCause() instanceof StackOverflowError) {
                throw new ContentTooComplexException();
            }
            throw new IllegalStateException("content rendering failed", e.getCause());
        }
    }

    private RenderedContent renderNow(String source) {
        Node document = parser.parse(source);
        MarkdownTransformer transformer = new MarkdownTransformer(linkRules, properties.maxNesting());
        document.accept(transformer);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .attributeProviderFactory(context -> new HtmlAttributes(linkRules))
                .build();
        String rendered = renderer.render(document);
        String html = policy.sanitize(rendered);
        return new RenderedContent(html, ExcerptExtractor.extract(document), List.copyOf(transformer.imageUrls()),
                RENDER_VERSION);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

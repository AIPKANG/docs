package com.team.blog.post.markdown;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Text;

/**
 * AST 변환(12 §2 ②): 제목 한 단계 낮추기, 외부 이미지 → {@code [이미지] 대체글} 링크, 중첩 깊이 검사, 우리 저장소 이미지 주소 모으기.
 * 008이 GIF 규칙(23 §5-2)을 여기에 더한다.
 */
final class MarkdownTransformer extends AbstractVisitor {

    /** GIF 정지 장면(썸네일) 주소 찾기. 없으면 원본 GIF를 그대로 보인다. */
    interface GifThumbnails {
        java.util.Optional<String> thumbnailOf(String gifUrl);
    }

    static final String GIF_TITLE = "움직이는 이미지 재생";

    private final LinkRules linkRules;
    private final int maxNesting;
    private final GifThumbnails gifThumbnails;
    private final List<String> imageUrls = new ArrayList<>();
    private int depth;

    MarkdownTransformer(LinkRules linkRules, int maxNesting, GifThumbnails gifThumbnails) {
        this.linkRules = linkRules;
        this.maxNesting = maxNesting;
        this.gifThumbnails = gifThumbnails;
    }

    List<String> imageUrls() {
        return imageUrls;
    }

    @Override
    public void visit(Heading heading) {
        heading.setLevel(Math.min(6, heading.getLevel() + 1));
        visitChildren(heading);
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        nested(blockQuote);
    }

    @Override
    public void visit(BulletList bulletList) {
        nested(bulletList);
    }

    @Override
    public void visit(OrderedList orderedList) {
        nested(orderedList);
    }

    private void nested(Node node) {
        depth++;
        if (depth > maxNesting) {
            throw new ContentTooComplexSignal();
        }
        visitChildren(node);
        depth--;
    }

    @Override
    public void visit(Image image) {
        String src = image.getDestination();
        if (linkRules.isOwnImage(src)) {
            if (!imageUrls.contains(src)) {
                imageUrls.add(src);
            }
            if (src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif") && gifThumbnails != null) {
                gif(image, src);
            }
            return;
        }
        // 외부·위험 이미지는 <img>로 만들지 않는다: [이미지] 대체글 링크(주소 정화·외부 링크 규칙은 렌더러·정화기가 적용)
        String alt = textOf(image);
        Link link = new Link(src, image.getTitle());
        link.appendChild(new Text("[이미지] " + (alt.isBlank() ? (src == null ? "" : src) : alt)));
        image.insertBefore(link);
        image.unlink();
    }

    /**
     * 23 §5-2: 우리 저장소 GIF → 정지 장면(썸네일) 이미지를 원본 GIF 링크로 감싼다. 허용 목록·CSP는 그대로다.
     * 재생은 {@code /js/gif-play.js}가 하고, 스크립트가 없으면 링크로 원본이 새 탭에서 열린다.
     */
    private void gif(Image image, String src) {
        java.util.Optional<String> thumb = gifThumbnails.thumbnailOf(src);
        if (thumb.isEmpty()) {
            return;
        }
        Link link = new Link(src, GIF_TITLE);
        Image still = new Image(thumb.get(), image.getTitle());
        Node child = image.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            still.appendChild(child);
            child = next;
        }
        link.appendChild(still);
        image.insertBefore(link);
        image.unlink();
    }

    /** 노드 아래의 글자(대체글 등). */
    static String textOf(Node node) {
        StringBuilder out = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                out.append(text.getLiteral());
            }

            @Override
            public void visit(Code code) {
                out.append(code.getLiteral());
            }
        });
        return out.toString();
    }

    /** 중첩 한도 초과 신호(렌더러가 {@code ContentTooComplexException}으로 바꾼다). */
    static final class ContentTooComplexSignal extends RuntimeException {
        ContentTooComplexSignal() {
            super(null, null, false, false);
        }
    }
}

package com.team.blog.post.markdown;

import java.util.Map;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.renderer.html.AttributeProvider;

/**
 * 렌더링 속성(12 §3, §5, §6): 제목 {@code id}, 외부 링크 새 탭 + {@code rel}, 이미지 지연 로딩·비동기 디코딩.
 * 렌더링 한 번마다 새로 만든다(앵커 중복 계산).
 */
final class HtmlAttributes implements AttributeProvider {

    static final String EXTERNAL_REL = "noopener noreferrer nofollow ugc";

    private final LinkRules linkRules;
    private final HeadingAnchors anchors = new HeadingAnchors();

    HtmlAttributes(LinkRules linkRules) {
        this.linkRules = linkRules;
    }

    @Override
    public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
        if (node instanceof Heading heading) {
            attributes.put("id", anchors.next(MarkdownTransformer.textOf(heading)));
        } else if (node instanceof Link && "a".equals(tagName)) {
            String href = attributes.get("href");
            if (href != null && !href.isEmpty() && !linkRules.isInternal(href)) {
                attributes.put("target", "_blank");
                attributes.put("rel", EXTERNAL_REL);
            }
        } else if (node instanceof Image && "img".equals(tagName)) {
            attributes.put("loading", "lazy");
            attributes.put("decoding", "async");
        }
    }
}

package com.team.blog.post.markdown;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

/**
 * 요약(10 §2-1): 코드 블록·이미지·표를 빼고 제목·문단·목록의 글자만(인라인 코드·링크 글자는 남김), 공백을 하나로,
 * 앞 200자(단어 중간이면 그 단어 앞에서). 변환을 마친 AST에서 뽑는다 — 정화된 HTML의 보이는 글자와 같다.
 */
final class ExcerptExtractor extends AbstractVisitor {

    static final int MAX = 200;

    private final StringBuilder out = new StringBuilder();

    static String extract(Node document) {
        ExcerptExtractor extractor = new ExcerptExtractor();
        document.accept(extractor);
        return cut(extractor.out.toString().replaceAll("\\s+", " ").strip());
    }

    static String cut(String text) {
        if (text.codePointCount(0, text.length()) <= MAX) {
            return text;
        }
        int end = text.offsetByCodePoints(0, MAX);
        if (Character.isWhitespace(text.charAt(end))) {
            return text.substring(0, end).strip();
        }
        int lastSpace = text.lastIndexOf(' ', end);
        return (lastSpace > 0 ? text.substring(0, lastSpace) : text.substring(0, end)).strip();
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        // 코드 블록은 뺀다
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
    }

    @Override
    public void visit(Image image) {
    }

    @Override
    public void visit(CustomBlock customBlock) {
        if (!(customBlock instanceof TableBlock)) {
            visitChildren(customBlock);
            out.append(' ');
        }
    }

    @Override
    public void visit(Text text) {
        out.append(text.getLiteral());
    }

    @Override
    public void visit(Code code) {
        out.append(code.getLiteral());
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        out.append(htmlInline.getLiteral()); // 직접 쓴 HTML은 글자로 보인다
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        out.append(htmlBlock.getLiteral()).append(' ');
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        out.append(' ');
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        out.append(' ');
    }

    @Override
    protected void visitChildren(Node parent) {
        super.visitChildren(parent);
        if (parent instanceof Block) {
            out.append(' ');
        }
    }
}

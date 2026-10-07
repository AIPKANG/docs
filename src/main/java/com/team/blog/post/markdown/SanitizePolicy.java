package com.team.blog.post.markdown;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/** 정화 허용 목록(12 §4). 이 목록을 바꾸면 {@link ContentRenderer#RENDER_VERSION}을 올린다. */
final class SanitizePolicy {

    private SanitizePolicy() {
    }

    static PolicyFactory create(LinkRules linkRules) {
        return new HtmlPolicyBuilder()
                .allowElements("p", "br", "hr", "blockquote", "h2", "h3", "h4", "h5", "h6", "strong", "em", "del",
                        "ul", "ol", "li", "input", "code", "pre", "table", "thead", "tbody", "tr", "th", "td", "a", "img")
                .allowAttributes("id").matching(Pattern.compile("h-[\\p{L}\\p{N}_-]{1,100}"))
                .onElements("h2", "h3", "h4", "h5", "h6")
                .allowAttributes("start").matching(Pattern.compile("\\d{1,6}")).onElements("ol")
                .allowAttributes("type").matching(Pattern.compile("checkbox")).onElements("input")
                .allowAttributes("checked", "disabled").onElements("input")
                .allowAttributes("class").matching(Pattern.compile("language-[a-z0-9+#-]{1,20}")).onElements("code")
                .allowAttributes("align").matching(Pattern.compile("left|center|right")).onElements("th", "td")
                .allowUrlProtocols("http", "https", "mailto")
                .allowAttributes("href", "title").onElements("a")
                .allowAttributes("target").matching(Pattern.compile("_blank")).onElements("a")
                .allowAttributes("rel").matching(Pattern.compile(HtmlAttributes.EXTERNAL_REL)).onElements("a")
                .allowAttributes("src").matching((element, attribute, value) -> linkRules.isOwnImage(value) ? value : null)
                .onElements("img")
                .allowAttributes("alt", "title").onElements("img")
                .allowAttributes("loading").matching(Pattern.compile("lazy")).onElements("img")
                .allowAttributes("decoding").matching(Pattern.compile("async")).onElements("img")
                .toFactory();
    }
}

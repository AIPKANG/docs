package com.team.blog.tag.application.suggest;

import java.util.List;

/** 공급자 하나(Gemini·Ollama). 형식 {@code {"tags":[…]}} 최대 5개를 강제하고, 맞지 않으면 {@link ProviderException}. */
public interface TagSuggester {

    /** @param popular 인기 태그(이 중 맞으면 그 표기 그대로), @param exclude 이미 붙인 태그 */
    record Prompt(String input, List<String> popular, List<String> exclude, int max) {
    }

    String name();

    List<String> suggest(Prompt prompt);

    /** 공급자 실패 종류(34 §3-2). */
    class ProviderException extends RuntimeException {

        public enum Kind { DAILY_LIMIT, MINUTE_LIMIT, UNKNOWN_LIMIT, TIMEOUT_OR_SERVER, INVALID_FORMAT }

        private final Kind kind;

        public ProviderException(Kind kind) {
            super(kind.name());
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }

    /** 34 §4 지시문(고정된 것이 앞). 프롬프트를 바꾸면 {@code prompt-version}을 올린다. */
    static String instruction(Prompt p) {
        StringBuilder sb = new StringBuilder();
        sb.append("이 블로그 글의 핵심 주제를 나타내는 태그를 최대 ").append(p.max()).append("개 고르세요. ")
                .append("글에 나오지 않는 기술은 넣지 마세요. 태그는 영어 소문자·한글·숫자와 하이픈만. ")
                .append("JSON {\"tags\": [문자열]} 으로만 답하세요.\n");
        if (!p.popular().isEmpty()) {
            sb.append("기존 인기 태그(이 중 맞는 것이 있으면 그 표기를 그대로 쓰세요): ").append(String.join(", ", p.popular())).append('\n');
        }
        if (!p.exclude().isEmpty()) {
            sb.append("이미 붙인 태그(제외): ").append(String.join(", ", p.exclude())).append('\n');
        }
        sb.append("글:\n").append(p.input());
        return sb.toString();
    }
}

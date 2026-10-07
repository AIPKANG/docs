package com.team.blog.post.application;

import com.team.blog.post.domain.PostContentRules;
import com.team.blog.post.domain.PostTitleRules;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.domain.TagRejectedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 발행 입력 검사(05 §4, FR-003~FR-008). 실패한 칸을 모두 모아 400 {@code VALIDATION_FAILED}로 던진다 — 형식은 003과 같은
 * {@code errors[]}({@link ProfileValidationException}을 공용으로 쓴다).
 */
@Component
public class PublishValidator {

    /** 업로드가 끝나지 않은 사진 표시: {@code ](local:…)}, {@code ](<local:…>)}, 참조 정의 {@code ]: local:…}. */
    private static final Pattern PENDING_IMAGE = Pattern.compile("\\]\\(\\s*<?\\s*local:|\\]:\\s*<?\\s*local:",
            Pattern.CASE_INSENSITIVE);

    private final TagNormalizer tagNormalizer;
    private final PostProperties properties;

    public PublishValidator(TagNormalizer tagNormalizer, PostProperties properties) {
        this.tagNormalizer = tagNormalizer;
        this.properties = properties;
    }

    /** 검사를 통과한 값. */
    public record Validated(String title, String contentMd, List<String> tags, String visibility, long baseVersion) {
    }

    public Validated validate(PublishCommand command) {
        if (command == null || command.baseVersion() == null || command.baseVersion() < 0) {
            throw new PostContentException(PostContentRules.INVALID_REQUEST);
        }
        List<FieldError> errors = new ArrayList<>();

        String title = PostTitleRules.clean(command.title());
        if (title.isEmpty()) {
            errors.add(FieldError.of("title", "TITLE_REQUIRED"));
        } else if (title.codePointCount(0, title.length()) > properties.titleMaxLength()) {
            errors.add(FieldError.of("title", PostContentRules.TITLE_TOO_LONG));
        }

        String content = PostContentRules.normalizeContent(command.contentMd());
        if (content.isBlank()) {
            errors.add(FieldError.of("contentMd", "CONTENT_REQUIRED"));
        } else if (content.codePointCount(0, content.length()) > properties.contentMaxLength()) {
            errors.add(FieldError.of("contentMd", PostContentRules.CONTENT_TOO_LONG));
        } else if (PENDING_IMAGE.matcher(content).find()) {
            errors.add(FieldError.of("contentMd", "PENDING_IMAGES"));
        }

        Set<String> tags = new LinkedHashSet<>();
        List<String> rawTags = command.tags() == null ? List.of() : command.tags();
        for (int i = 0; i < rawTags.size(); i++) {
            try {
                tags.add(tagNormalizer.normalize(rawTags.get(i)));
            } catch (TagRejectedException e) {
                errors.add(FieldError.of("tags[" + i + "]", e.getCode()));
            }
        }
        if (tags.size() > properties.maxTags()) {
            errors.add(FieldError.of("tags", "TOO_MANY_TAGS"));
        }

        String visibility = command.visibility();
        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) {
            errors.add(FieldError.of("visibility", "INVALID_VISIBILITY"));
        }

        if (!errors.isEmpty()) {
            throw new ProfileValidationException(errors);
        }
        return new Validated(title, content, List.copyOf(tags), visibility, command.baseVersion());
    }
}

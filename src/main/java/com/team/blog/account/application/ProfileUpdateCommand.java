package com.team.blog.account.application;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 프로필 저장 요청(11 §5). 보낸 칸만 바꾼다 — 칸마다 "보냄 여부 + 값(NULL 가능)"을 따로 가진다.
 * 회원 식별자는 담지 않는다(헌법 III: 대상은 {@code CurrentUser}).
 *
 * @param nickname       보냄 + 값. {@code null} 값은 "보내지 않음"과 같게 본다(research R-2)
 * @param bio            보냄 + 값. {@code null} 값은 빈 소개
 * @param profileImageId 보냄 + 값. {@code null} 값은 기본 이미지로
 * @param invalidFields  타입이 맞지 않는 칸 이름({@code INVALID_VALUE}로 보고)
 */
public record ProfileUpdateCommand(Field<String> nickname, Field<String> bio, Field<Long> profileImageId,
                                   Set<String> invalidFields) {

    /** 칸 하나: {@code present}가 거짓이면 그 칸을 보내지 않은 것이다. */
    public record Field<T>(boolean present, T value) {

        private static final Field<?> ABSENT = new Field<>(false, null);

        @SuppressWarnings("unchecked")
        public static <T> Field<T> absent() {
            return (Field<T>) ABSENT;
        }

        public static <T> Field<T> of(T value) {
            return new Field<>(true, value);
        }
    }

    public ProfileUpdateCommand {
        nickname = nickname == null ? Field.absent() : nickname;
        bio = bio == null ? Field.absent() : bio;
        profileImageId = profileImageId == null ? Field.absent() : profileImageId;
        invalidFields = invalidFields == null ? Set.of() : Set.copyOf(invalidFields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Field<String> nickname = Field.absent();
        private Field<String> bio = Field.absent();
        private Field<Long> profileImageId = Field.absent();
        private final Set<String> invalidFields = new LinkedHashSet<>();

        public Builder nickname(String value) {
            this.nickname = Field.of(value);
            return this;
        }

        public Builder bio(String value) {
            this.bio = Field.of(value);
            return this;
        }

        public Builder profileImageId(Long value) {
            this.profileImageId = Field.of(value);
            return this;
        }

        public Builder invalid(String field) {
            this.invalidFields.add(field);
            return this;
        }

        public ProfileUpdateCommand build() {
            return new ProfileUpdateCommand(nickname, bio, profileImageId, invalidFields);
        }
    }
}

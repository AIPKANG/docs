package com.team.blog.account.web;

import com.team.blog.account.application.ProfileService;
import com.team.blog.account.application.ProfileUpdateCommand;
import com.team.blog.account.application.ProfileView;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 프로필 API(contracts/web-routes.md §2). 경로에 회원 식별자가 없다 — 대상은 로그인 정보로만 정한다(FR-002).
 * 본문은 JSON 트리로 읽어 "보내지 않음"과 {@code null}을 구분한다. 알 수 없는 칸({@code handle}, {@code email} 등)은 무시한다(FR-003).
 */
@RestController
@RequestMapping("/api/me/profile")
public class ProfileApiController {

    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public ProfileApiController(ProfileService profileService, CurrentUserProvider currentUserProvider) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ProfileView view() {
        return profileService.view(currentUserProvider.current());
    }

    @PatchMapping
    public ProfileView update(@RequestBody JsonNode body) {
        return profileService.update(currentUserProvider.current(), toCommand(body));
    }

    static ProfileUpdateCommand toCommand(JsonNode body) {
        ProfileUpdateCommand.Builder builder = ProfileUpdateCommand.builder();
        if (body == null || !body.isObject()) {
            return builder.invalid("body").build();
        }
        JsonNode nickname = body.get("nickname");
        if (nickname != null) {
            if (nickname.isNull()) {
                // null 닉네임은 "보내지 않음"과 같다(research R-2)
            } else if (nickname.isString()) {
                builder.nickname(nickname.stringValue());
            } else {
                builder.invalid("nickname");
            }
        }
        JsonNode bio = body.get("bio");
        if (bio != null) {
            if (bio.isNull()) {
                builder.bio(null);
            } else if (bio.isString()) {
                builder.bio(bio.stringValue());
            } else {
                builder.invalid("bio");
            }
        }
        JsonNode image = body.get("profileImageId");
        if (image != null) {
            if (image.isNull()) {
                builder.profileImageId(null);
            } else if (image.isIntegralNumber() && image.canConvertToLong() && image.longValue() > 0) {
                builder.profileImageId(image.longValue());
            } else {
                builder.invalid("profileImageId");
            }
        }
        return builder.build();
    }
}

package com.team.blog.account.integration;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 다른 패키지(media) 테스트가 프로필 저장 요청을 만들 때 쓰는 공개 도우미. */
public final class ProfileImageTestAccess {

    private ProfileImageTestAccess() {
    }

    public static MockHttpServletRequestBuilder patchProfile(long memberId, String json) {
        return ProfileTestSupport.patchProfile(memberId, json);
    }
}

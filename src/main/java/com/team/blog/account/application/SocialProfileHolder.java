package com.team.blog.account.application;

import com.team.blog.account.domain.SocialProfile;

/** OAuth2/OIDC 사용자 서비스가 돌려주는 principal이 정리된 {@link SocialProfile}을 함께 들고 있게 하는 표시. */
public interface SocialProfileHolder {

    SocialProfile socialProfile();
}

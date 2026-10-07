package com.team.blog.media.domain;

/** 사진 용도({@code image.purpose}, 11 §7). 003은 {@code PROFILE}만 받고, 008이 {@code POST}를 받는다. */
public enum ImagePurpose {
    POST,
    PROFILE
}

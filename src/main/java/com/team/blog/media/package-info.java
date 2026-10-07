/**
 * 사진(이미지) 업로드·검사·정리를 소유하는 media 모듈(02 §3). {@code image} 테이블과 객체 저장소({@code ImageStorage})를 가진다.
 * 다른 모듈은 application 패키지의 공개 Service로만 쓰고, media는 account 테이블을 읽지 않는다 — 참조 확인은
 * SPI {@code ImageReferenceLookup}으로 묻는다(헌법 I). 003은 프로필 용도의 최소만 만들고 008이 글 사진을 더한다.
 */
package com.team.blog.media;

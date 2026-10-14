# Feature Specification: 문의하기

**Feature Branch**: `090-contact` | **Created**: 2026-10-14 | **Status**: Implemented (1.38.0)

**Scope**: **개인 확장**. 구현 PR practicech/blog#136.

## 원인
- [문의하기]가 메일 앱(mailto)을 열었고, 그 주소가 관리자 지정 주소(`ADMIN_EMAILS` 첫 주소)라 관리자의 개인 메일이 화면에 드러났다.

## 결정
- 꼬리말·☰ 메뉴·설정의 [문의하기]는 사이트 안 `/contact` 양식으로 간다. 문의 종류(계정·로그인 / 버그 신고 / 기능 제안 / 기타), 제목, 내용, 답장 받을 이메일. 보내면 완료 화면.
- 로그인한 회원은 계정 이메일이 채워져 있고 바꿀 수 없다. 한 IP에서 한 시간 5개까지.
- 새 문의는 관리자 모두에게 운영 알림. 관리 메뉴 **문의함**에서 새 문의 / 처리 완료를 보고, 답장하면 메일이 가고 처리 완료가 된다.
- 문의 주소는 `blog.contact.email`(`CONTACT_EMAIL`, 기본은 사이트 발송 계정)로 따로 두고, 관리자 지정 주소는 어느 화면에도 쓰지 않는다.
- 끄기: `blog.contact.enabled=false`(`CONTACT_ENABLED`) → 예전처럼 메일 링크(문의 주소).
- `contact`는 예약 주소(블로그 주소로 읽지 않음). 탈퇴 정리 때 그 회원의 문의를 지운다. 개인정보 처리방침에 한 줄.

## 데이터
- `inquiry` 표(Flyway V21, 새 표만), 알림 종류 제약에 `INQUIRY` 추가. V20(089) 다음에 적용.

## Success Criteria
- `ContactIT`·`ContactDisabledIT`: 양식·검증·회원 이메일 고정·관리자만 문의함·답장 메일·탈퇴 정리·IP 횟수 제한, 관리자 주소가 어느 화면에도 없음. 전체 테스트 통과.

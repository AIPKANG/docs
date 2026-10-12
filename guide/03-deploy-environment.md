# 3. 배포 환경

```
브라우저 ──https──▶ 학교 공용 Nginx (s2.java21.net, geulgreen.java21.net.conf)
                         │ 8300
                         ▼
               [도커] team-blog-app (Spring Boot, JRE 21, 사용자 blog)
                   │            │                    │
           team-blog-net   team-blog-data 볼륨     인터넷
                   ▼        (올린 사진)              ├─ Crowfoot PostgreSQL (s3.java21.net, 스키마 cf_u39_d1)
           [도커] Redis                              ├─ Gmail SMTP (가입 인증 메일)
     (세션·자동 저장 버퍼·조회수·요청 제한)            ├─ Google·GitHub 로그인(OAuth)
                                                     └─ Gemini API (키가 있을 때만)
```

| 항목 | 값 |
|---|---|
| 서버 | 학교 공용 서버 s2.java21.net, SSH 8822(비밀번호), 내게 배정된 포트 8300~8309 중 8300 |
| 주소 | https://geulgreen.java21.net (HTTPS는 학교 Nginx가 처리) |
| 앱 | 도커 이미지 하나(`team-blog:latest`), Java 21 + Spring Boot 4.1, `prod` 프로필, 메모리 75%까지 |
| DB | Crowfoot이 발급한 PostgreSQL. 스키마 변경은 앱이 켜질 때 Flyway가 V1~V14 순서로 적용 |
| Redis | 앱 옆 컨테이너. 배포 때 다시 만들지 않아 로그인이 유지됨 |
| 사진 | 서버 디스크(도커 볼륨). 서버를 늘리면 설정만 바꿔 MinIO(S3 호환)로 옮김 |
| 메일 | Gmail SMTP(앱 비밀번호) |
| 비밀값 | GitHub Repository secrets → 배포 때 서버 `app.env`(0600)로. 이미지·저장소에는 없음 |
| 상태 확인 | 도커 HEALTHCHECK가 30초마다 `/actuator/health/liveness` |

## 환경별 차이
| | 로컬(개발) | 테스트(GitHub Actions) | 운영 |
|---|---|---|---|
| DB·Redis·메일·사진 | `compose.yaml`로 띄운 PostgreSQL·Redis·Mailpit·MinIO | Testcontainers가 테스트 때만 띄움 | Crowfoot PG·Redis 컨테이너·Gmail·서버 디스크 |
| 실행 | `./gradlew bootRun` | `./gradlew test` | `Dockerfile` 이미지 |
| 비밀값 | `.env`(git 제외) | 없음(가짜 값) | Repository secrets |

예비안: 학교 서버 대신 서버 한 대를 직접 쓸 때(`compose.prod.yaml` + 자체 Nginx·Let's Encrypt)는 소스 저장소 `deploy/README.md`.

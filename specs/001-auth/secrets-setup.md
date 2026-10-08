# 001 남은 확인: 소셜 로그인·Gmail 키 준비

T176(소셜 로그인 실제 확인)과 T177(Gmail 실제 발송)을 끝내려면 키 5개가 필요하다. 키는 저장소에 올리지 않고 `~/Documents/blog/.env`에만 둔다(`.gitignore`에 이미 있음).

## 1. Google 로그인 키 (약 10분)
1. https://console.cloud.google.com 접속 → 위쪽 프로젝트 선택 → **새 프로젝트** (이름 예: `team-blog`)
2. 왼쪽 메뉴 **API 및 서비스 → OAuth 동의 화면** → 사용자 유형 **외부** → 앱 이름·지원 이메일 입력 → 범위는 기본(`email`, `profile`, `openid`) → **테스트 사용자**에 로그인해 볼 내 Gmail 추가
3. **사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**
   - 애플리케이션 유형: **웹 애플리케이션**
   - 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`
4. 만들어진 **클라이언트 ID** → `GOOGLE_CLIENT_ID`, **클라이언트 보안 비밀번호** → `GOOGLE_CLIENT_SECRET`

## 2. GitHub 로그인 키 (약 3분)
1. https://github.com/settings/developers → **OAuth Apps → New OAuth App**
2. Homepage URL: `http://localhost:8080`, Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
3. **Client ID** → `GITHUB_CLIENT_ID`, **Generate a new client secret** → `GITHUB_CLIENT_SECRET` (한 번만 보이니 바로 복사)

## 3. Gmail 앱 비밀번호 (약 3분)
1. 보낼 Gmail 계정에 **2단계 인증**이 켜져 있어야 한다: https://myaccount.google.com/security
2. https://myaccount.google.com/apppasswords → 앱 이름(예: `team-blog`) → **만들기** → 16자리 비밀번호
3. Gmail 주소 → `MAIL_USERNAME`, 16자리(띄어쓰기 없이) → `MAIL_PASSWORD`

## 4. `.env`에 넣기
`~/Documents/blog/.env` 파일을 만들고 아래처럼 채운다(따옴표 없이).
```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
MAIL_USERNAME=보낼주소@gmail.com
MAIL_PASSWORD=16자리앱비밀번호
```

## 5. 확인 (Claude가 진행)
- T176: 앱을 `http://localhost:8080`으로 띄우면, 브라우저에서 [Google로 로그인]·[GitHub로 로그인]을 **본인이 눌러** 계정 선택·허용을 한다(비밀번호 입력은 본인만). 첫 로그인 → 주소·닉네임 입력 화면 → 가입 완료, 같은 계정으로 다시 로그인 → 바로 로그인, 같은 이메일의 이메일 가입이 있으면 안내가 나오는지 Claude가 확인한다.
- T177: `SPRING_PROFILES_ACTIVE=prod`로 띄워 키가 없을 때 기동이 멈추는지, 키가 있을 때 가입 인증 메일 1통이 실제 Gmail로 오는지 확인한다.

주의: 키를 채팅에 붙여 넣지 말 것(대화 기록에 남음). 파일에만 넣고 "넣었어"라고만 알려 주면 된다.

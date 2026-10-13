# 2·8. Git 버전 관리 규칙과 깃플로우

## 지금까지(1.13.0까지)
`main` 하나에 바로 커밋·push = 바로 배포. 브랜치·PR 없이 진행했고, 릴리스마다 태그와 CHANGELOG 절만 남겼습니다.

## 1.14.0부터: 깃플로우
피드백("배포 전 브랜치를 만들어서 작업 후 머지, AI와 규칙을 잡아 진행")에 맞춰 바꿨습니다. 첫 적용이 백엔드·프론트 분리 작업입니다.

| 브랜치 | 어디서 따나 | 어디로 머지 | 쓰임 |
|---|---|---|---|
| `main` | — | — | 배포된 코드만. 머지되면 자동 배포, 릴리스마다 태그 |
| `develop` | `main` | — | 다음 릴리스를 모으는 곳 |
| `feature/<기능>` | `develop` | `develop` (PR) | 기능 하나 |
| `release/X.Y.Z` | `develop` | `main` (PR) → `develop` | 배포 직전 CHANGELOG·버전 정리 |
| `hotfix/X.Y.Z` | `main` | `main` → `develop` | 운영 급한 수정 |

흐름: `feature/…` → PR → (GitHub에서 테스트 656개) → `develop` 머지 → `release/X.Y.Z` → PR → `main` 머지(= 배포) → `vX.Y.Z` 태그·GitHub Release → `develop`에도 머지 → 과제 제출 저장소에 같은 내용 올리기.

## 버전 번호 (SemVer)
- MAJOR: 팀 공통 요구사항이 바뀔 때(1.0.0 = 팀 공통 최소 요구사항 완성)
- MINOR: 새 기능(명세 하나 또는 묶음). 예: 063 설정 섹션 → 1.13.0
- PATCH: 버그·디자인 수정
- 새로 바뀐 것이 생길 때마다 릴리스를 냅니다: `CHANGELOG.md`에 `## [X.Y.Z] — 날짜 · 강성찬 개인 확장 (기능)` 절, 주석 태그 `vX.Y.Z`, 같은 내용의 **GitHub Release**
- GitHub Release는 소스 저장소와 과제 제출 저장소([AIP-1/blog-basic-AIGJ_01_001-geulgreen](https://github.com/AIP-1/blog-basic-AIGJ_01_001-geulgreen)) 둘 다에 올립니다(v1.0.0~v1.14.1은 10-13에 CHANGELOG로 채움)

## 커밋·PR 규칙
- 커밋: 한국어, `<명세 번호> <기능>(강성찬 개인 확장): 무엇을 했는지 — 릴리스, 테스트 수`
- PR: `.github/pull_request_template.md`(무엇을 바꿨나 / 전·후 / 확인한 것 / 배포할 때 DB·secrets)
- 리뷰어: `.github/CODEOWNERS`
- 명세(무엇을 왜)는 문서 저장소 `specs/NNN-이름/`, 구현은 소스 저장소. 두 저장소를 나눈 이유는 [05-file-management.md](05-file-management.md)

## 하루 작업 저장
하루 일정이 끝나면 끝나지 않은 작업도 그 브랜치에 커밋·push해 둡니다(커밋 메시지에 "진행 중"이라고 적음). `main`·`develop`에는 머지하지 않으므로 사이트에는 영향이 없습니다.

## 문서 저장소
문서 저장소(이 저장소)는 배포가 없어 `develop` 없이 `docs/<주제>` 브랜치 → PR → `main` 머지로 고칩니다.

## AI와 함께 쓰는 규칙
Claude Code가 작업할 때도 같은 규칙을 따릅니다: 기능마다 `feature/` 브랜치, PR 본문은 템플릿대로, GitHub 테스트가 초록일 때만 머지, `main`에는 직접 push하지 않음, 되돌리기는 `git revert`(강제 push 금지). 이 규칙은 프로젝트 메모리에 적어 두어 다음 작업에서도 이어집니다.

## 아직 안 한 것
- GitHub 브랜치 보호 규칙(`main`·`develop`에 PR·테스트 필수)은 저장소 설정이라 아직 켜지 않았습니다. 켜면 규칙을 실수로 어기는 일을 GitHub가 막아 줍니다.

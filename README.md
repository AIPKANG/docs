# 블로그 플랫폼

[GitHub Spec Kit](https://github.com/github/spec-kit)으로 진행하는 팀 블로그 프로젝트입니다.

## 폴더 구성

| 경로 | 내용 |
|---|---|
| `docs/` | 팀이 작성한 원본 설계 문서 (요구사항·아키텍처·ERD·기능별 설계). 결정 근거는 여기에 있습니다. |
| `.specify/memory/constitution.md` | 프로젝트 헌법. `docs/01`·`docs/02`의 확정 원칙을 옮겼습니다. |
| `specs/NNN-기능/spec.md` | 기능 명세. `docs/`의 기능 문서를 Spec Kit 형식으로 다시 정리했습니다. |
| `specs/NNN-기능/source-notes.md` | plan 단계에서 참고할 기술 결정과 원문 위치 |
| `.claude/skills/speckit-*` | Claude Code에서 쓰는 Spec Kit 명령 |

## 진행 순서

기능마다 아래 순서로 진행합니다.

1. `/speckit-specify` 기능 명세 (완료: `specs/`)
2. `/speckit-clarify` 남은 질문 정리 (선택)
3. `/speckit-plan` 구현 계획 (`docs/03`·`docs/51` ERD와 `source-notes.md` 참고)
4. `/speckit-tasks` 작업 목록
5. `/speckit-implement` 구현

우선순위는 Tier A(공통 필수) → Tier B(공통 권장) → Tier C(2차 후보) 순입니다.

# Implementation Plan: 권한 매트릭스

**Branch**: `012-access-permission` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-OWN-1(Tier A, 공통 규칙). 판정 장치는 001·006·각 Service에 이미 있으므로, 구현된 기능의 권한 표(1·2·7·8)를 한 곳의 데이터 기반 통합 테스트로 고정하고 판정 순서 사례를 더한다. 나머지 표는 해당 기능이 행을 추가한다.

## Constitution Check
원칙 III·VI의 검증 그 자체. 위반 없음.

## Project Structure
```text
src/test/java/com/team/blog/shared/security/PermissionMatrixIT.java
```

# Implementation Plan: 다크 모드

**Branch**: `024-dark-mode` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
화면 공통 스타일을 레이아웃의 `<style>`에서 `/css/site.css`로 옮기고 모든 색을 `/css/theme.css`의 색 역할(`--color-*`)로 바꿨다. 테마는 `<html data-theme>`(라이트·다크 고정) 또는 없음(시스템 설정 = `prefers-color-scheme`)으로 정하고, `<head>`의 외부 스크립트 `theme-init.js`가 그리기 전에 저장된 선택을 적용한다. 헤더 맨 오른쪽 버튼이 시스템 → 라이트 → 다크를 돌고 이 기기 브라우저에만 저장한다. 스크립트가 없으면 기기 설정을 따르고 버튼은 숨겨진다.

## Constitution Check
I(화면 공통, 서버 코드 변경 없음) · II(색 역할 이름은 공통 목록 그대로, 값만 여기서) · IV(인라인 스크립트 없음, CSP `script-src 'self'` 그대로) · VI(통합 테스트로 머리말 순서·인라인 스크립트·직접 쓴 색 0건 확인, 브라우저 확인) — 위반 없음.

## Project Structure
```text
static/css/{theme.css(색 역할·라이트/다크), site.css(공통 스타일)}, static/js/{theme-init.js, theme-toggle.js}
templates/layout/base.html(color-scheme, 초기 스크립트, 버튼), static/js/post/list-more.js(아이콘 색 클래스)
tests: shared/web/ThemeIT
```

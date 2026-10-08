/**
 * AI 태그 추천(021, 34). 작성자가 버튼을 누를 때만, 동의한 회원의 자기 공개 글에 대해 Gemini(무료 등급) → 한도·장애 때 Ollama(자체 서버)로
 * 추천을 받고, 결과는 Redis에 저장해 재사용한다. 어떤 실패도 글쓰기·자동 저장·발행에 영향을 주지 않는다.
 */
package com.team.blog.tag.application.suggest;

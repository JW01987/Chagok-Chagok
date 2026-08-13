#!/usr/bin/env python3
"""
scripts/review.py
(GitHub Actions에서 실행)

PR의 코드 diff를 Gemini API로 리뷰하고 review-result.json 을 생성한다.
이 결과를 score_decision.py 가 읽어서 점수 계산 + 텔레그램 알림을 보낸다.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

import requests

from google import genai

_SYSTEM_PROMPT = """당신은 숙련된 Java 백엔드 코드 리뷰어입니다.
Spring Boot 3.x, JPA, Spring Security 기반의 금융 앱 코드를 리뷰합니다.
반드시 아래 JSON 형식으로만 응답하세요 (다른 텍스트 없이):

{
  "scores": {
    "readability": <0-100 정수>,
    "logic": <0-100 정수>,
    "exception_handling": <0-100 정수>,
    "test_code": <0-100 정수>,
    "security": <0-100 정수>
  },
  "comments": {
    "readability": "<한 줄 코멘트>",
    "logic": "<한 줄 코멘트>",
    "exception_handling": "<한 줄 코멘트>",
    "test_code": "<한 줄 코멘트>",
    "security": "<한 줄 코멘트>"
  },
  "summary": "<전체 요약 2-3문장>"
}"""

_EMPTY_RESULT = {
    "scores": {k: 100 for k in ["readability", "logic", "exception_handling", "test_code", "security"]},
    "comments": {k: "변경사항 없음" for k in ["readability", "logic", "exception_handling", "test_code", "security"]},
    "summary": "코드 변경사항이 없습니다.",
}

# Gemini 2.5 Flash는 컨텍스트가 넉넉해서(1M 토큰) 훨씬 크게 잡아도 된다.
# 그래도 극단적으로 큰 PR을 대비해 상한은 둔다.
_MAX_DIFF_CHARS = 200_000


def _changed_java_files() -> list[str]:
    """origin/main 대비 변경된 .java 파일 목록.

    예전에는 pathspec을 "src/**/*.java"로 고정해서 backend/src/... 처럼
    하위 디렉터리에 있는 실제 경로와 전혀 매칭되지 않았다 (repo 구조에
    묶인 버그). 디렉터리 프리픽스 없는 "*.java"는 git이 어느 깊이에서든
    파일명으로 매칭해주므로 repo 구조가 바뀌어도 안전하다.
    """
    result = subprocess.run(
        ["git", "diff", "--name-only", "origin/main...HEAD", "--", "*.java"],
        capture_output=True, text=True,
    )
    return [f for f in result.stdout.splitlines() if f]


def _diff_for_paths(paths: list[str]) -> str:
    if not paths:
        return ""
    result = subprocess.run(
        ["git", "diff", "origin/main...HEAD", "--", *paths],
        capture_output=True, text=True, check=True,
    )
    return result.stdout


def get_pr_diff() -> str:
    files = _changed_java_files()
    if not files:
        # .java 변경이 없거나 목록을 못 얻었으면 전체 diff로 폴백
        result = subprocess.run(
            ["git", "diff", "origin/main...HEAD"],
            capture_output=True, text=True, check=True,
        )
        return result.stdout[:_MAX_DIFF_CHARS]

    # git diff는 pathspec 인자 순서와 무관하게 항상 트리 순서(경로 알파벳순)로
    # 출력하기 때문에, "test" 디렉터리가 "main"보다 뒤에 와서 큰 PR에서는
    # _MAX_DIFF_CHARS에 잘려 테스트 코드가 리뷰 대상에서 통째로 빠지는 일이
    # 있었다. 테스트 파일 diff를 먼저 뽑아 앞에 붙여서, 잘리더라도 테스트
    # 코드가 항상 살아남도록 한다.
    test_files = [f for f in files if "/test/" in f or f.endswith("Test.java")]
    main_files = [f for f in files if f not in test_files]

    diff = _diff_for_paths(test_files) + _diff_for_paths(main_files)
    return diff[:_MAX_DIFF_CHARS]


def parse_gemini_response(text: str) -> dict:
    """코드블록 마크다운을 걷어내고 JSON 파싱."""
    text = text.strip()
    if "```" in text:
        parts = text.split("```")
        # ```json ... ``` 형태에서 중간 부분 추출
        text = parts[1]
        if text.startswith("json"):
            text = text[4:]
    return json.loads(text.strip())


def send_telegram_error(message: str) -> None:
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat_id:
        return
    requests.post(
        f"https://api.telegram.org/bot{token}/sendMessage",
        json={"chat_id": chat_id, "text": message, "parse_mode": "HTML"},
        timeout=10,
    )


def main() -> None:
    client = genai.Client(api_key=os.environ["GEMINI_API_KEY"])

    diff = get_pr_diff()
    if not diff.strip():
        print("변경된 Java 파일 없음 — 리뷰 스킵")
        Path("review-result.json").write_text(
            json.dumps(_EMPTY_RESULT, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return

    prompt = f"{_SYSTEM_PROMPT}\n\n[코드 diff]\n```diff\n{diff}\n```"
    try:
        response = client.models.generate_content(model="gemini-2.5-flash", contents=prompt)
    except Exception as e:
        err = str(e)
        if "429" in err or "RESOURCE_EXHAUSTED" in err:
            pr_number = os.environ.get("PR_NUMBER", "?")
            send_telegram_error(
                f"⚠️ <b>PR #{pr_number} AI 리뷰 실패 — Gemini 쿼터 초과</b>\n\n"
                "무료 티어 일일 한도를 초과했어요.\n"
                "내일 다시 push하거나 Google Cloud 결제를 활성화해주세요."
            )
        else:
            send_telegram_error(
                f"🚨 <b>PR #{os.environ.get('PR_NUMBER', '?')} AI 리뷰 오류</b>\n<code>{err[:300]}</code>"
            )
        print(f"Gemini API 오류: {err}")
        sys.exit(1)

    try:
        result = parse_gemini_response(response.text)
    except (json.JSONDecodeError, IndexError) as e:
        print(f"Gemini 응답 파싱 실패: {e}\n원문:\n{response.text}")
        sys.exit(1)

    Path("review-result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("리뷰 완료 → review-result.json 생성")


if __name__ == "__main__":
    main()

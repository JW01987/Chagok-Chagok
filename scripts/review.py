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

_MAX_DIFF_CHARS = 15_000  # Gemini 토큰 한도 대비


def get_pr_diff() -> str:
    result = subprocess.run(
        ["git", "diff", "origin/main...HEAD", "--", "src/**/*.java"],
        capture_output=True, text=True,
    )
    diff = result.stdout
    if not diff:
        # paths glob 이 안 먹힐 경우 fallback
        result = subprocess.run(
            ["git", "diff", "origin/main...HEAD"],
            capture_output=True, text=True, check=True,
        )
        diff = result.stdout
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
        response = client.models.generate_content(model="gemini-2.0-flash", contents=prompt)
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

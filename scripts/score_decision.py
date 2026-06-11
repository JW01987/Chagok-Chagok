#!/usr/bin/env python3
"""
scripts/score_decision.py
(GitHub Actions에서 실행)

review.py 가 생성한 review-result.json 을 읽어서:
  1. PR에 상세 리뷰 코멘트 작성
  2. 텔레그램으로 점수 요약 + "통과 / 다시 생성 / 보류" 인라인 버튼 전송

callback_data 포맷: "decision:{ACTION}:{PR_NUMBER}"
  - ACTION: PASS | RETRY | HOLD
로컬에서 상시 실행 중인 auto_pilot.py 가 이 콜백을 받아서 처리한다.
"""

import json
import os
from pathlib import Path

import requests

# ── 진이가 직접 조정하는 통과 기준 (참고용 표시 — 최종 판단은 진이가 함) ─────
CRITERIA: dict[str, dict] = {
    "readability":        {"label": "가독성",  "pass": 80},
    "logic":              {"label": "로직",    "pass": 90},
    "exception_handling": {"label": "예외처리", "pass": 80},
    "test_code":          {"label": "테스트",  "pass": 85},
    "security":           {"label": "보안",    "pass": 90},
}
# ─────────────────────────────────────────────────────────────────────────────


def post_github_comment(body: str) -> None:
    resp = requests.post(
        f"https://api.github.com/repos/{os.environ['REPO']}/issues/{os.environ['PR_NUMBER']}/comments",
        headers={
            "Authorization": f"token {os.environ['GITHUB_TOKEN']}",
            "Accept": "application/vnd.github+json",
        },
        json={"body": body},
        timeout=15,
    )
    if not resp.ok:
        print(f"[GitHub 코멘트 실패] {resp.text}")


def send_telegram_with_buttons(
    score_lines: str,
    summary: str,
    pr_number: str,
    branch: str,
    all_passed: bool,
) -> None:
    status = (
        "✅ 모든 항목이 기준을 통과했어요."
        if all_passed
        else "⚠️ 일부 항목이 기준에 못 미쳤어요. 그래도 최종 판단은 진이 몫!"
    )
    message = (
        f"📊 <b>PR #{pr_number} 리뷰 결과</b>\n"
        f"브랜치: <code>{branch}</code>\n\n"
        f"{score_lines}\n{status}\n\n"
        f"📝 {summary}\n\n"
        "아래 버튼으로 다음 작업을 결정해주세요 👇"
    )
    resp = requests.post(
        f"https://api.telegram.org/bot{os.environ['TELEGRAM_BOT_TOKEN']}/sendMessage",
        json={
            "chat_id": os.environ["TELEGRAM_CHAT_ID"],
            "text": message,
            "parse_mode": "HTML",
            "reply_markup": {
                "inline_keyboard": [
                    [
                        {
                            "text": "✅ 통과 (머지 + 다음 Task)",
                            "callback_data": f"decision:PASS:{pr_number}",
                        },
                        {
                            "text": "🔄 다시 생성",
                            "callback_data": f"decision:RETRY:{pr_number}",
                        },
                    ],
                    [
                        {
                            "text": "⏸️ 보류",
                            "callback_data": f"decision:HOLD:{pr_number}",
                        }
                    ],
                ]
            },
        },
        timeout=15,
    )
    if not resp.ok:
        print(f"[텔레그램 전송 실패] {resp.text}")


def main() -> None:
    result = json.loads(Path("review-result.json").read_text(encoding="utf-8"))
    scores = result["scores"]
    comments = result["comments"]
    summary = result["summary"]
    pr_number = os.environ["PR_NUMBER"]
    branch = os.environ.get("PR_BRANCH", "(알 수 없음)")

    all_passed = True
    score_lines = ""
    comment_parts: list[str] = []

    for key, meta in CRITERIA.items():
        score = scores[key]
        passed = score >= meta["pass"]
        if not passed:
            all_passed = False
        emoji = "✅" if passed else "❌"
        score_lines += f"{emoji} <b>{meta['label']}</b>: {score}점 (기준 {meta['pass']}점)\n"
        comment_parts.append(
            f"### {emoji} {meta['label']}: {score}점 (기준 {meta['pass']}점)\n> {comments[key]}"
        )

    comment_body = (
        "## 📊 AI 코드 리뷰 결과\n\n"
        + "\n\n".join(comment_parts)
        + f"\n\n---\n📝 **요약**: {summary}\n\n"
        + "> 최종 진행 여부는 텔레그램에서 진이가 직접 결정합니다 (통과 / 다시 생성 / 보류)."
    )

    post_github_comment(comment_body)
    send_telegram_with_buttons(score_lines, summary, pr_number, branch, all_passed)

    Path("pr-context.json").write_text(
        json.dumps(
            {"prNumber": pr_number, "branch": branch, "repo": os.environ["REPO"]},
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"PR #{pr_number} 리뷰 결과 전송 완료. 진이의 결정을 기다립니다.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
scripts/auto_pilot.py

진이 컴퓨터에서 계속 켜두는 로컬 자동화 데몬.

하는 일:
 1. 텔레그램을 주기적으로 폴링하면서 "통과 / 다시 생성 / 보류" 버튼 클릭을 기다림
 2. 통과  → PR 머지 + TASKS.md 에서 "상태: 대기" Task를 찾아 Claude Code 자동 실행
 3. 다시 생성 → 리뷰 피드백을 반영해서 같은 브랜치에서 Claude Code 재실행
 4. 보류  → 아무 것도 하지 않고 알림만 남김

실행:
  python scripts/auto_pilot.py

환경변수 (.env.auto-pilot):
  TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, GITHUB_TOKEN, REPO
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

import requests
from dotenv import load_dotenv

# .env.auto-pilot 을 레포 루트에서 읽음
_ROOT = Path(__file__).resolve().parent.parent
load_dotenv(dotenv_path=_ROOT / ".env.auto-pilot")

POLL_INTERVAL = 15  # 초
STATE_FILE = _ROOT / ".auto-pilot-state.json"
TASKS_DIR = _ROOT / "tasks"
LOGS_DIR = _ROOT / "docs" / "logs"
CONVENTIONS_FILE = "docs/CONVENTIONS.md"

_BOT_TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]
_CHAT_ID = os.environ["TELEGRAM_CHAT_ID"]
_GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]
REPO = os.environ["REPO"]

TG_BASE = f"https://api.telegram.org/bot{_BOT_TOKEN}"
GH_HEADERS = {
    "Authorization": f"token {_GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json",
}


# ── 상태 관리 ─────────────────────────────────────────────────────────────────

def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {"offset": 0, "handled_callbacks": []}


def save_state(state: dict) -> None:
    STATE_FILE.write_text(
        json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8"
    )


# ── 텔레그램 ──────────────────────────────────────────────────────────────────

def get_updates(offset: int) -> list:
    resp = requests.get(
        f"{TG_BASE}/getUpdates",
        params={"offset": offset, "timeout": 10},
        timeout=30,
    )
    return resp.json().get("result", [])


def answer_callback(callback_query_id: str, text: str) -> None:
    requests.post(
        f"{TG_BASE}/answerCallbackQuery",
        json={"callback_query_id": callback_query_id, "text": text},
        timeout=10,
    )


def send_telegram(message: str) -> None:
    resp = requests.post(
        f"{TG_BASE}/sendMessage",
        json={"chat_id": _CHAT_ID, "text": message, "parse_mode": "HTML"},
        timeout=10,
    )
    if not resp.ok:
        print(f"[텔레그램 전송 실패] {resp.text}")


# ── GitHub ────────────────────────────────────────────────────────────────────

def get_pr(pr_number: str) -> dict:
    resp = requests.get(
        f"https://api.github.com/repos/{REPO}/pulls/{pr_number}",
        headers=GH_HEADERS,
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()


def get_pr_comments(pr_number: str) -> list:
    resp = requests.get(
        f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments",
        headers=GH_HEADERS,
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()


def merge_pr(pr_number: str) -> None:
    print(f"→ PR #{pr_number} 머지 중...")
    # GITHUB_TOKEN을 환경에서 제거해 gh CLI가 자체 OAuth 토큰(repo scope)을 사용하게 함
    env = {k: v for k, v in os.environ.items() if k != "GITHUB_TOKEN"}
    subprocess.run(
        ["gh", "pr", "merge", str(pr_number), "--squash", "--delete-branch"],
        check=True,
        env=env,
    )


def get_latest_open_pr() -> dict | None:
    """가장 최근에 업데이트된 열린 PR을 반환한다."""
    resp = requests.get(
        f"https://api.github.com/repos/{REPO}/pulls",
        headers=GH_HEADERS,
        params={"state": "open", "sort": "updated", "direction": "desc", "per_page": 1},
        timeout=15,
    )
    resp.raise_for_status()
    prs = resp.json()
    return prs[0] if prs else None


def rerun_review_workflow(pr_number: str, branch: str) -> None:
    """PR 브랜치의 가장 최근 AI 리뷰 워크플로우 실행을 재실행한다 (실패한 job만)."""
    resp = requests.get(
        f"https://api.github.com/repos/{REPO}/actions/runs",
        headers=GH_HEADERS,
        params={"branch": branch, "event": "pull_request", "per_page": 1},
        timeout=15,
    )
    resp.raise_for_status()
    runs = resp.json().get("workflow_runs", [])
    if not runs:
        send_telegram(f"❌ PR #{pr_number} (<code>{branch}</code>)의 워크플로우 실행 기록을 찾지 못했어요.")
        return

    run_id = runs[0]["id"]
    rerun_resp = requests.post(
        f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}/rerun-failed-jobs",
        headers=GH_HEADERS,
        timeout=15,
    )
    if rerun_resp.ok:
        send_telegram(
            f"🔁 <b>PR #{pr_number} AI 리뷰 재시작</b>\n"
            f"브랜치: <code>{branch}</code>\n"
            "워크플로우를 다시 실행했어요. 잠시 후 리뷰 결과가 도착할 거예요!"
        )
    else:
        send_telegram(
            f"🚨 워크플로우 재실행 실패 (run #{run_id}):\n<code>{rerun_resp.text[:200]}</code>"
        )


# ── TASKS.md 파싱 ─────────────────────────────────────────────────────────────

def find_next_pending_task() -> dict | None:
    for task_file in sorted(TASKS_DIR.glob("phase*/BT-*.md")):
        content = task_file.read_text(encoding="utf-8")
        title_m = re.search(r"^# BT-(\d+)\s*\|\s*(.+)$", content, re.MULTILINE)
        status_m = re.search(r"^- \*\*상태\*\*:\s*(.+)$", content, re.MULTILINE)
        if title_m and status_m and "대기" in status_m.group(1):
            return {
                "number": title_m.group(1),
                "title": title_m.group(2).strip(),
                "file": str(task_file.relative_to(_ROOT)),
            }
    return None


# ── 작업 로그 ────────────────────────────────────────────────────────────────

def find_task_by_number(task_number: str) -> dict | None:
    """tasks/phase*/BT-{number}_*.md 에서 특정 번호의 Task 정보를 반환한다."""
    matches = list(TASKS_DIR.glob(f"phase*/BT-{task_number}_*.md"))
    if not matches:
        return None
    content = matches[0].read_text(encoding="utf-8")
    title_m = re.search(r"^# BT-(\d+)\s*\|\s*(.+)$", content, re.MULTILINE)
    if not title_m:
        return None
    return {
        "number": title_m.group(1),
        "title": title_m.group(2).strip(),
        "file": str(matches[0].relative_to(_ROOT)),
    }


def task_log_path(task_number: str) -> Path:
    date_str = datetime.now().strftime("%Y%m%d")
    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    return LOGS_DIR / f"bt-{task_number}_{date_str}.md"


def create_task_log(task_number: str, task_title: str, pr_number: str, branch: str) -> None:
    """Task 로그 파일을 생성한다. 이미 있으면 덮어쓰지 않고 이력 행만 추가한다."""
    log_path = task_log_path(task_number)
    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    if not log_path.exists():
        content = (
            f"# BT-{task_number} | {task_title}\n\n"
            f"- **날짜**: {datetime.now().strftime('%Y-%m-%d')}\n"
            f"- **PR**: #{pr_number}\n"
            f"- **브랜치**: `{branch}`\n\n"
            "## 진행 이력\n\n"
            "| 시각 | 이벤트 | 내용 |\n"
            "|---|---|---|\n"
            f"| {now} | ✅ 머지 완료 | PR #{pr_number} squash merge |\n"
        )
        log_path.write_text(content, encoding="utf-8")
    else:
        append_to_task_log(task_number, "✅ 머지 완료", f"PR #{pr_number} squash merge")

    print(f"→ 작업 로그 생성: {log_path.relative_to(_ROOT)}")


def append_to_task_log(task_number: str, event: str, detail: str) -> None:
    """기존 Task 로그에 이력 행을 추가한다."""
    log_path = task_log_path(task_number)
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    row = f"| {now} | {event} | {detail} |\n"

    if log_path.exists():
        with log_path.open("a", encoding="utf-8") as f:
            f.write(row)
    else:
        content = (
            f"# BT-{task_number} | (제목 미확인)\n\n"
            "## 진행 이력\n\n"
            "| 시각 | 이벤트 | 내용 |\n"
            "|---|---|---|\n"
            + row
        )
        log_path.write_text(content, encoding="utf-8")

    print(f"→ 작업 로그 업데이트: {log_path.relative_to(_ROOT)}")


def extract_task_number_from_branch(branch: str) -> str | None:
    """feature/bt-{number}-auto 형식에서 번호 추출."""
    m = re.search(r"bt-(\d+)", branch)
    return m.group(1) if m else None


# ── Claude Code 프롬프트 빌더 ─────────────────────────────────────────────────

def build_new_task_prompt(task_number: str, task_title: str, task_file: str) -> str:
    branch = f"feature/bt-{task_number}-auto"
    return f"""{CONVENTIONS_FILE} 와 {task_file} 를 읽어줘.

BT-{task_number} 을 구현해줘.

작업 순서:
1. CONVENTIONS.md 규칙 반드시 따를 것
2. {task_file} 에서 BT-{task_number} 요구사항 파악
3. 코드 작성 (Controller / Service / Repository / DTO / Exception 등 필요한 레이어)
4. 테스트 코드 작성 (JUnit5 + Mockito, 커버리지 80% 이상)
5. git checkout -b {branch}
6. git add .
7. git commit -m "feat: BT-{task_number} {task_title} 구현"
8. git push origin {branch}
9. gh pr create --title "[BT-{task_number}] {task_title}" --base main --body "BT-{task_number} 구현 완료 / 테스트 포함"

PR 생성 완료되면 PR 번호 알려줘."""


def build_retry_prompt(branch: str, review_comments: str) -> str:
    return f"""현재 브랜치({branch})에서 작업 중인 코드에 대해 AI 리뷰에서 개선 요청이 왔어.
docs/CONVENTIONS.md 규칙을 지키면서 아래 피드백을 반영해서 코드를 수정해줘.

[리뷰 피드백]
{review_comments}

작업 순서:
1. 위 피드백을 반영해서 코드 수정 (필요하면 테스트 코드도 보완)
2. git add .
3. git commit -m "fix: AI 리뷰 피드백 반영"
4. git push

작업 완료되면 알려줘."""


def run_claude_code(prompt: str) -> None:
    print("→ Claude Code 실행 중...\n")
    subprocess.run(
        ["claude", "-p", prompt, "--dangerously-skip-permissions"],
        check=True,
    )


def _commit_logs(message: str) -> None:
    """docs/logs/ 변경사항을 main 브랜치에 직접 커밋·푸시한다."""
    try:
        subprocess.run(["git", "checkout", "main"], check=True, capture_output=True)
        subprocess.run(["git", "add", str(LOGS_DIR)], check=True, capture_output=True)
        result = subprocess.run(
            ["git", "diff", "--cached", "--quiet"],
            capture_output=True,
        )
        if result.returncode != 0:  # staged 변경사항이 있을 때만 커밋
            subprocess.run(["git", "commit", "-m", message], check=True, capture_output=True)
            subprocess.run(["git", "push", "origin", "main"], check=True, capture_output=True)
            print(f"→ 로그 커밋 완료: {message}")
    except subprocess.CalledProcessError as e:
        print(f"[로그 커밋 실패 — 무시하고 계속] {e}")


# ── 액션 핸들러 ───────────────────────────────────────────────────────────────

def handle_pass(pr_number: str) -> None:
    send_telegram(f"✅ <b>PR #{pr_number} 통과 처리</b>\n머지를 진행할게요...")

    pr = get_pr(pr_number)
    merged_branch = pr["head"]["ref"]
    merged_task_number = extract_task_number_from_branch(merged_branch)
    merged_task_info = find_task_by_number(merged_task_number) if merged_task_number else None
    merged_task_title = merged_task_info["title"] if merged_task_info else pr.get("title", "")

    try:
        merge_pr(pr_number)
    except subprocess.CalledProcessError as e:
        send_telegram(f"🚨 PR #{pr_number} 머지 실패:\n<code>{e}</code>")
        return

    # 머지된 Task 로그 기록
    if merged_task_number:
        create_task_log(merged_task_number, merged_task_title, pr_number, merged_branch)
        _commit_logs(f"docs: Task-{merged_task_number} 작업 로그 추가")

    send_telegram(f"🎉 PR #{pr_number} 머지 완료! (CD가 자동으로 배포할 거예요)")

    task = find_next_pending_task()
    if not task:
        send_telegram('📋 TASKS.md에 "대기" 상태인 작업이 더 없어요. 새 Task를 추가해주세요!')
        return

    send_telegram(
        f"🚀 다음 작업 시작: <b>Task-{task['number']} | {task['title']}</b>\n"
        "Claude Code를 자동 실행할게요."
    )
    try:
        run_claude_code(build_new_task_prompt(task["number"], task["title"], task["file"]))
        send_telegram(
            f"✅ Task-{task['number']} 완료 — Claude Code가 PR까지 생성했어요. AI 리뷰를 기다려주세요."
        )
    except subprocess.CalledProcessError as e:
        send_telegram(
            f"🚨 Task-{task['number']} 자동 실행 오류:\n<code>{e}</code>\n직접 확인이 필요해요."
        )


def handle_retry(pr_number: str) -> None:
    send_telegram(
        f"🔄 <b>PR #{pr_number} 다시 생성</b>\n리뷰 피드백을 반영해서 재작업할게요..."
    )

    pr = get_pr(pr_number)
    branch = pr["head"]["ref"]
    task_number = extract_task_number_from_branch(branch)

    comments = get_pr_comments(pr_number)
    last_review = next(
        (c for c in reversed(comments) if "AI 코드 리뷰 결과" in c["body"]),
        None,
    )
    feedback = (
        last_review["body"]
        if last_review
        else "(리뷰 코멘트를 찾지 못했어요. PR을 직접 확인해주세요.)"
    )

    # 리뷰 피드백 로그 기록 (재작업 시작 전)
    if task_number:
        feedback_summary = feedback[:120].replace("\n", " ") + ("..." if len(feedback) > 120 else "")
        append_to_task_log(task_number, "🔄 리뷰 피드백 반영 시작", feedback_summary)

    try:
        subprocess.run(["git", "fetch", "origin", branch], check=True)
        subprocess.run(["git", "checkout", branch], check=True)
        subprocess.run(["git", "pull", "origin", branch], check=True)
    except subprocess.CalledProcessError as e:
        send_telegram(
            f"🚨 브랜치 {branch} 체크아웃 실패. 로컬 git 상태를 확인해주세요.\n<code>{e}</code>"
        )
        return

    try:
        run_claude_code(build_retry_prompt(branch, feedback))
        if task_number:
            append_to_task_log(task_number, "✅ 재작업 완료", f"PR #{pr_number} push — AI 리뷰 재실행 대기 중")
            _commit_logs(f"docs: Task-{task_number} 리뷰 피드백 반영 로그 추가")
        send_telegram(f"✅ PR #{pr_number} 재작업 완료 — push 했어요. AI 리뷰가 다시 돌아갈 거예요.")
    except subprocess.CalledProcessError as e:
        send_telegram(f"🚨 PR #{pr_number} 재작업 오류:\n<code>{e}</code>")


def handle_hold(pr_number: str) -> None:
    send_telegram(
        f"⏸️ <b>PR #{pr_number} 보류</b>\n준비되면 다시 버튼을 눌러주세요."
    )


# ── 메인 루프 ─────────────────────────────────────────────────────────────────

def process_callback(callback: dict, state: dict) -> None:
    callback_id = callback["id"]
    data = callback.get("data", "")

    # 등록된 chat ID 가 아니면 무시
    sender_id = str(callback.get("from", {}).get("id", ""))
    if sender_id != _CHAT_ID:
        print(f"[보안] 허가되지 않은 요청 무시 (from: {sender_id})")
        answer_callback(callback_id, "권한이 없어요.")
        return

    if not data.startswith("decision:"):
        return
    if callback_id in state["handled_callbacks"]:
        return  # 중복 처리 방지

    parts = data.split(":")
    if len(parts) != 3:
        return
    _, action, pr_number = parts

    answer_callback(callback_id, "처리 중이에요...")
    print(f"\n=== 콜백 수신: {action} / PR #{pr_number} ===")

    if action == "PASS":
        handle_pass(pr_number)
    elif action == "RETRY":
        handle_retry(pr_number)
    elif action == "HOLD":
        handle_hold(pr_number)
    else:
        print(f"알 수 없는 액션: {action}")

    state["handled_callbacks"].append(callback_id)
    state["handled_callbacks"] = state["handled_callbacks"][-200:]
    save_state(state)


def handle_start_command() -> None:
    task = find_next_pending_task()
    if not task:
        send_telegram('📋 대기 중인 작업이 없어요. tasks/ 폴더를 확인해주세요!')
        return

    send_telegram(
        f"🚀 작업 시작: <b>BT-{task['number']} | {task['title']}</b>\n"
        "Claude Code를 자동 실행할게요."
    )
    try:
        run_claude_code(build_new_task_prompt(task["number"], task["title"], task["file"]))
        send_telegram(
            f"✅ BT-{task['number']} 완료 — Claude Code가 PR까지 생성했어요. AI 리뷰를 기다려주세요."
        )
    except subprocess.CalledProcessError as e:
        send_telegram(f"🚨 BT-{task['number']} 실행 오류:\n<code>{e}</code>")


def handle_restart_command(args: str) -> None:
    """오류로 멈춘 AI 리뷰 워크플로우를 재시작한다. PR 번호 생략 시 가장 최근 열린 PR 사용."""
    pr_number = args.strip()

    if pr_number:
        try:
            pr = get_pr(pr_number)
        except requests.HTTPError as e:
            send_telegram(f"❌ PR #{pr_number}를 찾지 못했어요:\n<code>{e}</code>")
            return
    else:
        pr = get_latest_open_pr()
        if not pr:
            send_telegram("❌ 열린 PR이 없어요.")
            return
        pr_number = str(pr["number"])

    branch = pr["head"]["ref"]
    rerun_review_workflow(pr_number, branch)


def process_message(message: dict, state: dict) -> None:
    sender_id = str(message.get("from", {}).get("id", ""))
    if sender_id != _CHAT_ID:
        return

    text = message.get("text", "").strip()
    message_id = message["message_id"]

    if message_id in state.get("handled_messages", []):
        return

    state.setdefault("handled_messages", [])
    state["handled_messages"].append(message_id)
    state["handled_messages"] = state["handled_messages"][-200:]

    print(f"\n=== 메시지 수신: {text} ===")

    command, _, rest = text.partition(" ")
    if command in ("/시작", "/start"):
        handle_start_command()
    elif command in ("/다시시작", "/restart"):
        handle_restart_command(rest)
    else:
        send_telegram(
            "사용 가능한 명령어:\n"
            "/시작 — 다음 대기 태스크 시작\n"
            "/다시시작 [PR번호] — 오류로 멈춘 AI 리뷰 재시작 (번호 생략 시 최근 PR)"
        )


def poll_loop() -> None:
    state = load_state()
    print(f"🤖 차곡차곡 auto-pilot 시작 (폴링 주기 {POLL_INTERVAL}초)")
    print(f"레포: {REPO}")
    print("텔레그램에서 /시작 을 보내면 첫 태스크가 시작됩니다.")

    while True:
        try:
            updates = get_updates(state["offset"])
            for update in updates:
                state["offset"] = update["update_id"] + 1
                if "callback_query" in update:
                    process_callback(update["callback_query"], state)
                elif "message" in update:
                    process_message(update["message"], state)
            save_state(state)
        except Exception as e:
            print(f"[폴링 오류] {e}")

        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    poll_loop()

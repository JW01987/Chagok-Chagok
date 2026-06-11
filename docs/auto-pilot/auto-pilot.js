/**
 * scripts/auto-pilot.js
 *
 * 진이 컴퓨터에서 "계속 켜두는" 로컬 자동화 데몬.
 *
 * 하는 일:
 *  1. 텔레그램을 주기적으로 폴링하면서 "통과 / 다시 생성 / 보류" 버튼 클릭을 기다림
 *  2. 통과 → 해당 PR을 머지하고, docs/TASKS.md에서 "상태: 대기"인 다음 Task를 찾아
 *     Claude Code 프롬프트를 자동 생성해서 실행 (headless)
 *  3. 다시 생성 → 리뷰 미달 피드백을 담은 프롬프트로 같은 브랜치에서 Claude Code 재실행
 *  4. 보류 → 아무 것도 하지 않고 알림만 남김 (나중에 진이가 직접 처리)
 *
 * 실행:
 *   node scripts/auto-pilot.js
 *
 * 미리 설정해야 하는 것 (.env 또는 환경변수):
 *   TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, GITHUB_TOKEN, REPO (예: "jin/chagogchagog")
 *
 * 전제 조건:
 *   - 로컬에 git, gh(GitHub CLI), claude(Claude Code CLI) 설치 + 인증 완료
 *   - 이 스크립트를 레포 루트에서 실행 (TASKS.md, CONVENTIONS.md 상대경로 기준)
 */

require("dotenv").config();
const fs = require("fs");
const path = require("path");
const { spawn, execSync } = require("child_process");

const POLL_INTERVAL_MS = 15_000; // 15초마다 폴링
const STATE_FILE = path.join(__dirname, "..", ".auto-pilot-state.json");
const TASKS_FILE = path.join(__dirname, "..", "docs", "TASKS.md");
const CONVENTIONS_FILE = "docs/CONVENTIONS.md";

// =============================================
// 상태 관리 (텔레그램 offset, 처리 이력)
// =============================================
function loadState() {
  if (fs.existsSync(STATE_FILE)) {
    return JSON.parse(fs.readFileSync(STATE_FILE, "utf8"));
  }
  return { offset: 0, handledCallbacks: [] };
}

function saveState(state) {
  fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

// =============================================
// 텔레그램 헬퍼
// =============================================
const TG_BASE = `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}`;

async function getUpdates(offset) {
  const res = await fetch(`${TG_BASE}/getUpdates?offset=${offset}&timeout=10`);
  const data = await res.json();
  return data.result || [];
}

async function answerCallbackQuery(callbackQueryId, text) {
  await fetch(`${TG_BASE}/answerCallbackQuery`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ callback_query_id: callbackQueryId, text }),
  });
}

async function sendTelegram(message) {
  await fetch(`${TG_BASE}/sendMessage`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: process.env.TELEGRAM_CHAT_ID,
      text: message,
      parse_mode: "HTML",
    }),
  });
}

// =============================================
// GitHub 헬퍼
// =============================================
async function getPR(prNumber) {
  const res = await fetch(
    `https://api.github.com/repos/${process.env.REPO}/pulls/${prNumber}`,
    { headers: { Authorization: `token ${process.env.GITHUB_TOKEN}` } },
  );
  if (!res.ok)
    throw new Error(`PR #${prNumber} 조회 실패: ${await res.text()}`);
  return res.json();
}

function mergePR(prNumber) {
  console.log(`→ PR #${prNumber} 머지 중...`);
  execSync(`gh pr merge ${prNumber} --squash --delete-branch`, {
    stdio: "inherit",
  });
}

// =============================================
// TASKS.md 파싱 — "상태: 대기"인 가장 빠른 Task 찾기
// =============================================
function findNextPendingTask() {
  const content = fs.readFileSync(TASKS_FILE, "utf8");
  const blocks = content.split(/(?=^## Task-)/m);

  for (const block of blocks) {
    const titleMatch = block.match(/^## Task-(\d+)\s*\|\s*(.+)$/m);
    const statusMatch = block.match(/^- 상태:\s*(.+)$/m);
    if (titleMatch && statusMatch && statusMatch[1].trim() === "대기") {
      return { number: titleMatch[1], title: titleMatch[2].trim() };
    }
  }
  return null;
}

function slugify(title) {
  // 한글 제목을 영문 브랜치명으로 변환하기 어려우므로, 진이가 매번 직접 다듬을 필요 없게
  // 태스크 번호 + "auto" 접미사를 기본으로 사용. 필요하면 직접 브랜치명을 바꿔도 됨.
  return "auto";
}

// =============================================
// Claude Code 헤드리스 실행
// =============================================
function buildNewTaskPrompt(taskNumber, taskTitle) {
  const branch = `feature/task-${taskNumber}-${slugify(taskTitle)}`;
  return `
${CONVENTIONS_FILE} 와 docs/TASKS.md 를 읽어줘.

Task-${taskNumber} 을 구현해줘.

작업 순서:
1. CONVENTIONS.md 규칙 반드시 따를 것
2. TASKS.md 에서 Task-${taskNumber} 요구사항 파악
3. 코드 작성 (Controller / Service / Repository / DTO / Exception 등 필요한 레이어)
4. 테스트 코드 작성 (JUnit5 + Mockito, 커버리지 80% 이상)
5. git checkout -b ${branch}
6. git add .
7. git commit -m "feat: Task-${taskNumber} ${taskTitle} 구현"
8. git push origin ${branch}
9. gh pr create --title "[Task-${taskNumber}] ${taskTitle}" --base main --body "Task-${taskNumber} 구현 완료 / 테스트 포함"

PR 생성 완료되면 PR 번호 알려줘.
`.trim();
}

function buildRetryPrompt(branch, reviewComments) {
  return `
현재 브랜치(${branch})에서 작업 중인 코드에 대해 AI 리뷰에서 개선 요청이 왔어.
docs/CONVENTIONS.md 규칙을 지키면서 아래 피드백을 반영해서 코드를 수정해줘.

[리뷰 피드백]
${reviewComments}

작업 순서:
1. 위 피드백을 반영해서 코드 수정 (필요하면 테스트 코드도 보완)
2. git add .
3. git commit -m "fix: AI 리뷰 피드백 반영"
4. git push

작업 완료되면 알려줘.
`.trim();
}

/**
 * Claude Code CLI를 헤드리스(non-interactive) 모드로 실행한다.
 * ⚠️ 사용 중인 Claude Code 버전에 따라 플래그가 다를 수 있으니,
 *    `claude --help` 로 헤드리스 실행 옵션(예: -p, --print)을 확인 후 필요시 수정할 것.
 */
function runClaudeCode(prompt) {
  return new Promise((resolve, reject) => {
    console.log("→ Claude Code 실행 중...\n");
    const child = spawn("claude", ["-p", prompt], {
      stdio: "inherit",
      shell: true,
    });

    child.on("close", (code) => {
      if (code === 0) resolve();
      else reject(new Error(`Claude Code 종료 코드 ${code}`));
    });
  });
}

// =============================================
// 액션 핸들러
// =============================================
async function handlePass(prNumber) {
  await sendTelegram(
    `✅ <b>PR #${prNumber} 통과 처리</b>\n머지를 진행할게요...`,
  );

  const pr = await getPR(prNumber);
  mergePR(prNumber);

  await sendTelegram(
    `🎉 PR #${prNumber} 머지 완료! (배포는 deploy.yml 이 자동 진행)`,
  );

  const next = findNextPendingTask();
  if (!next) {
    await sendTelegram(
      `📋 TASKS.md에 "대기" 상태인 작업이 더 없어요. 새 Task를 추가해주세요!`,
    );
    return;
  }

  await sendTelegram(
    `🚀 다음 작업 시작: <b>Task-${next.number} | ${next.title}</b>\nClaude Code를 자동 실행할게요.`,
  );

  const prompt = buildNewTaskPrompt(next.number, next.title);

  try {
    await runClaudeCode(prompt);
    await sendTelegram(
      `✅ Task-${next.number} 작업 완료 — Claude Code가 PR까지 생성했어요. AI 리뷰를 기다려주세요.`,
    );
  } catch (err) {
    await sendTelegram(
      `🚨 Task-${next.number} 자동 실행 중 오류 발생:\n<code>${err.message}</code>\n진이 확인이 필요해요.`,
    );
  }
}

async function handleRetry(prNumber) {
  await sendTelegram(
    `🔄 <b>PR #${prNumber} 다시 생성 처리</b>\n리뷰 피드백을 반영해서 재작업할게요...`,
  );

  const pr = await getPR(prNumber);
  const branch = pr.head.ref;

  // 최근 PR 코멘트에서 리뷰 피드백 가져오기
  const commentsRes = await fetch(
    `https://api.github.com/repos/${process.env.REPO}/issues/${prNumber}/comments`,
    { headers: { Authorization: `token ${process.env.GITHUB_TOKEN}` } },
  );
  const comments = await commentsRes.json();
  const lastReview = [...comments]
    .reverse()
    .find((c) => c.body.includes("AI 코드 리뷰 결과"));
  const feedback = lastReview
    ? lastReview.body
    : "(리뷰 코멘트를 찾지 못했어요. PR을 직접 확인해주세요.)";

  // 로컬 작업 디렉토리를 PR 브랜치로 전환
  try {
    execSync(`git fetch origin ${branch}`, { stdio: "inherit" });
    execSync(`git checkout ${branch}`, { stdio: "inherit" });
    execSync(`git pull origin ${branch}`, { stdio: "inherit" });
  } catch (err) {
    await sendTelegram(
      `🚨 브랜치 ${branch} 체크아웃 실패. 로컬 git 상태를 확인해주세요.\n<code>${err.message}</code>`,
    );
    return;
  }

  const prompt = buildRetryPrompt(branch, feedback);

  try {
    await runClaudeCode(prompt);
    await sendTelegram(
      `✅ PR #${prNumber} 재작업 완료 — push 했어요. AI 리뷰가 다시 돌아갈 거예요.`,
    );
  } catch (err) {
    await sendTelegram(
      `🚨 PR #${prNumber} 재작업 중 오류 발생:\n<code>${err.message}</code>`,
    );
  }
}

async function handleHold(prNumber) {
  await sendTelegram(
    `⏸️ <b>PR #${prNumber} 보류</b>\n아무 작업도 하지 않을게요. 준비되면 다시 버튼을 눌러주세요.`,
  );
}

// =============================================
// 메인 루프
// =============================================
async function processCallback(callbackQuery, state) {
  const { id: callbackQueryId, data } = callbackQuery;
  if (!data || !data.startsWith("decision:")) return;

  if (state.handledCallbacks.includes(callbackQueryId)) return; // 중복 처리 방지

  const [, action, prNumber] = data.split(":");
  await answerCallbackQuery(callbackQueryId, "처리 중이에요...");

  console.log(`\n=== 콜백 수신: ${action} / PR #${prNumber} ===`);

  switch (action) {
    case "PASS":
      await handlePass(prNumber);
      break;
    case "RETRY":
      await handleRetry(prNumber);
      break;
    case "HOLD":
      await handleHold(prNumber);
      break;
    default:
      console.log("알 수 없는 액션:", action);
  }

  state.handledCallbacks.push(callbackQueryId);
  // 이력이 너무 커지지 않도록 최근 200개만 유지
  state.handledCallbacks = state.handledCallbacks.slice(-200);
  saveState(state);
}

async function pollLoop() {
  const state = loadState();
  console.log(
    `🤖 차곡차곡 auto-pilot 시작 (폴링 주기 ${POLL_INTERVAL_MS / 1000}초)`,
  );
  console.log(`레포: ${process.env.REPO}`);

  while (true) {
    try {
      const updates = await getUpdates(state.offset);
      for (const update of updates) {
        state.offset = update.update_id + 1;
        if (update.callback_query) {
          await processCallback(update.callback_query, state);
        }
      }
      saveState(state);
    } catch (err) {
      console.error("폴링 중 오류:", err.message);
    }

    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
  }
}

pollLoop();

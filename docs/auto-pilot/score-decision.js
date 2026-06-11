/**
 * scripts/score-decision.js
 * (GitHub Actions에서 실행)
 *
 * Gemini 리뷰 점수를 계산해서 PR에 코멘트로 남기고,
 * 텔레그램으로 "통과 / 다시 생성 / 보류" 버튼이 달린 메시지를 보낸다.
 *
 * ⚠️ 이 스크립트는 더 이상 자동 머지를 하지 않는다.
 *    최종 결정은 진이가 텔레그램 버튼으로 직접 내리고,
 *    그 결정은 로컬에서 상시 실행되는 scripts/auto-pilot.js 가 받아서 처리한다.
 *
 * 점수 기준은 여기서 직접 수정하면 됩니다 ↓
 */

const fs = require("fs");

// =============================================
// ✏️ 진이가 직접 조정하는 통과 기준 (참고용 표시 — 최종 판단은 진이가 함)
// =============================================
const CRITERIA = {
  readability: { label: "가독성", pass: 80 },
  logic: { label: "로직", pass: 90 },
  exception_handling: { label: "예외처리", pass: 80 },
  test_code: { label: "테스트", pass: 85 },
  security: { label: "보안", pass: 90 },
};
// =============================================

async function postGithubComment(body) {
  const res = await fetch(
    `https://api.github.com/repos/${process.env.REPO}/issues/${process.env.PR_NUMBER}/comments`,
    {
      method: "POST",
      headers: {
        Authorization: `token ${process.env.GITHUB_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ body }),
    },
  );
  if (!res.ok) console.error("GitHub 코멘트 실패:", await res.text());
}

/**
 * 텔레그램으로 점수/요약 + 인라인 버튼 3개를 전송한다.
 *
 * callback_data 포맷: "decision:{ACTION}:{PR_NUMBER}"
 *   - ACTION: PASS | RETRY | HOLD
 * auto-pilot.js 가 이 포맷을 파싱해서 동작을 결정한다.
 */
async function sendTelegramWithButtons({
  scoreLines,
  summary,
  prNumber,
  branch,
  allPassed,
}) {
  const statusLine = allPassed
    ? "✅ 모든 항목이 기준을 통과했어요."
    : "⚠️ 일부 항목이 기준에 못 미쳤어요. 그래도 최종 판단은 진이 몫!";

  const message =
    `📊 <b>PR #${prNumber} 리뷰 결과</b>\n` +
    `브랜치: <code>${branch}</code>\n\n` +
    scoreLines +
    `\n${statusLine}\n\n` +
    `📝 ${summary}\n\n` +
    `아래 버튼으로 다음 작업을 결정해주세요 👇`;

  const res = await fetch(
    `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: process.env.TELEGRAM_CHAT_ID,
        text: message,
        parse_mode: "HTML",
        reply_markup: {
          inline_keyboard: [
            [
              {
                text: "✅ 통과 (머지 + 다음 Task)",
                callback_data: `decision:PASS:${prNumber}`,
              },
              {
                text: "🔄 다시 생성",
                callback_data: `decision:RETRY:${prNumber}`,
              },
            ],
            [{ text: "⏸️ 보류", callback_data: `decision:HOLD:${prNumber}` }],
          ],
        },
      }),
    },
  );

  if (!res.ok) console.error("텔레그램 전송 실패:", await res.text());
}

async function main() {
  const result = JSON.parse(fs.readFileSync("review-result.json", "utf8"));
  const { scores, comments, summary } = result;
  const prNumber = process.env.PR_NUMBER;
  const branch = process.env.PR_BRANCH || "(알 수 없음)";

  // ── 점수 라인 구성 (참고용 — 통과 기준 대비 표시만, 최종 판단 X) ──
  let allPassed = true;
  let scoreLines = "";

  for (const [key, { label, pass }] of Object.entries(CRITERIA)) {
    const score = scores[key];
    const passed = score >= pass;
    if (!passed) allPassed = false;
    const emoji = passed ? "✅" : "❌";
    scoreLines += `${emoji} <b>${label}</b>: ${score}점 (기준 ${pass}점)\n`;
  }

  // ── PR 코멘트 (상세 피드백 기록용) ──
  const commentBody =
    `## 📊 AI 코드 리뷰 결과\n\n` +
    Object.entries(CRITERIA)
      .map(([key, { label, pass }]) => {
        const score = scores[key];
        const emoji = score >= pass ? "✅" : "❌";
        return `### ${emoji} ${label}: ${score}점 (기준 ${pass}점)\n> ${comments[key]}`;
      })
      .join("\n\n") +
    `\n\n---\n📝 **요약**: ${summary}\n\n` +
    `> 최종 진행 여부는 텔레그램에서 진이가 직접 결정합니다 (통과 / 다시 생성 / 보류).`;

  await postGithubComment(commentBody);

  // ── 텔레그램 알림 (버튼 포함) ──
  await sendTelegramWithButtons({
    scoreLines,
    summary,
    prNumber,
    branch,
    allPassed,
  });

  // 다음 단계(auto-pilot.js)에서 참고할 수 있도록 PR 정보 파일로 저장 + 아티팩트 업로드는 워크플로에서 처리
  fs.writeFileSync(
    "pr-context.json",
    JSON.stringify({ prNumber, branch, repo: process.env.REPO }, null, 2),
  );

  console.log(
    `PR #${prNumber} 리뷰 결과를 텔레그램으로 전송했습니다. 진이의 결정을 기다립니다.`,
  );
}

main().catch((err) => {
  console.error("score-decision 실패:", err);
  process.exit(1);
});

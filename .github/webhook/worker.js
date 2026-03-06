/**
 * Cloudflare Worker: Notion Webhook → GitHub repository_dispatch
 *
 * Notion 자동화에서 webhook을 받으면 GitHub Actions를 즉시 트리거한다.
 *
 * 환경변수 (Cloudflare Worker Settings > Variables):
 *   GITHUB_TOKEN: GitHub Fine-grained PAT (Actions: write 권한)
 *
 * 배포 후 URL 예시: https://notion-webhook.{your-subdomain}.workers.dev
 */
export default {
  async fetch(request, env) {
    // POST만 허용
    if (request.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // GitHub repository_dispatch 호출
    const response = await fetch(
      "https://api.github.com/repos/Grensil/marathon/dispatches",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.GITHUB_TOKEN}`,
          Accept: "application/vnd.github.v3+json",
          "User-Agent": "notion-webhook-bridge",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          event_type: "notion-sync",
        }),
      }
    );

    if (response.ok || response.status === 204) {
      return new Response(JSON.stringify({ ok: true }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(
      JSON.stringify({ ok: false, status: response.status }),
      { status: 502, headers: { "Content-Type": "application/json" } }
    );
  },
};

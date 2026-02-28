#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# GitHub Issue 닫힘 → Notion 상태 "종료" 자동 변경 스크립트
#
# GitHub Issue가 닫히면 해당 Issue URL을 가진 Notion 페이지를 찾아
# 상태를 "종료"로 업데이트한다.
# ============================================================

NOTION_DB_ID="${NOTION_DB_ID:?NOTION_DB_ID is not set}"
NOTION_API_VERSION="2022-06-28"
ISSUE_URL="${ISSUE_URL:?ISSUE_URL is not set}"

if [[ -z "${NOTION_API_KEY:-}" ]]; then
  echo "::error::NOTION_API_KEY is not set"
  exit 1
fi

echo "=== Closing Notion issue for: ${ISSUE_URL} ==="

# ------------------------------------------------------------------
# Notion DB 쿼리: GitHub Issue URL이 일치하는 페이지 찾기
# ------------------------------------------------------------------
TMPDIR_SYNC=$(mktemp -d)
RESPONSE_FILE="${TMPDIR_SYNC}/notion_response.json"
trap 'rm -rf "$TMPDIR_SYNC"' EXIT

FILTER_BODY=$(jq -n --arg url "$ISSUE_URL" '{
  filter: {
    property: "GitHub Issue URL",
    url: { equals: $url }
  }
}')

curl -sS -X POST \
  "https://api.notion.com/v1/databases/${NOTION_DB_ID}/query" \
  -H "Authorization: Bearer ${NOTION_API_KEY}" \
  -H "Notion-Version: ${NOTION_API_VERSION}" \
  -H "Content-Type: application/json" \
  -d "$FILTER_BODY" \
  -o "$RESPONSE_FILE"

if jq -e '.object == "error"' "$RESPONSE_FILE" > /dev/null 2>&1; then
  MSG=$(jq -r '.message' "$RESPONSE_FILE")
  echo "::error::Notion API error: $MSG"
  exit 1
fi

PAGE_COUNT=$(jq '.results | length' "$RESPONSE_FILE")

if [[ "$PAGE_COUNT" -eq 0 ]]; then
  echo "  No Notion page found for ${ISSUE_URL}"
  exit 0
fi

# ------------------------------------------------------------------
# 각 페이지 상태를 "종료"로 변경
# ------------------------------------------------------------------
for (( i=0; i<PAGE_COUNT; i++ )); do
  PAGE_ID=$(jq -r ".results[$i].id" "$RESPONSE_FILE")
  TITLE=$(jq -r "[.results[$i].properties.\"제목\".title[]?.plain_text] | join(\"\")" "$RESPONSE_FILE")
  CURRENT_STATUS=$(jq -r ".results[$i].properties.\"상태\".status.name // \"없음\"" "$RESPONSE_FILE")

  echo "  Page: ${TITLE}"
  echo "  Status: ${CURRENT_STATUS} → 종료"

  if [[ "$CURRENT_STATUS" == "종료" ]]; then
    echo "  Already closed, skipping."
    continue
  fi

  UPDATE_BODY='{"properties":{"상태":{"status":{"name":"종료"}}}}'

  if ! curl -sS -f -X PATCH \
    "https://api.notion.com/v1/pages/${PAGE_ID}" \
    -H "Authorization: Bearer ${NOTION_API_KEY}" \
    -H "Notion-Version: ${NOTION_API_VERSION}" \
    -H "Content-Type: application/json" \
    -d "$UPDATE_BODY" > /dev/null; then
    echo "  Failed to update Notion page"
    exit 1
  fi

  echo "  Notion status updated to 종료"
done

echo ""
echo "=== Done ==="

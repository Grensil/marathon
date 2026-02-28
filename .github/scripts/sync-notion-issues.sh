#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Notion QA -> GitHub Issue Sync Script
#
# Queries the Notion QA issue tracker for issues in "접수" status
# that don't have a GitHub Issue URL yet, creates GitHub Issues,
# and writes the URL back to Notion.
# ============================================================

NOTION_DB_ID="${NOTION_DB_ID:?NOTION_DB_ID is not set}"
NOTION_API_VERSION="2022-06-28"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

if [[ -z "${NOTION_API_KEY:-}" ]]; then
  echo "::error::NOTION_API_KEY is not set"
  exit 1
fi
if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "::error::GH_TOKEN is not set"
  exit 1
fi

# ------------------------------------------------------------------
# Helper: create a GitHub label if it doesn't already exist
# ------------------------------------------------------------------
ensure_label() {
  local name="$1"
  local color="${2:-ededed}"
  local description="${3:-}"
  gh label create "$name" --repo "$REPO" \
    --color "$color" --description "$description" 2>/dev/null || true
}

echo "=== Ensuring GitHub labels exist ==="
# Category
ensure_label "QA"       "0075ca" "QA issue"
ensure_label "bug"      "d73a4a" "Bug"
ensure_label "task"     "0e8a16" "Task"
# Priority
ensure_label "P0"       "e11d48" "Critical"
ensure_label "P1"       "eb6420" "High"
ensure_label "P2"       "fbca04" "Medium"
ensure_label "P3"       "cccccc" "Low"
# Environment
ensure_label "android"  "a4c639" "Android"
ensure_label "ios"      "147efb" "iOS"
ensure_label "web"      "ff6600" "Web"
ensure_label "backend"  "6f42c1" "Backend"
ensure_label "infra"    "666666" "Infra"

# ------------------------------------------------------------------
# Query Notion DB: status = "접수" AND GitHub Issue URL is empty
# ------------------------------------------------------------------
echo ""
echo "=== Querying Notion DB for new issues ==="

TMPDIR_SYNC=$(mktemp -d)
RESPONSE_FILE="${TMPDIR_SYNC}/notion_response.json"
trap 'rm -rf "$TMPDIR_SYNC"' EXIT

curl -sS -X POST \
  "https://api.notion.com/v1/databases/${NOTION_DB_ID}/query" \
  -H "Authorization: Bearer ${NOTION_API_KEY}" \
  -H "Notion-Version: ${NOTION_API_VERSION}" \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "and": [
        { "property": "상태",            "status": { "equals": "접수" } },
        { "property": "GitHub Issue URL", "url":    { "is_empty": true } }
      ]
    }
  }' -o "$RESPONSE_FILE"

# Check for API errors
if jq -e '.object == "error"' "$RESPONSE_FILE" > /dev/null 2>&1; then
  MSG=$(jq -r '.message' "$RESPONSE_FILE")
  echo "::error::Notion API error: $MSG"
  exit 1
fi

RESULTS_COUNT=$(jq '.results | length' "$RESPONSE_FILE")

if [[ "$RESULTS_COUNT" -eq 0 ]]; then
  echo "  No new issues to sync."
  exit 0
fi

echo "  Found ${RESULTS_COUNT} new issue(s) to sync."

# ------------------------------------------------------------------
# Label mapping helpers
# ------------------------------------------------------------------
map_category_label() {
  case "$1" in
    QA)   echo "QA"   ;;
    Bug)  echo "bug"  ;;
    Task) echo "task" ;;
    *)    echo ""      ;;
  esac
}

map_env_label() {
  case "$1" in
    Android) echo "android" ;;
    iOS)     echo "ios"     ;;
    Web)     echo "web"     ;;
    Backend) echo "backend" ;;
    Infra)   echo "infra"   ;;
    *)       echo ""        ;;
  esac
}

# ------------------------------------------------------------------
# Process each issue
# ------------------------------------------------------------------
SYNCED=0
FAILED=0

# Extract each page into individual temp files to avoid control-char issues
PAGE_COUNT=$(jq '.results | length' "$RESPONSE_FILE")

for (( i=0; i<PAGE_COUNT; i++ )); do
  PAGE_FILE="${TMPDIR_SYNC}/page_${i}.json"
  jq ".results[$i]" "$RESPONSE_FILE" > "$PAGE_FILE"

  PAGE_ID=$(jq -r '.id' "$PAGE_FILE")
  PAGE_URL=$(jq -r '.url' "$PAGE_FILE")

  # --- Extract properties ---
  TITLE=$(jq -r '[.properties."제목".title[]?.plain_text] | join("")' "$PAGE_FILE")
  CATEGORY=$(jq -r '.properties."구분".select.name // empty' "$PAGE_FILE")
  PRIORITY=$(jq -r '.properties."우선순위".select.name // empty' "$PAGE_FILE")
  ENVIRONMENT=$(jq -r '.properties."발생 환경".select.name // empty' "$PAGE_FILE")
  REPRO_STEPS=$(jq -r '[.properties."재현 단계".rich_text[]?.plain_text] | join("\n")' "$PAGE_FILE")
  EXPECTED=$(jq -r '[.properties."기대 동작".rich_text[]?.plain_text] | join("\n")' "$PAGE_FILE")
  ACTUAL=$(jq -r '[.properties."실제 동작".rich_text[]?.plain_text] | join("\n")' "$PAGE_FILE")

  if [[ -z "$TITLE" ]]; then
    echo "  Skipping page ${PAGE_ID}: empty title"
    continue
  fi

  echo ""
  echo "--- Processing: ${TITLE} ---"

  # --- Build issue title: [Category][Priority] Title ---
  ISSUE_TITLE=""
  [[ -n "$CATEGORY" ]] && ISSUE_TITLE+="[${CATEGORY}]"
  [[ -n "$PRIORITY" ]] && ISSUE_TITLE+="[${PRIORITY}]"
  ISSUE_TITLE+=" ${TITLE}"

  # --- Collect labels ---
  LABELS=()
  CAT_LABEL=$(map_category_label "${CATEGORY:-}")
  [[ -n "$CAT_LABEL" ]] && LABELS+=("$CAT_LABEL")
  [[ -n "$PRIORITY" ]]  && LABELS+=("$PRIORITY")
  ENV_LABEL=$(map_env_label "${ENVIRONMENT:-}")
  [[ -n "$ENV_LABEL" ]] && LABELS+=("$ENV_LABEL")

  # --- Build issue body ---
  BODY_FILE=$(mktemp)
  {
    echo "## 재현 단계"
    if [[ -n "$REPRO_STEPS" ]]; then echo "$REPRO_STEPS"; else echo "_정보 없음_"; fi
    echo ""
    echo "## 기대 동작"
    if [[ -n "$EXPECTED" ]]; then echo "$EXPECTED"; else echo "_정보 없음_"; fi
    echo ""
    echo "## 실제 동작"
    if [[ -n "$ACTUAL" ]]; then echo "$ACTUAL"; else echo "_정보 없음_"; fi
    echo ""
    echo "---"
    echo "> **Notion**: ${PAGE_URL}"
  } > "$BODY_FILE"

  # --- Create GitHub Issue ---
  LABEL_ARGS=()
  for l in "${LABELS[@]}"; do
    LABEL_ARGS+=(--label "$l")
  done

  if ! ISSUE_URL=$(gh issue create \
    --repo "$REPO" \
    --title "$ISSUE_TITLE" \
    --body-file "$BODY_FILE" \
    "${LABEL_ARGS[@]}" 2>&1); then
    echo "  Failed to create GitHub issue: $ISSUE_URL"
    rm -f "$BODY_FILE"
    FAILED=$((FAILED + 1))
    continue
  fi

  rm -f "$BODY_FILE"
  echo "  Created: ${ISSUE_URL}"

  # --- Update Notion page with GitHub Issue URL ---
  UPDATE_BODY=$(jq -n --arg url "$ISSUE_URL" '{
    properties: { "GitHub Issue URL": { url: $url } }
  }')

  if ! curl -sS -f -X PATCH \
    "https://api.notion.com/v1/pages/${PAGE_ID}" \
    -H "Authorization: Bearer ${NOTION_API_KEY}" \
    -H "Notion-Version: ${NOTION_API_VERSION}" \
    -H "Content-Type: application/json" \
    -d "$UPDATE_BODY" > /dev/null; then
    echo "  Failed to update Notion page with GitHub URL"
    FAILED=$((FAILED + 1))
    continue
  fi

  echo "  Updated Notion page with GitHub URL"
  SYNCED=$((SYNCED + 1))

done

echo ""
echo "=== Sync complete: ${SYNCED} synced, ${FAILED} failed ==="

if [[ "$FAILED" -gt 0 ]]; then
  exit 1
fi

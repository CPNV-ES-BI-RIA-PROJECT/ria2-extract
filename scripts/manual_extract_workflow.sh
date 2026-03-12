#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  manual_extract_workflow.sh <source_url> <destination_remote> [expiration_time] [api_base_url]

Arguments:
  source_url          Public or pre-signed URL of the source file
  destination_remote  Bucket destination in the form bucket/key
  expiration_time     Share link expiration in seconds (default: 3600)
  api_base_url        Base URL of the API (default: http://localhost:8080/api)

Example:
  ./scripts/manual_extract_workflow.sh \
    "https://public.example.com/calendar.ics?signature=abc" \
    "my-bucket/raw/job-123/calendar.ics" \
    3600 \
    "http://localhost:8080/api"
EOF
}

if [[ $# -lt 2 || $# -gt 4 ]]; then
    usage
    exit 1
fi

SOURCE_URL="$1"
DESTINATION_REMOTE="$2"
EXPIRATION_TIME="${3:-3600}"
API_BASE_URL="${4:-http://localhost:8080/api}"

TEMP_FILE="$(mktemp /tmp/extract-workflow.XXXXXX)"

cleanup() {
    rm -f "$TEMP_FILE"
}

trap cleanup EXIT

echo "1. Downloading source file through the Extract API..."
curl --fail --silent --show-error \
    --get \
    --data-urlencode "remote=${SOURCE_URL}" \
    "${API_BASE_URL}/objects/download" \
    --output "$TEMP_FILE"

FILE_SIZE="$(wc -c < "$TEMP_FILE" | tr -d ' ')"
echo "   Downloaded ${FILE_SIZE} bytes"

echo "2. Uploading file to bucket destination ${DESTINATION_REMOTE}..."
curl --fail --silent --show-error \
    -X POST \
    -F "remote=${DESTINATION_REMOTE}" \
    -F "file=@${TEMP_FILE}" \
    "${API_BASE_URL}/objects"

echo "   Upload completed"

echo "3. Requesting a shared URL..."
SHARED_URL="$(
    curl --fail --silent --show-error \
        -X POST \
        --get \
        --data-urlencode "remote=${DESTINATION_REMOTE}" \
        --data-urlencode "expirationTime=${EXPIRATION_TIME}" \
        "${API_BASE_URL}/objects/share"
)"

echo
echo "Workflow completed"
echo "Shared URL:"
echo "${SHARED_URL}"

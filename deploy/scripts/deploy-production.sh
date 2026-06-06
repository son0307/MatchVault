#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${1:-/opt/match-vault}"
RELEASE_ID="${2:-manual-$(date +%Y%m%d%H%M%S)}"
SERVICE_NAME="match-vault"
INCOMING_ARCHIVE="$DEPLOY_PATH/incoming/$RELEASE_ID.tar.gz"
RELEASE_DIR="$DEPLOY_PATH/releases/$RELEASE_ID"
CURRENT_LINK="$DEPLOY_PATH/current"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTH_INITIAL_DELAY_SECONDS="${HEALTH_INITIAL_DELAY_SECONDS:-10}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-600}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"

if [ ! -f "$INCOMING_ARCHIVE" ]; then
  echo "Release archive not found: $INCOMING_ARCHIVE" >&2
  exit 1
fi

mkdir -p "$DEPLOY_PATH/releases"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

tar -xzf "$INCOMING_ARCHIVE" -C "$RELEASE_DIR"

if [ ! -f "$RELEASE_DIR/backend/app.jar" ]; then
  echo "Backend jar is missing from release bundle." >&2
  exit 1
fi

if [ ! -f "$RELEASE_DIR/frontend/index.html" ]; then
  echo "Frontend index.html is missing from release bundle." >&2
  exit 1
fi

if [ -L "$CURRENT_LINK" ]; then
  rm "$CURRENT_LINK"
elif [ -e "$CURRENT_LINK" ]; then
  LEGACY_CURRENT_BACKUP="$DEPLOY_PATH/current.backup-before-symlink-$(date +%Y%m%d%H%M%S)"
  echo "Existing current path is not a symlink. Moving it to $LEGACY_CURRENT_BACKUP"
  mv "$CURRENT_LINK" "$LEGACY_CURRENT_BACKUP"
fi

ln -s "$RELEASE_DIR" "$CURRENT_LINK"

sudo systemctl daemon-reload
sudo systemctl restart "$SERVICE_NAME"

echo "Service restart requested. Waiting ${HEALTH_INITIAL_DELAY_SECONDS}s before health checks."
sleep "$HEALTH_INITIAL_DELAY_SECONDS"

HEALTH_DEADLINE=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
attempt=1

while [ "$SECONDS" -lt "$HEALTH_DEADLINE" ]; do
  service_state="$(sudo systemctl is-active "$SERVICE_NAME" || true)"
  if [ "$service_state" = "failed" ] || [ "$service_state" = "inactive" ]; then
    echo "Service is $service_state before becoming healthy." >&2
    sudo journalctl -u "$SERVICE_NAME" -n 200 --no-pager >&2
    exit 1
  fi

  http_status="$(curl --silent --show-error --output /tmp/match-vault-health-response --write-out '%{http_code}' "$HEALTH_URL" || true)"
  if [ "$http_status" = "200" ]; then
    echo "Deployment succeeded: $RELEASE_ID"
    exit 0
  fi

  remaining=$((HEALTH_DEADLINE - SECONDS))
  echo "Waiting for health check... attempt=$attempt status=$http_status service=$service_state remaining=${remaining}s"
  attempt=$((attempt + 1))
  sleep "$HEALTH_INTERVAL_SECONDS"
done

echo "Health check failed after ${HEALTH_TIMEOUT_SECONDS}s. Last service logs:" >&2
sudo journalctl -u "$SERVICE_NAME" -n 200 --no-pager >&2
exit 1

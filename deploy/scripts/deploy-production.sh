#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: deploy-production.sh DEPLOY_PATH RELEASE_ID" >&2
  exit 2
fi

DEPLOY_PATH="$1"
RELEASE_ID="$2"
ACTIVE_BACKEND_FILE="/etc/nginx/match-vault-active-backend.conf"
NGINX_SITE="/etc/nginx/sites-available/match-vault.conf"
INCOMING_ARCHIVE="$DEPLOY_PATH/incoming/$RELEASE_ID.tar.gz"
RELEASE_DIR="$DEPLOY_PATH/releases/$RELEASE_ID"
CURRENT_LINK="$DEPLOY_PATH/current"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-600}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"

if [[ "$DEPLOY_PATH" != /* || "$DEPLOY_PATH" == / || ! "$RELEASE_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Invalid deployment path or release ID." >&2
  exit 2
fi
if [[ ! "$HEALTH_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ || ! "$HEALTH_INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "Health check intervals must be positive integers." >&2
  exit 2
fi
if [ ! -f "$INCOMING_ARCHIVE" ] || [ ! -f "$ACTIVE_BACKEND_FILE" ] || [ ! -L "$CURRENT_LINK" ]; then
  echo "Release archive, active backend file, or current symlink is missing." >&2
  exit 1
fi
if [ ! -f "$NGINX_SITE" ] || [ "$(grep -Fc 'proxy_pass http://match_vault_backend;' "$NGINX_SITE")" -ne 2 ] ||
   ! grep -Fq 'include /etc/nginx/match-vault-active-backend.conf;' "$NGINX_SITE"; then
  echo "Nginx site is not connected to the active backend include." >&2
  exit 1
fi

mkdir -p "$DEPLOY_PATH/releases"
exec 9>"$DEPLOY_PATH/deploy.lock"
flock -n 9 || { echo "Another deployment is running." >&2; exit 1; }

case "$(cat "$ACTIVE_BACKEND_FILE")" in
  "server 127.0.0.1:8080;")
    target_color="blue"
    target_port=8082
    old_service="match-vault"
    ;;
  "server 127.0.0.1:8082;")
    target_color="green"
    target_port=8083
    old_service="match-vault-blue"
    ;;
  "server 127.0.0.1:8083;")
    target_color="blue"
    target_port=8082
    old_service="match-vault-green"
    ;;
  *)
    echo "Unknown active backend. Refusing to deploy." >&2
    exit 1
    ;;
esac

target_service="match-vault-$target_color"
slot_link="$DEPLOY_PATH/$target_color"
if [ "$(systemctl show "$target_service" -p LoadState --value)" != "loaded" ]; then
  echo "Target systemd service is not installed: $target_service" >&2
  exit 1
fi
if [ "$(systemctl is-active "$target_service" || true)" = "active" ]; then
  echo "Inactive slot is already running: $target_service" >&2
  exit 1
fi
if [ -e "$slot_link" ] && [ ! -L "$slot_link" ]; then
  echo "Slot path is not a symlink: $slot_link" >&2
  exit 1
fi

old_slot=""
if [ -L "$slot_link" ]; then old_slot="$(readlink "$slot_link")"; fi
old_current="$(readlink "$CURRENT_LINK")"
old_backend_file=""
candidate_backend_file=""
staging_dir=""
slot_changed=false
target_started=false
backend_changed=false
current_changed=false
target_enabled=false
committed=false

switch_link() {
  local link="$1"
  local destination="$2"
  local temporary="$link.next.$$"
  ln -s "$destination" "$temporary"
  mv -Tf "$temporary" "$link"
}

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if [ "$status" -ne 0 ] && [ "$committed" = false ]; then
    echo "Deployment failed before completion. Restoring the previous route." >&2
    if [ "$current_changed" = true ]; then
      switch_link "$CURRENT_LINK" "$old_current" || echo "Could not restore frontend link." >&2
    fi
    if [ "$backend_changed" = true ]; then
      sudo -n install -m 644 "$old_backend_file" "$ACTIVE_BACKEND_FILE" || echo "Could not restore backend file." >&2
      sudo -n nginx -t && sudo -n systemctl reload nginx || echo "Could not reload the previous Nginx route." >&2
    fi
    if [ "$target_enabled" = true ]; then sudo -n systemctl disable "$target_service" || true; fi
    if [ "$target_started" = true ]; then
      sudo -n systemctl stop "$target_service" || echo "Could not stop the candidate service." >&2
    fi
    if [ "$slot_changed" = true ]; then
      if [ -n "$old_slot" ]; then
        switch_link "$slot_link" "$old_slot" || echo "Could not restore slot link." >&2
      elif [ -L "$slot_link" ] && [ "$(readlink "$slot_link")" = "$RELEASE_DIR" ]; then
        rm -- "$slot_link"
      fi
    fi
  fi
  rm -f -- "$slot_link.next.$$" "$CURRENT_LINK.next.$$"
  if [ -n "$old_backend_file" ]; then rm -f -- "$old_backend_file"; fi
  if [ -n "$candidate_backend_file" ]; then rm -f -- "$candidate_backend_file"; fi
  if [ -n "$staging_dir" ] && [ -d "$staging_dir" ]; then rm -r -- "$staging_dir"; fi
  exit "$status"
}
trap cleanup EXIT

if [ -e "$RELEASE_DIR" ]; then
  if [ ! -d "$RELEASE_DIR" ] || [ -L "$RELEASE_DIR" ]; then
    echo "Release path is not a directory: $RELEASE_DIR" >&2
    exit 1
  fi
else
  staging_dir="$(mktemp -d "$DEPLOY_PATH/releases/.$RELEASE_ID.XXXXXX")"
  tar -xzf "$INCOMING_ARCHIVE" -C "$staging_dir"
  if [ ! -f "$staging_dir/backend/app.jar" ] || [ ! -f "$staging_dir/frontend/index.html" ]; then
    echo "Release bundle is missing backend or frontend files." >&2
    exit 1
  fi
  chmod 755 "$staging_dir"
  mv -- "$staging_dir" "$RELEASE_DIR"
  staging_dir=""
fi

if [ ! -f "$RELEASE_DIR/backend/app.jar" ] || [ ! -f "$RELEASE_DIR/frontend/index.html" ]; then
  echo "Release is missing backend or frontend files." >&2
  exit 1
fi
sudo -n -u match-vault test -r "$RELEASE_DIR/backend/app.jar"

switch_link "$slot_link" "$RELEASE_DIR"
slot_changed=true
target_started=true
sudo -n systemctl start "$target_service"

health_url="http://127.0.0.1:$target_port/actuator/health"
deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
ready=false
while [ "$SECONDS" -lt "$deadline" ]; do
  service_state="$(systemctl is-active "$target_service" || true)"
  if [ "$service_state" = "failed" ] || [ "$service_state" = "inactive" ]; then break; fi
  http_status="$(curl --silent --max-time 5 --output /dev/null --write-out '%{http_code}' "$health_url" || true)"
  if [ "$http_status" = "200" ]; then
    ready=true
    break
  fi
  sleep "$HEALTH_INTERVAL_SECONDS"
done
if [ "$ready" != true ]; then
  echo "Candidate did not become healthy: $target_service ($health_url)" >&2
  exit 1
fi

sudo -n nginx -t
old_backend_file="$(mktemp "$DEPLOY_PATH/incoming/.backend-previous.XXXXXX")"
candidate_backend_file="$(mktemp "$DEPLOY_PATH/incoming/.backend-candidate.XXXXXX")"
cp "$ACTIVE_BACKEND_FILE" "$old_backend_file"
echo "server 127.0.0.1:$target_port;" > "$candidate_backend_file"

backend_changed=true
sudo -n install -m 644 "$candidate_backend_file" "$ACTIVE_BACKEND_FILE"
sudo -n nginx -t
sudo -n systemctl reload nginx

switch_link "$CURRENT_LINK" "$RELEASE_DIR"
current_changed=true
target_enabled=true
sudo -n systemctl enable "$target_service"
committed=true

echo "Traffic switched to $target_service. Waiting for $old_service to finish."
sudo -n systemctl disable "$old_service"
sudo -n systemctl stop "$old_service"
echo "Deployment succeeded: $RELEASE_ID ($target_color)"

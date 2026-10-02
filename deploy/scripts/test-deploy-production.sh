#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
scenario="$(mktemp -d)"
mkdir -p "$scenario/bin" "$scenario/state" "$scenario/project/incoming" "$scenario/project/releases/old/backend" "$scenario/project/releases/old/frontend"
export DEPLOY_SIM_STATE="$scenario/state"

cat > "$scenario/bin/sudo" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = -n ]; then shift; fi
if [ "$1" = -u ]; then shift 2; fi
exec "$@"
EOF
cat > "$scenario/bin/systemctl" <<'EOF'
#!/usr/bin/env bash
command="$1"
shift
case "$command" in
  show) echo loaded ;;
  is-active) if [ -f "$DEPLOY_SIM_STATE/$1.active" ]; then echo active; else echo inactive; fi ;;
  start) touch "$DEPLOY_SIM_STATE/$1.active" ;;
  stop) rm -f "$DEPLOY_SIM_STATE/$1.active" ;;
  enable) touch "$DEPLOY_SIM_STATE/$1.enabled" ;;
  disable) rm -f "$DEPLOY_SIM_STATE/$1.enabled" ;;
  reload)
    if [ "${FAIL_RELOAD_ONCE:-}" = 1 ] && [ ! -f "$DEPLOY_SIM_STATE/reload-failed" ]; then
      touch "$DEPLOY_SIM_STATE/reload-failed"
      exit 1
    fi
    ;;
  *) exit 1 ;;
esac
EOF
cat > "$scenario/bin/nginx" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$scenario/bin/curl" <<'EOF'
#!/usr/bin/env bash
if [ "${FAIL_HEALTH:-}" = 1 ]; then echo 503; else echo 200; fi
EOF
cat > "$scenario/bin/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$scenario/bin/"*
export PATH="$scenario/bin:$PATH"

echo old > "$scenario/project/releases/old/backend/app.jar"
echo old > "$scenario/project/releases/old/frontend/index.html"
ln -s "$scenario/project/releases/old" "$scenario/project/current"
ln -s "$scenario/project/releases/old" "$scenario/project/blue"
echo 'server 127.0.0.1:8080;' > "$scenario/active-backend.conf"
touch "$scenario/state/match-vault.active"

for release in newfail new1 newfailroute new2; do
  mkdir -p "$scenario/bundles/$release/backend" "$scenario/bundles/$release/frontend"
  echo "$release" > "$scenario/bundles/$release/backend/app.jar"
  echo "$release" > "$scenario/bundles/$release/frontend/index.html"
  tar -czf "$scenario/project/incoming/$release.tar.gz" -C "$scenario/bundles/$release" .
done

cat > "$scenario/nginx-site.conf" <<EOF
upstream match_vault_backend {
  include $scenario/active-backend.conf;
}
server {
  location /api/ { proxy_pass http://match_vault_backend; }
  location /actuator/health { proxy_pass http://match_vault_backend; }
}
EOF
sed -e "s|/etc/nginx/match-vault-active-backend.conf|$scenario/active-backend.conf|g" \
    -e "s|/etc/nginx/sites-available/match-vault.conf|$scenario/nginx-site.conf|g" \
    "$script_dir/deploy-production.sh" > "$scenario/deploy.sh"

if FAIL_HEALTH=1 HEALTH_TIMEOUT_SECONDS=1 bash "$scenario/deploy.sh" "$scenario/project" newfail; then
  echo 'Unhealthy candidate was accepted' >&2
  exit 1
fi
test "$(cat "$scenario/active-backend.conf")" = 'server 127.0.0.1:8080;'
test "$(readlink "$scenario/project/blue")" = "$scenario/project/releases/old"
test "$(readlink "$scenario/project/current")" = "$scenario/project/releases/old"
test -f "$scenario/state/match-vault.active"

bash "$scenario/deploy.sh" "$scenario/project" new1
test "$(cat "$scenario/active-backend.conf")" = 'server 127.0.0.1:8082;'
test "$(readlink "$scenario/project/current")" = "$scenario/project/releases/new1"
test -f "$scenario/state/match-vault-blue.active"
test ! -f "$scenario/state/match-vault.active"

if FAIL_RELOAD_ONCE=1 bash "$scenario/deploy.sh" "$scenario/project" newfailroute; then
  echo 'Failed Nginx reload was accepted' >&2
  exit 1
fi
test "$(cat "$scenario/active-backend.conf")" = 'server 127.0.0.1:8082;'
test "$(readlink "$scenario/project/current")" = "$scenario/project/releases/new1"
test -f "$scenario/state/match-vault-blue.active"
test ! -f "$scenario/state/match-vault-green.active"

bash "$scenario/deploy.sh" "$scenario/project" new2
test "$(cat "$scenario/active-backend.conf")" = 'server 127.0.0.1:8083;'
test "$(readlink "$scenario/project/current")" = "$scenario/project/releases/new2"
test -f "$scenario/state/match-vault-green.active"
test ! -f "$scenario/state/match-vault-blue.active"
echo 'Deployment simulation passed.'

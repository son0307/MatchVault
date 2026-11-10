#!/usr/bin/env bash
set -euo pipefail

deploy_path="$1"
case "$(cat /etc/nginx/match-vault-active-backend.conf)" in
  'server 127.0.0.1:8082;') active_port=8082 ;;
  'server 127.0.0.1:8083;') active_port=8083 ;;
  *) echo 'Unknown active backend' >&2; exit 1 ;;
esac

test -f "$deploy_path/current/monitoring/record-hikari.py"
for name in active pending max; do
  curl --fail --silent --show-error --max-time 5 \
    "http://127.0.0.1:$active_port/actuator/metrics/hikaricp.connections.$name" \
    | python3 -c 'import json,sys; data=json.load(sys.stdin); assert any(m["statistic"] == "VALUE" for m in data["measurements"])'
done
echo "Hikari metrics available on active backend $active_port"

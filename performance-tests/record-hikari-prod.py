#!/usr/bin/env python3
"""Sample the active production backend's Hikari gauges into a CSV file."""

import argparse
import csv
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import urlopen


ACTIVE_BACKEND = Path("/etc/nginx/match-vault-active-backend.conf")
METRICS = ("active", "pending", "max")


def active_port():
    match = re.fullmatch(r"server 127\.0\.0\.1:(8082|8083);\s*", ACTIVE_BACKEND.read_text())
    if match is None:
        raise RuntimeError(f"Unexpected active backend in {ACTIVE_BACKEND}")
    return int(match.group(1))


def metric_value(port, name):
    url = f"http://127.0.0.1:{port}/actuator/metrics/hikaricp.connections.{name}"
    with urlopen(url, timeout=2) as response:
        payload = json.load(response)
    values = [item["value"] for item in payload["measurements"] if item["statistic"] == "VALUE"]
    if len(values) != 1:
        raise RuntimeError(f"Expected one VALUE measurement for {name}: {payload}")
    return values[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=float, default=30)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.seconds <= 0 or args.interval <= 0:
        parser.error("--seconds and --interval must be positive")

    port = active_port()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + args.seconds
    max_active = max_pending = samples = 0
    with args.output.open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output)
        writer.writerow(("time_utc", "backend_port", *METRICS))
        print(f"Recording Hikari metrics on 127.0.0.1:{port} to {args.output}", flush=True)
        while time.monotonic() < deadline:
            sampled_at = datetime.now(timezone.utc).isoformat()
            values = [metric_value(port, name) for name in METRICS]
            writer.writerow((sampled_at, port, *values))
            output.flush()
            samples += 1
            max_active = max(max_active, values[0])
            max_pending = max(max_pending, values[1])
            time.sleep(args.interval)
    print(f"Samples={samples} max_active={max_active} max_pending={max_pending}")


if __name__ == "__main__":
    main()

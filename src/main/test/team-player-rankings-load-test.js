import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://matchvault.site").replace(/\/+$/, "");
const SEASON = __ENV.SEASON || "2025";
const VUS = Number(__ENV.VUS || "10");
const DURATION = __ENV.DURATION || "30s";
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || "0.1");
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "10s";
const ORIGIN_IP = __ENV.ORIGIN_IP || "";
const ORIGIN_HOST = __ENV.ORIGIN_HOST || "matchvault.site";
const TEAM_IDS = loadTeamIds();

export const options = buildOptions();

function buildOptions() {
  const baseOptions = {
    scenarios: {
      team_player_rankings: {
        executor: "constant-vus",
        vus: VUS,
        duration: DURATION,
        gracefulStop: __ENV.GRACEFUL_STOP || "5s",
      },
    },
    thresholds: {
      "http_req_failed{endpoint:team_player_rankings}": ["rate<0.01"],
      "http_req_duration{endpoint:team_player_rankings}": ["p(95)<500", "p(99)<1000"],
    },
  };

  if (ORIGIN_IP) {
    baseOptions.hosts = {
      [ORIGIN_HOST]: ORIGIN_IP,
    };
  }

  return baseOptions;
}

export default function () {
  const teamId = TEAM_IDS[Math.floor(Math.random() * TEAM_IDS.length)];
  const requestId = `k6-team-player-rankings-${teamId}-${__VU}-${__ITER}-${Date.now()}`;
  const url = `${BASE_URL}/api/v1/teams/${encodeURIComponent(teamId)}/player-rankings?season=${encodeURIComponent(SEASON)}`;

  const response = http.get(url, {
    headers: {
      Accept: "application/json",
      "X-Load-Test-Request-Id": requestId,
    },
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint: "team_player_rankings",
      route_mode: ORIGIN_IP ? "origin_direct" : "cloudflare_proxy",
      season: SEASON,
      team_id: String(teamId),
    },
  });

  check(response, {
    "team player rankings status is 200": (res) => res.status === 200,
    "team player rankings has rows array": (res) => hasArrayField(res, "rows"),
    "team player rankings response is under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(SLEEP_SECONDS);
}

function loadTeamIds() {
  const rawValue = __ENV.TEAM_IDS || "33,40,42,47,49,50";
  const teamIds = rawValue
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean)
    .map((value) => Number(value))
    .filter((value) => Number.isFinite(value) && value > 0);

  if (teamIds.length === 0) {
    throw new Error("TEAM_IDS must contain at least one positive numeric team id.");
  }

  return teamIds;
}

function hasArrayField(response, fieldName) {
  try {
    const body = response.json();
    return Array.isArray(body[fieldName]);
  } catch (error) {
    return false;
  }
}

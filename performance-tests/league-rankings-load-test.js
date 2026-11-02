import http from "k6/http";
import { check, fail, sleep } from "k6";
import exec from "k6/execution";

const BASE_URL = (__ENV.BASE_URL || "https://matchvault.site").replace(/\/+$/, "");
const LEAGUE_ID = positiveInteger(__ENV.LEAGUE_ID || "39", "LEAGUE_ID");
const SEASON = positiveInteger(__ENV.SEASON || "2025", "SEASON");
const TARGET = (__ENV.TARGET || "both").toLowerCase();
const MODE = (__ENV.MODE || "steady").toLowerCase();
const DEFAULT_VUS = Number(__ENV.VUS || "10");
const PLAYER_VUS = Number(__ENV.PLAYER_VUS || DEFAULT_VUS);
const TEAM_VUS = Number(__ENV.TEAM_VUS || DEFAULT_VUS);
const DURATION = __ENV.DURATION || "30s";
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || "0.1");
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "10s";
const WARM_UP = (__ENV.WARM_UP || "true").toLowerCase() === "true";
const ORIGIN_IP = __ENV.ORIGIN_IP || "";
const ORIGIN_HOST = __ENV.ORIGIN_HOST || "matchvault.site";
const STAMPEDE_BASELINE_VUS = 100;
const STAMPEDE_PEAK_VUS = 250;
const STAMPEDE_PEAKS = 3;
const STAMPEDE_BASELINE_SECONDS = 20;
const STAMPEDE_RAMP_SECONDS = 1;
const STAMPEDE_PEAK_SECONDS = 10;
const STAMPEDE_CYCLE_SECONDS =
  STAMPEDE_BASELINE_SECONDS + STAMPEDE_RAMP_SECONDS * 2 + STAMPEDE_PEAK_SECONDS;

const PLAYER_RANKINGS_URL =
  `${BASE_URL}/api/v1/leagues/${encodeURIComponent(LEAGUE_ID)}/player-rankings?season=${encodeURIComponent(SEASON)}`;
const TEAM_RANKINGS_URL =
  `${BASE_URL}/api/v1/leagues/${encodeURIComponent(LEAGUE_ID)}/team-rankings?season=${encodeURIComponent(SEASON)}`;

const PLAYER_ARRAY_FIELDS = [
  "goals",
  "assists",
  "attackPoints",
  "ratings",
  "minutes",
  "yellowCards",
  "redCards",
  "saves",
  "cleanSheets",
  "savePercentages",
];

const TEAM_ARRAY_FIELDS = [
  "goalsFor",
  "goalsAgainst",
  "possession",
  "yellowCards",
  "redCards",
];

export const options = buildOptions();

export function setup() {
  if (!WARM_UP || MODE === "stampede") {
    return;
  }

  if (runsPlayerScenario()) {
    warmUp("player rankings", PLAYER_RANKINGS_URL, "player_rankings_warm_up");
  }

  if (runsTeamScenario()) {
    warmUp("team rankings", TEAM_RANKINGS_URL, "team_rankings_warm_up");
  }
}

export function playerRankings() {
  const response = requestRankings(
    PLAYER_RANKINGS_URL,
    "league_player_rankings",
    "player",
  );

  check(response, {
    "player rankings status is 200": (res) => res.status === 200,
    "player rankings league and season match": hasExpectedScope,
    "player rankings has all ranking arrays": (res) => hasArrayFields(res, PLAYER_ARRAY_FIELDS),
    "player rankings response is under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(SLEEP_SECONDS);
}

export function stampedePlayerRankings() {
  const elapsedSeconds = exec.instance.currentTestRunDuration / 1000;
  const cycle = Math.floor(elapsedSeconds / STAMPEDE_CYCLE_SECONDS);
  const cycleElapsed = elapsedSeconds % STAMPEDE_CYCLE_SECONDS;
  const peakStart = STAMPEDE_BASELINE_SECONDS;
  const inPeak = cycle < STAMPEDE_PEAKS
    && cycleElapsed >= peakStart
    && cycleElapsed < peakStart + STAMPEDE_RAMP_SECONDS + STAMPEDE_PEAK_SECONDS;
  const response = requestRankings(
    PLAYER_RANKINGS_URL,
    "league_player_rankings",
    "player",
    { phase: inPeak ? "peak" : "baseline", peak: inPeak ? String(cycle + 1) : "none" },
  );
  check(response, {
    "player rankings status is 200": (res) => res.status === 200,
    "player rankings league and season match": hasExpectedScope,
    "player rankings has all ranking arrays": (res) => hasArrayFields(res, PLAYER_ARRAY_FIELDS),
  });
  sleep(SLEEP_SECONDS);
}

export function teamRankings() {
  const response = requestRankings(
    TEAM_RANKINGS_URL,
    "league_team_rankings",
    "team",
  );

  check(response, {
    "team rankings status is 200": (res) => res.status === 200,
    "team rankings league and season match": hasExpectedScope,
    "team rankings has all ranking arrays": (res) => hasArrayFields(res, TEAM_ARRAY_FIELDS),
    "team rankings response is under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(SLEEP_SECONDS);
}

function buildOptions() {
  validateTarget();

  if (MODE === "stampede") {
    if (TARGET !== "player") {
      throw new Error("MODE=stampede requires TARGET=player to isolate the player ranking p95.");
    }
    if (!__ENV.BASE_URL) {
      throw new Error("MODE=stampede requires an explicit BASE_URL.");
    }
    const stages = [];
    for (let i = 0; i < STAMPEDE_PEAKS; i += 1) {
      stages.push(
        { duration: `${STAMPEDE_BASELINE_SECONDS}s`, target: STAMPEDE_BASELINE_VUS },
        { duration: `${STAMPEDE_RAMP_SECONDS}s`, target: STAMPEDE_PEAK_VUS },
        { duration: `${STAMPEDE_PEAK_SECONDS}s`, target: STAMPEDE_PEAK_VUS },
        { duration: `${STAMPEDE_RAMP_SECONDS}s`, target: STAMPEDE_BASELINE_VUS },
      );
    }
    return {
      scenarios: {
        league_player_rankings_stampede: {
          executor: "ramping-vus",
          exec: "stampedePlayerRankings",
          startVUs: STAMPEDE_BASELINE_VUS,
          stages,
          gracefulRampDown: "5s",
        },
      },
      thresholds: {
        "http_req_failed{endpoint:league_player_rankings,phase:peak}": ["rate<0.01"],
        "http_req_duration{endpoint:league_player_rankings,phase:peak}": ["p(95)<500"],
      },
      ...(ORIGIN_IP ? { hosts: { [ORIGIN_HOST]: ORIGIN_IP } } : {}),
    };
  }

  if (MODE !== "steady") {
    throw new Error("MODE must be steady or stampede.");
  }

  const scenarios = {};
  const thresholds = {};

  if (runsPlayerScenario()) {
    scenarios.league_player_rankings = constantVusScenario("playerRankings", PLAYER_VUS);
    thresholds["http_req_failed{endpoint:league_player_rankings}"] = ["rate<0.01"];
    thresholds["http_req_duration{endpoint:league_player_rankings}"] = [
      "p(95)<500",
      "p(99)<1000",
    ];
  }

  if (runsTeamScenario()) {
    scenarios.league_team_rankings = constantVusScenario("teamRankings", TEAM_VUS);
    thresholds["http_req_failed{endpoint:league_team_rankings}"] = ["rate<0.01"];
    thresholds["http_req_duration{endpoint:league_team_rankings}"] = [
      "p(95)<500",
      "p(99)<1000",
    ];
  }

  const result = {
    scenarios,
    thresholds,
  };

  if (ORIGIN_IP) {
    result.hosts = {
      [ORIGIN_HOST]: ORIGIN_IP,
    };
  }

  return result;
}

function constantVusScenario(exec, vus) {
  if (!Number.isFinite(vus) || vus <= 0) {
    throw new Error(`${exec} VUs must be a positive number.`);
  }

  return {
    executor: "constant-vus",
    exec,
    vus,
    duration: DURATION,
    gracefulStop: __ENV.GRACEFUL_STOP || "5s",
  };
}

function requestRankings(url, endpoint, rankingType, extraTags = {}) {
  const requestId = `k6-league-${rankingType}-rankings-${__VU}-${__ITER}-${Date.now()}`;

  return http.get(url, {
    headers: {
      Accept: "application/json",
      "X-Load-Test-Request-Id": requestId,
    },
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint,
      ranking_type: rankingType,
      route_mode: ORIGIN_IP ? "origin_direct" : "cloudflare_proxy",
      league_id: String(LEAGUE_ID),
      season: String(SEASON),
      cache_mode: MODE === "stampede" ? "ttl-cycles" : WARM_UP ? "warm" : "uncontrolled",
      ...extraTags,
    },
  });
}

function warmUp(name, url, endpoint) {
  const response = http.get(url, {
    headers: {
      Accept: "application/json",
      "X-Load-Test-Request-Id": `k6-${endpoint}-${Date.now()}`,
    },
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint,
      league_id: String(LEAGUE_ID),
      season: String(SEASON),
    },
  });

  if (response.status !== 200) {
    fail(`${name} warm-up failed. status=${response.status}`);
  }
}

function hasExpectedScope(response) {
  try {
    const body = response.json();
    return Number(body.leagueId) === LEAGUE_ID && Number(body.season) === SEASON;
  } catch (error) {
    return false;
  }
}

function hasArrayFields(response, fieldNames) {
  try {
    const body = response.json();
    return fieldNames.every((fieldName) => Array.isArray(body[fieldName]));
  } catch (error) {
    return false;
  }
}

function positiveInteger(rawValue, name) {
  const value = Number(rawValue);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return value;
}

function validateTarget() {
  if (!["both", "player", "team"].includes(TARGET)) {
    throw new Error("TARGET must be both, player, or team.");
  }
}

function runsPlayerScenario() {
  return TARGET === "both" || TARGET === "player";
}

function runsTeamScenario() {
  return TARGET === "both" || TARGET === "team";
}

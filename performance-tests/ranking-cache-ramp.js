import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const leagueId = positiveInteger(__ENV.LEAGUE_ID || '39', 'LEAGUE_ID');
const seasons = (__ENV.SEASONS || '2024,2025,2026').split(',')
  .map((value) => positiveInteger(value.trim(), 'SEASONS'));
const teamsPerSeason = positiveInteger(__ENV.TEAMS_PER_SEASON || '4', 'TEAMS_PER_SEASON');
const requestTimeout = __ENV.REQUEST_TIMEOUT || '10s';
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || '0.1');

const baselineVus = 100;
const peakVus = 250;
const peaks = 3;
const baselineSeconds = 20;
const rampSeconds = 1;
const peakSeconds = 10;
const cycleSeconds = baselineSeconds + rampSeconds * 2 + peakSeconds;

if (!baseUrl || !/^https?:\/\//.test(baseUrl)) {
  throw new Error('BASE_URL must be an explicit http(s) URL.');
}
if (new Set(seasons).size !== seasons.length || seasons.length < 2 || teamsPerSeason < 2 ||
    !Number.isFinite(sleepSeconds) || sleepSeconds < 0) {
  throw new Error('Use at least two distinct seasons, two teams per season, and nonnegative SLEEP_SECONDS.');
}

const stages = [];
for (let i = 0; i < peaks; i += 1) {
  stages.push(
    { duration: `${baselineSeconds}s`, target: baselineVus },
    { duration: `${rampSeconds}s`, target: peakVus },
    { duration: `${peakSeconds}s`, target: peakVus },
    { duration: `${rampSeconds}s`, target: baselineVus },
  );
}

export const options = {
  scenarios: {
    ranking_mixed_stampede: {
      executor: 'ramping-vus',
      startVUs: baselineVus,
      stages,
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed{phase:peak}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  const coverage = http.get(`${baseUrl}/api/v1/leagues/${leagueId}/seasons`, { timeout: requestTimeout });
  if (coverage.status !== 200) {
    fail(`Season coverage preflight failed: HTTP ${coverage.status}`);
  }
  const available = new Set((coverage.json('seasons') || []).map((item) => item.seasonYear));
  const cases = [];
  for (const season of seasons) {
    if (!available.has(season)) {
      fail(`Season ${season} is absent from league ${leagueId} coverage.`);
    }
    cases.push({ endpoint: 'league-player', season, teamId: null });
    cases.push({ endpoint: 'league-team', season, teamId: null });

    const standings = http.get(`${baseUrl}/api/v1/teams/standings?season=${season}`, {
      timeout: requestTimeout,
    });
    if (standings.status !== 200) {
      fail(`Team standings preflight failed for ${season}: HTTP ${standings.status}`);
    }
    const teamIds = [...new Set((standings.json() || [])
      .map((item) => item.team && item.team.id)
      .filter((id) => Number.isInteger(id)))].slice(0, teamsPerSeason);
    if (teamIds.length < teamsPerSeason) {
      fail(`Season ${season} has fewer than ${teamsPerSeason} usable teams.`);
    }
    for (const teamId of teamIds) {
      cases.push({ endpoint: 'team-player', season, teamId });
    }
  }
  // setup() does not call the ranking endpoints, so it does not warm the tested entries.
  console.log(`Ranking cases: ${JSON.stringify(cases)}`);
  return { cases };
}

export default function (data) {
  const cycle = Math.floor(exec.instance.currentTestRunDuration / 1000 / cycleSeconds);
  const cycleElapsed = exec.instance.currentTestRunDuration / 1000 % cycleSeconds;
  const inPeak = cycle < peaks && cycleElapsed >= baselineSeconds &&
    cycleElapsed < baselineSeconds + rampSeconds + peakSeconds;
  const testCase = data.cases[(__VU - 1 + __ITER) % data.cases.length];
  const path = testCase.endpoint === 'team-player'
    ? `/api/v1/teams/${testCase.teamId}/player-rankings?season=${testCase.season}`
    : `/api/v1/leagues/${leagueId}/${testCase.endpoint === 'league-player' ? 'player-rankings' : 'team-rankings'}?season=${testCase.season}`;
  const tags = {
    endpoint: testCase.endpoint,
    season: String(testCase.season),
    phase: inPeak ? 'peak' : 'baseline',
    peak: inPeak ? String(cycle + 1) : 'none',
  };
  const response = http.get(`${baseUrl}${path}`, {
    timeout: requestTimeout,
    tags,
    headers: { Accept: 'application/json' },
  });
  let valid = false;
  if (response.status === 200) {
    try {
      const body = response.json();
      const rows = testCase.endpoint === 'team-player' ? body.rows
        : testCase.endpoint === 'league-player' ? body.goals : body.goalsFor;
      valid = Array.isArray(rows) && rows.length > 0;
    } catch (_) {
      valid = false;
    }
  }
  check(response, {
    'ranking HTTP 200': (res) => res.status === 200,
    'ranking has data': () => valid,
  }, tags);
  sleep(sleepSeconds);
}

function positiveInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must contain positive integers.`);
  }
  return parsed;
}

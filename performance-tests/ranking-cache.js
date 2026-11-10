import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const rankingDuration = new Trend('ranking_request_duration', true);
const rankingFailed = new Rate('ranking_request_failed');

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const testCase = __ENV.TEST_CASE || 'same-key';
const seasons = (__ENV.SEASONS || '2025,2024,2026').split(',').map((value) => Number(value.trim()));
const vus = Number(__ENV.VUS || 50);
const teamsPerSeason = Number(__ENV.TEAMS_PER_SEASON || 4);
const supportedCases = ['same-key', 'mixed-season', 'mixed-team', 'mixed-all'];

if (!supportedCases.includes(testCase)) {
  throw new Error(`Unknown TEST_CASE: ${testCase}`);
}
if (!Number.isInteger(vus) || vus < 1 || !Number.isInteger(teamsPerSeason) || teamsPerSeason < 2 ||
    seasons.length === 0 || seasons.some((season) => !Number.isInteger(season))) {
  throw new Error('VUS, TEAMS_PER_SEASON and SEASONS must contain positive integers');
}
if (testCase !== 'same-key' && seasons.length < 2) {
  throw new Error('Mixed cases need at least two seasons');
}

export const options = {
  scenarios: {
    ranking_burst: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration: '40s',
    },
  },
  thresholds: {
    ranking_request_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  const coverage = http.get(`${baseUrl}/api/v1/leagues/39/seasons`, { timeout: '10s' });
  if (coverage.status !== 200) {
    fail(`Season coverage preflight failed: HTTP ${coverage.status}`);
  }
  const available = new Set((coverage.json('seasons') || []).map((item) => item.seasonYear));
  for (const season of seasons) {
    if (!available.has(season)) {
      fail(`Season ${season} is absent from league coverage`);
    }
  }

  const teamsBySeason = {};
  if (testCase === 'mixed-team' || testCase === 'mixed-all') {
    for (const season of seasons) {
      const standings = http.get(`${baseUrl}/api/v1/teams/standings?season=${season}`, { timeout: '15s' });
      if (standings.status !== 200) {
        fail(`Standings preflight failed for ${season}: HTTP ${standings.status}`);
      }
      teamsBySeason[season] = standings.json()
        .map((item) => item.team && item.team.id)
        .filter((id) => Number.isInteger(id))
        .slice(0, teamsPerSeason);
      if (teamsBySeason[season].length < 2) {
        fail(`Season ${season} has fewer than two usable team IDs`);
      }
    }
  }
  return { teamsBySeason };
}

export default function (data) {
  const index = __VU - 1;
  let season;
  let teamId;
  let endpoint;

  if (testCase === 'same-key') {
    season = seasons[0];
    endpoint = 'league-player';
  } else if (testCase === 'mixed-season') {
    season = seasons[index % seasons.length];
    endpoint = 'league-player';
  } else if (testCase === 'mixed-team') {
    season = seasons[index % seasons.length];
    const teams = data.teamsBySeason[season];
    teamId = teams[Math.floor(index / seasons.length) % teams.length];
    endpoint = 'team-player';
  } else {
    const endpoints = ['league-player', 'league-team', 'team-player'];
    endpoint = endpoints[index % endpoints.length];
    season = seasons[Math.floor(index / endpoints.length) % seasons.length];
    if (endpoint === 'team-player') {
      const teams = data.teamsBySeason[season];
      teamId = teams[Math.floor(index / (endpoints.length * seasons.length)) % teams.length];
    }
  }

  const path = endpoint === 'team-player'
    ? `/api/v1/teams/${teamId}/player-rankings?season=${season}`
    : `/api/v1/leagues/39/${endpoint === 'league-player' ? 'player-rankings' : 'team-rankings'}?season=${season}`;
  const response = http.get(`${baseUrl}${path}`, {
    timeout: '25s',
    headers: { Accept: 'application/json', 'Accept-Encoding': 'gzip' },
    tags: { test_case: testCase, endpoint, season: String(season) },
  });
  const tags = { test_case: testCase, endpoint, season: String(season) };

  let populated = false;
  if (response.status === 200) {
    try {
      const body = response.json();
      const rows = endpoint === 'team-player' ? body.rows
        : endpoint === 'league-player' ? body.goals : body.goalsFor;
      populated = Array.isArray(rows) && rows.length > 0;
    } catch (_) {
      populated = false;
    }
  }
  rankingDuration.add(response.timings.duration, tags);
  rankingFailed.add(response.status !== 200 || !populated, tags);
  check(response, {
    'HTTP 200': (item) => item.status === 200,
    'ranking has data': () => populated,
  }, tags);
}

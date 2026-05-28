export type FixtureSummary = {
  fixtureId: number;
  fixtureDate: string | null;
  round: number | null;
  homeTeamName: string | null;
  awayTeamName: string | null;
  homeTeamLogoUrl: string | null;
  awayTeamLogoUrl: string | null;
  homeScore: number;
  awayScore: number;
  fixtureStatus: string | null;
};

export type FixtureMeta = {
  minDate: string | null;
  maxDate: string | null;
  minRound: number | null;
  maxRound: number | null;
};

export type TeamSummary = {
  teamId: number;
  teamName: string | null;
  code: string | null;
  logoUrl: string | null;
};

export type FixtureQuery = {
  season?: number;
  round?: number;
  date?: string;
  dateFrom?: string;
  dateTo?: string;
  teamId?: number;
  cursorId?: number | null;
  size?: number;
};

export type CursorResponse<T> = {
  content: T[];
  nextCursor: number | null;
  hasNext: boolean;
};

export type StandingGoals = {
  goalsFor: number | null;
  goalsAgainst: number | null;
};

export type StandingRecord = {
  played: number | null;
  win: number | null;
  draw: number | null;
  lose: number | null;
  goals: StandingGoals | null;
};

export type RecentForm = StandingRecord & {
  points: number | null;
  goalsDiff: number | null;
  results: string[];
};

export type TeamStanding = {
  season: number | null;
  rank: number | null;
  team: {
    id: number;
    name: string | null;
    logo: string | null;
  } | null;
  points: number | null;
  goalsDiff: number | null;
  form: string | null;
  description: string | null;
  all: StandingRecord | null;
  home: StandingRecord | null;
  away: StandingRecord | null;
  recentForm: RecentForm | null;
};

export type HomeSummary = {
  todayFixtures: FixtureSummary[];
  standings: TeamStanding[];
};

export type FavoriteDashboard = {
  teams: FavoriteTeamCard[];
  players: FavoritePlayerCard[];
};

export type FavoriteTeamCard = {
  teamId: number;
  teamName: string | null;
  logoUrl: string | null;
  rank: number | null;
  points: number | null;
  form: string | null;
  recentFixtures: FavoriteTeamFixture[];
  nextFixture: FavoriteTeamFixture | null;
  liveFixture: FavoriteLiveFixture | null;
};

export type FavoriteTeamFixture = {
  fixtureId: number;
  fixtureDate: string | null;
  homeTeamName: string | null;
  awayTeamName: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
  result: string | null;
};

export type FavoriteLiveFixture = FavoriteTeamFixture & {
  statusShort: string | null;
  statusLong: string | null;
  elapsed: number | null;
};

export type FavoritePlayerCard = {
  playerId: number;
  playerName: string | null;
  photoUrl: string | null;
  position: string | null;
  recentMatch: FavoriteRecentPlayerMatch | null;
  seasonStat: FavoritePlayerSeasonStat | null;
};

export type FavoriteRecentPlayerMatch = {
  fixtureId: number;
  fixtureDate: string | null;
  teamName: string | null;
  opponentTeamName: string | null;
  teamScore: number | null;
  opponentScore: number | null;
  minutesPlayed: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
};

export type FavoritePlayerSeasonStat = {
  season: number | null;
  teamName: string | null;
  teamLogoUrl: string | null;
  appearances: number | null;
  minutes: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
  yellowCards: number | null;
  redCards: number | null;
};

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function fetchHomeSummary(season: number): Promise<HomeSummary> {
  const response = await fetch(`/api/v1/home/summary?season=${season}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`홈 정보를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchStandings(season: number): Promise<TeamStanding[]> {
  const response = await fetch(`/api/v1/teams/standings?season=${season}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`순위 정보를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFixturesByDate(
  season: number,
  dateKey: string,
): Promise<CursorResponse<FixtureSummary>> {
  return fetchFixtures({ season, date: dateKey, size: 100 });
}

export async function fetchFixtures(query: FixtureQuery): Promise<CursorResponse<FixtureSummary>> {
  const params = new URLSearchParams();
  appendParam(params, "season", query.season);
  appendParam(params, "round", query.round);
  appendParam(params, "date", query.date);
  appendParam(params, "dateFrom", query.dateFrom);
  appendParam(params, "dateTo", query.dateTo);
  appendParam(params, "teamId", query.teamId);
  appendParam(params, "cursorId", query.cursorId ?? undefined);
  appendParam(params, "size", query.size);

  const response = await fetch(`/api/v1/fixtures?${params.toString()}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`경기 일정을 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFixtureMeta(season: number): Promise<FixtureMeta> {
  const response = await fetch(`/api/v1/fixtures/meta?season=${season}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`경기 범위를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchTeams(): Promise<TeamSummary[]> {
  const response = await fetch("/api/v1/teams", {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`팀 목록을 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFavoriteDashboard(season: number): Promise<FavoriteDashboard> {
  const response = await fetch(`/api/v1/favorites/dashboard?season=${season}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`즐겨찾기를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

function appendParam(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== null && value !== "") {
    params.set(key, String(value));
  }
}

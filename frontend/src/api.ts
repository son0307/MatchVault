export type FixtureSummary = {
  fixtureId: number;
  fixtureDate: string | null;
  round: number | null;
  homeTeamId: number | null;
  awayTeamId: number | null;
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

export type FixtureEventResponse = {
  fixtureId: number;
  events: FixtureEvent[];
};

export type FixtureEvent = {
  sequence: number | null;
  time: {
    elapsed: number | null;
    extra: number | null;
  } | null;
  team: {
    id: number;
    name: string | null;
    logo: string | null;
  } | null;
  player: FixtureEventPlayer | null;
  assist: FixtureEventPlayer | null;
  type: string | null;
  detail: string | null;
  comments: string | null;
};

export type FixtureEventPlayer = {
  id: number;
  name: string | null;
};

export type FixtureLineupResponse = {
  fixtureId: number;
  homeTeam: FixtureTeamLineup | null;
  awayTeam: FixtureTeamLineup | null;
};

export type FixtureTeamLineup = {
  teamId: number;
  teamName: string | null;
  formation: string | null;
  coachName: string | null;
  colors: FixtureUniformColors | null;
  starters: FixtureLineupPlayer[];
  substitutes: FixtureLineupPlayer[];
  absences: FixtureLineupAbsence[];
};

export type FixtureUniformColors = {
  player: FixtureColorInfo | null;
  goalkeeper: FixtureColorInfo | null;
};

export type FixtureColorInfo = {
  primary: string | null;
  number: string | null;
  border: string | null;
};

export type FixtureLineupPlayer = {
  playerId: number;
  playerName: string | null;
  backNumber: number | null;
  position: string | null;
  grid: string | null;
  starter: boolean;
};

export type FixtureLineupAbsence = {
  playerId: number;
  playerName: string | null;
  teamId: number;
  teamName: string | null;
  absenceType: string | null;
  reason: string | null;
};

export type FixtureStatResponse = {
  fixtureId: number;
  homeTeamStat: FixtureTeamStat | null;
  awayTeamStat: FixtureTeamStat | null;
};

export type FixtureTeamStat = {
  teamId: number;
  score: number;
  shotsOnGoal: number;
  shotsOffGoal: number;
  totalShots: number;
  shotsOnTarget: number;
  blockedShots: number;
  shotsInsideBox: number;
  shotsOutsideBox: number;
  totalPasses: number;
  passesAccurate: number;
  passAccuracy: number;
  fouls: number;
  cornerKicks: number;
  offsides: number;
  ballPossession: number;
  goalkeeperSaves: number;
  yellowCards: number;
  redCards: number;
  expectedGoals: number | null;
};

export type FixturePlayerStatResponse = {
  fixtureId: number;
  homeTeam: FixtureTeamPlayerStats | null;
  awayTeam: FixtureTeamPlayerStats | null;
};

export type FixtureTeamPlayerStats = {
  teamId: number;
  teamName: string | null;
  players: FixturePlayerStat[];
};

export type FixturePlayerStat = {
  playerId: number;
  playerName: string | null;
  jerseyNumber: number | null;
  position: string | null;
  minutesPlayed: number | null;
  rating: number | null;
  captain: boolean | null;
  substitute: boolean | null;
  goals: number | null;
  assists: number | null;
  shotsTotal: number | null;
  shotsOnTarget: number | null;
  passesTotal: number | null;
  passesKey: number | null;
  tacklesTotal: number | null;
  yellowCards: number | null;
  redCards: number | null;
};

export type TeamSummary = {
  teamId: number;
  teamName: string | null;
  code: string | null;
  logoUrl: string | null;
};

export type TeamDetails = TeamSummary & {
  country: string | null;
  founded: number | null;
  venue: TeamVenue | null;
};

export type TeamVenue = {
  venueId: number | null;
  venueName: string | null;
  venueAddress: string | null;
  venueCity: string | null;
  capacity: number | null;
  surface: string | null;
  venueImageUrl: string | null;
};

export type PlayerSummary = {
  playerId: number;
  playerName: string | null;
  backNumber: number | null;
  position: string | null;
  photoUrl: string | null;
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

export type PlayerPanel = {
  profile: PlayerProfile | null;
  seasons: PlayerSeasonSummary[];
  matches: PlayerMatchStat[];
};

export type PlayerProfile = {
  playerId: number;
  playerName: string | null;
  firstname: string | null;
  lastname: string | null;
  backNumber: number | null;
  age: number | null;
  birthDate: string | null;
  birthPlace: string | null;
  birthCountry: string | null;
  nationality: string | null;
  height: string | null;
  weight: string | null;
  position: string | null;
  photoUrl: string | null;
  teamId: number | null;
  teamName: string | null;
  teamLogoUrl: string | null;
};

export type PlayerSeasonSummary = {
  season: number | null;
  totalFixtures: number;
  minutesPlayed: number;
  averageRating: number;
  goals: number;
  assists: number;
  shots: number;
  shotsOnTarget: number;
  keyPasses: number;
  yellowCards: number;
  redCards: number;
  teams: PlayerTeamSeasonSummary[];
};

export type PlayerTeamSeasonSummary = {
  teamId: number;
  teamName: string | null;
  teamLogoUrl: string | null;
  totalFixtures: number;
  minutesPlayed: number;
  averageRating: number;
  goals: number;
  assists: number;
  shots: number;
  shotsOnTarget: number;
  keyPasses: number;
  yellowCards: number;
  redCards: number;
};

export type PlayerMatchStat = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  teamId: number | null;
  teamName: string | null;
  opponentTeamId: number | null;
  opponentTeamName: string | null;
  teamScore: number | null;
  opponentScore: number | null;
  homeTeamId: number | null;
  homeTeamName: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
  homeScore: number | null;
  awayScore: number | null;
  minutesPlayed: number | null;
  rating: number | null;
  goals: number | null;
  assists: number | null;
};

export type CurrentUser = {
  id: number;
  email: string;
  nickname: string | null;
  role: string;
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

export async function fetchFixture(fixtureId: number): Promise<FixtureSummary> {
  const response = await fetch(`/api/v1/fixtures/${fixtureId}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`경기 정보를 불러오지 못했습니다. (${response.status})`, response.status);
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

export async function fetchFixtureEvents(fixtureId: number): Promise<FixtureEventResponse> {
  const response = await fetch(`/api/v1/fixtures/${fixtureId}/events`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`경기 이벤트를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFixtureLineups(fixtureId: number): Promise<FixtureLineupResponse> {
  const response = await fetch(`/api/v1/fixtures/${fixtureId}/lineups`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`라인업을 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFixtureStats(fixtureId: number): Promise<FixtureStatResponse> {
  const response = await fetch(`/api/v1/fixtures/${fixtureId}/stats`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`팀 통계를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchFixturePlayerStats(fixtureId: number): Promise<FixturePlayerStatResponse> {
  const response = await fetch(`/api/v1/fixtures/${fixtureId}/player-stats`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`선수별 경기 통계를 불러오지 못했습니다. (${response.status})`, response.status);
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

export async function fetchTeamDetails(teamId: number): Promise<TeamDetails> {
  const response = await fetch(`/api/v1/teams/${teamId}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`팀 정보를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function fetchTeamPlayers(teamId: number, season: number): Promise<PlayerSummary[]> {
  const response = await fetch(`/api/v1/teams/${teamId}/players?season=${season}`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`팀 선수 목록을 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

export async function login(email: string, password: string): Promise<CurrentUser> {
  return parseCurrentUser(await postJson("/api/v1/auth/login", { email, password }));
}

export async function signup(email: string, password: string, nickname: string): Promise<CurrentUser> {
  return parseCurrentUser(await postJson("/api/v1/auth/signup", { email, password, nickname }));
}

export async function logout(): Promise<void> {
  const response = await fetch("/api/v1/auth/logout", {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `로그아웃에 실패했습니다. (${response.status})`);
  }
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await fetch("/api/v1/auth/me", {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `로그인이 필요합니다. (${response.status})`);
  }

  return parseCurrentUser(await responseJson(response, "로그인 사용자 정보가 올바르지 않습니다."));
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

export async function addFavoriteTeam(teamId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/teams/${teamId}?season=${season}`, "POST");
}

export async function removeFavoriteTeam(teamId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/teams/${teamId}?season=${season}`, "DELETE");
}

export async function addFavoritePlayer(playerId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/players/${playerId}?season=${season}`, "POST");
}

export async function removeFavoritePlayer(playerId: number, season: number): Promise<FavoriteDashboard> {
  return requestFavoriteDashboard(`/api/v1/favorites/players/${playerId}?season=${season}`, "DELETE");
}

export async function fetchPlayerPanel(playerId: number): Promise<PlayerPanel> {
  const response = await fetch(`/api/v1/players/${playerId}/panel`, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(`선수 정보를 불러오지 못했습니다. (${response.status})`, response.status);
  }

  return response.json();
}

async function requestFavoriteDashboard(url: string, method: "POST" | "DELETE"): Promise<FavoriteDashboard> {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await responseError(response, `즐겨찾기 요청에 실패했습니다. (${response.status})`);
  }

  return response.json();
}

function appendParam(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== null && value !== "") {
    params.set(key, String(value));
  }
}

async function postJson(url: string, body: unknown): Promise<unknown> {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    credentials: "same-origin",
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw await responseError(response, `${url} 요청에 실패했습니다. (${response.status})`);
  }

  return responseJson(response, `${url} 응답이 비어 있습니다.`);
}

async function responseError(response: Response, fallbackMessage: string) {
  try {
    const errorBody = (await response.json()) as { message?: string };
    return new ApiError(errorBody.message || fallbackMessage, response.status);
  } catch {
    return new ApiError(fallbackMessage, response.status);
  }
}

async function responseJson(response: Response, emptyMessage: string): Promise<unknown> {
  if (response.status === 204) {
    throw new ApiError(emptyMessage, response.status);
  }

  try {
    return await response.json();
  } catch {
    throw new ApiError(emptyMessage, response.status);
  }
}

function parseCurrentUser(value: unknown): CurrentUser {
  if (
    !isRecord(value)
    || typeof value.id !== "number"
    || typeof value.email !== "string"
    || typeof value.role !== "string"
  ) {
    throw new ApiError("로그인 사용자 정보가 올바르지 않습니다.", 500);
  }

  return {
    id: value.id,
    email: value.email,
    nickname: typeof value.nickname === "string" ? value.nickname : null,
    role: value.role,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

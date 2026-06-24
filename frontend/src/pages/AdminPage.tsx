import { FormEvent, useEffect, useRef, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { Navigate } from "react-router-dom";
import {
  clearApiMemoryCache,
  fetchFixtures,
  fetchLeagueSeasons,
  fetchTeamPlayers,
  type LeagueSeasonCoverage,
  type PlayerSummary,
} from "../api";
import { type LeagueAuthState } from "../App";

type AdminPageProps = {
  authState: LeagueAuthState;
};

type AdminTab = "team" | "player" | "fixture";

type FieldKind = "text" | "number" | "datetime" | "date" | "boolean" | "select";

type FieldOption = {
  label: string;
  value: string | number;
};

type FieldConfig = {
  name: string;
  label: string;
  kind?: FieldKind;
  help?: string;
  min?: number;
  max?: number;
  options?: FieldOption[];
  preview?: "color";
};

type AdminOverride = {
  fieldName: string;
  updatedAt: string | null;
};

type TeamAdmin = Record<string, unknown> & {
  teamId: number;
  name: string | null;
  manualOverrides?: AdminOverride[];
};

type TeamSelectionState = {
  teamId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: TeamAdmin | null;
};

type PlayerAdmin = Record<string, unknown> & {
  playerId: number;
  name: string | null;
  manualOverrides?: AdminOverride[];
};

type PlayerSelectionState = {
  playerId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: PlayerAdmin | null;
};

type FixtureSummaryAdmin = {
  fixtureId: number;
  fixtureDate: string | null;
  season: number | null;
  round: number | null;
  homeTeamName: string | null;
  awayTeamName: string | null;
  homeScore: number | null;
  awayScore: number | null;
  fixtureStatus: string | null;
};

type FixtureDetailAdmin = {
  fixture: FixtureAdmin;
  events: FixtureEventAdmin[];
  lineups: FixtureLineupAdmin[];
  teamStats: FixtureTeamStatAdmin[];
  playerStats: FixturePlayerStatAdmin[];
};

type FixtureSelectionState = {
  fixtureId: number | null;
  status: "idle" | "loading" | "ready" | "error";
  detail: FixtureDetailAdmin | null;
};

type FixtureListStatus = "idle" | "loading" | "ready" | "error";

type FixtureTeamOption = {
  teamId: number;
  name: string | null;
};

type FixtureAdmin = Record<string, unknown> & {
  fixtureId: number;
  homeTeamId: number | null;
  homeTeamName: string | null;
  awayTeamId: number | null;
  awayTeamName: string | null;
};

type FixtureEventAdmin = Record<string, unknown> & {
  eventSequence: number;
  playerName: string | null;
  eventType: string | null;
  eventDetail: string | null;
};

type FixtureLineupAdmin = Record<string, unknown> & {
  teamId: number;
  teamName: string | null;
  playerId: number;
  playerName: string | null;
};

type FixtureTeamStatAdmin = Record<string, unknown> & {
  teamId: number;
  teamName: string | null;
};

type FixturePlayerStatAdmin = Record<string, unknown> & {
  playerId: number;
  playerName: string | null;
  teamName: string | null;
};

type AuditLog = {
  id: number;
  adminEmail: string | null;
  type: string;
  targetType: string | null;
  targetId: number | null;
  message: string;
  details: string | null;
  success: boolean;
  createdAt: string | null;
};

type SyncStatus = {
  task: string;
  label: string;
  lastSyncedAt: string | null;
};

type CoverageStatus = "loading" | "ready" | "error";

type AuditLogPage = {
  logs: AuditLog[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
};

const AUDIT_LOG_PAGE_SIZE = 20;
const ADMIN_SEARCH_KEYWORD_MAX_LENGTH = 80;
const MANUAL_SYNC_COOLDOWN_MS = 30_000;

const teamFields: FieldConfig[] = [
  { name: "name", label: "Name" },
  { name: "code", label: "Code" },
  { name: "country", label: "Country" },
  { name: "founded", label: "Founded", kind: "number" },
  { name: "logoUrl", label: "Logo URL" },
  { name: "venueId", label: "Venue ID", kind: "number" },
  { name: "venueName", label: "Venue Name" },
  { name: "venueAddress", label: "Venue Address" },
  { name: "venueCity", label: "Venue City" },
  { name: "capacity", label: "Capacity", kind: "number" },
  { name: "surface", label: "Surface" },
  { name: "venueImageUrl", label: "Venue Image URL" },
];

const playerFields: FieldConfig[] = [
  { name: "name", label: "Name" },
  { name: "firstname", label: "Firstname" },
  { name: "lastname", label: "Lastname" },
  { name: "age", label: "Age", kind: "number" },
  { name: "birthDate", label: "Birth Date", kind: "date" },
  { name: "birthPlace", label: "Birth Place" },
  { name: "birthCountry", label: "Birth Country" },
  { name: "nationality", label: "Nationality" },
  { name: "height", label: "Height", kind: "number" },
  { name: "weight", label: "Weight", kind: "number" },
  { name: "position", label: "Position" },
  { name: "number", label: "Number", kind: "number" },
  { name: "photoUrl", label: "Photo URL" },
];

const fixtureFields: FieldConfig[] = [
  { name: "fixtureDate", label: "경기 일시", kind: "datetime", help: "한국 시간(KST) 기준으로 입력됩니다." },
  { name: "referee", label: "Referee" },
  { name: "venueId", label: "Venue ID", kind: "number" },
  { name: "venueName", label: "Venue Name" },
  { name: "venueCity", label: "Venue City" },
  { name: "homeFormation", label: "Home Formation" },
  { name: "awayFormation", label: "Away Formation" },
  { name: "homeCoachName", label: "Home Coach" },
  { name: "awayCoachName", label: "Away Coach" },
  { name: "homePlayerColorPrimary", label: "Home Player Primary", preview: "color" },
  { name: "homePlayerColorNumber", label: "Home Player Number", preview: "color" },
  { name: "homePlayerColorBorder", label: "Home Player Border", preview: "color" },
  { name: "homeGoalkeeperColorPrimary", label: "Home GK Primary", preview: "color" },
  { name: "homeGoalkeeperColorNumber", label: "Home GK Number", preview: "color" },
  { name: "homeGoalkeeperColorBorder", label: "Home GK Border", preview: "color" },
  { name: "awayPlayerColorPrimary", label: "Away Player Primary", preview: "color" },
  { name: "awayPlayerColorNumber", label: "Away Player Number", preview: "color" },
  { name: "awayPlayerColorBorder", label: "Away Player Border", preview: "color" },
  { name: "awayGoalkeeperColorPrimary", label: "Away GK Primary", preview: "color" },
  { name: "awayGoalkeeperColorNumber", label: "Away GK Number", preview: "color" },
  { name: "awayGoalkeeperColorBorder", label: "Away GK Border", preview: "color" },
];

const eventFields: FieldConfig[] = [
  { name: "teamId", label: "Team", kind: "select" },
  { name: "playerId", label: "Player", kind: "select" },
  { name: "assistPlayerId", label: "Assist Player", kind: "select" },
  { name: "elapsed", label: "Elapsed", kind: "number", min: 0, max: 90 },
  { name: "extra", label: "Extra", kind: "number", min: 0, max: 20 },
  { name: "eventType", label: "Type", kind: "select" },
  { name: "eventDetail", label: "Detail", kind: "select" },
  { name: "comments", label: "Comments" },
];

const eventTypeOptions: FieldOption[] = ["Goal", "Card", "Subst", "Var"].map((value) => ({ value, label: value }));

const eventDetailOptionsByType: Record<string, FieldOption[]> = {
  Goal: ["Normal Goal", "Own Goal", "Penalty", "Missed Penalty"].map((value) => ({ value, label: value })),
  Card: ["Yellow Card", "Red card"].map((value) => ({ value, label: value })),
  Subst: Array.from({ length: 10 }, (_, index) => {
    const value = `Substitution ${index + 1}`;
    return { value, label: value };
  }),
  Var: ["Goal cancelled", "Penalty confirmed"].map((value) => ({ value, label: value })),
};

const lineupFields: FieldConfig[] = [
  { name: "position", label: "Position" },
  { name: "grid", label: "Grid" },
  { name: "starter", label: "Starter", kind: "boolean" },
];

const teamStatFields: FieldConfig[] = [
  "shotsOnGoal",
  "shotsOffGoal",
  "totalShots",
  "blockedShots",
  "shotsInsideBox",
  "shotsOutsideBox",
  "fouls",
  "cornerKicks",
  "offsides",
  "ballPossession",
  "yellowCards",
  "redCards",
  "goalkeeperSaves",
  "totalPasses",
  "passesAccurate",
].map((name) => ({ name, label: labelize(name), kind: "number" as const })).concat({
  name: "expectedGoals",
  label: "Expected Goals",
  kind: "number",
});

const playerStatFields: FieldConfig[] = [
  { name: "minutesPlayed", label: "Minutes", kind: "number" },
  { name: "rating", label: "Rating", kind: "number" },
  { name: "captain", label: "Captain", kind: "boolean" },
  { name: "substitute", label: "Substitute", kind: "boolean" },
  ...[
    "goals",
    "assists",
    "conceded",
    "saves",
    "shotsTotal",
    "shotsOnTarget",
    "passesTotal",
    "passesKey",
    "passesAccurate",
    "tacklesTotal",
    "blocks",
    "interceptions",
    "duelsTotal",
    "duelsWon",
    "dribblesAttempts",
    "dribblesSuccess",
    "dribblesPast",
    "foulsDrawn",
    "foulsCommitted",
    "yellowCards",
    "redCards",
    "offsides",
    "penaltyWon",
    "penaltyCommitted",
    "penaltyScored",
    "penaltyMissed",
    "penaltySaved",
  ].map((name) => ({ name, label: labelize(name), kind: "number" as const })),
];

const syncTasks = [
  { task: "seasons", label: "Seasons" },
  { task: "teams", label: "Teams" },
  { task: "standings", label: "Standings" },
  { task: "fixtures", label: "Fixtures" },
  { task: "fixture-details", label: "Season Details" },
  { task: "players", label: "Players" },
  { task: "injuries", label: "Injuries" },
];

export function AdminPage({ authState }: AdminPageProps) {
  const [activeTab, setActiveTab] = useState<AdminTab>("team");
  const [teamKeyword, setTeamKeyword] = useState("");
  const [teams, setTeams] = useState<TeamAdmin[]>([]);
  const [teamSelection, setTeamSelection] = useState<TeamSelectionState>({
    teamId: null,
    status: "idle",
    detail: null,
  });
  const [playerKeyword, setPlayerKeyword] = useState("");
  const [players, setPlayers] = useState<PlayerAdmin[]>([]);
  const [selectedPlayerTeamId, setSelectedPlayerTeamId] = useState<number | null>(null);
  const [teamPlayers, setTeamPlayers] = useState<PlayerSummary[]>([]);
  const [teamPlayerStatus, setTeamPlayerStatus] = useState<FixtureListStatus>("idle");
  const [playerSelection, setPlayerSelection] = useState<PlayerSelectionState>({
    playerId: null,
    status: "idle",
    detail: null,
  });
  const [fixtureTeams, setFixtureTeams] = useState<FixtureTeamOption[]>([]);
  const [fixtureTeamStatus, setFixtureTeamStatus] = useState<FixtureListStatus>("idle");
  const [selectedFixtureTeamId, setSelectedFixtureTeamId] = useState<number | null>(null);
  const [fixtures, setFixtures] = useState<FixtureSummaryAdmin[]>([]);
  const [fixtureListStatus, setFixtureListStatus] = useState<FixtureListStatus>("idle");
  const [fixtureSelection, setFixtureSelection] = useState<FixtureSelectionState>({
    fixtureId: null,
    status: "idle",
    detail: null,
  });
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [syncFixtureId, setSyncFixtureId] = useState("");
  const [syncMessage, setSyncMessage] = useState("");
  const [syncingTask, setSyncingTask] = useState<string | null>(null);
  const [syncCooldownUntil, setSyncCooldownUntil] = useState<Record<string, number>>({});
  const [syncClock, setSyncClock] = useState(Date.now());
  const [syncStatuses, setSyncStatuses] = useState<SyncStatus[]>([]);
  const [seasonCoverages, setSeasonCoverages] = useState<LeagueSeasonCoverage[]>([]);
  const [coverageStatus, setCoverageStatus] = useState<CoverageStatus>("loading");
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [auditPage, setAuditPage] = useState(0);
  const [auditTotalPages, setAuditTotalPages] = useState(0);
  const [auditTotalElements, setAuditTotalElements] = useState(0);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const teamRequestIdRef = useRef(0);
  const playerRequestIdRef = useRef(0);
  const teamPlayerRequestIdRef = useRef(0);
  const fixtureTeamRequestIdRef = useRef(0);
  const fixtureListRequestIdRef = useRef(0);
  const fixtureRequestIdRef = useRef(0);
  const fixtureEditorRef = useRef<HTMLDivElement>(null);
  const selectedTeam = teamSelection.status === "ready" ? teamSelection.detail : null;
  const loadingTeamId = teamSelection.status === "loading" ? teamSelection.teamId : null;
  const selectedPlayer = playerSelection.status === "ready" ? playerSelection.detail : null;
  const loadingPlayerId = playerSelection.status === "loading" ? playerSelection.playerId : null;
  const selectedFixture = fixtureSelection.status === "ready" ? fixtureSelection.detail : null;
  const loadingFixtureId = fixtureSelection.status === "loading" ? fixtureSelection.fixtureId : null;

  useEffect(() => {
    if (authState.authStatus === "authenticated" && authState.currentUser?.role === "ADMIN") {
      void loadAuditLogs(0, setLogs, setAuditPage, setAuditTotalPages, setAuditTotalElements);
      void loadSyncStatuses(authState.season, setSyncStatuses);
      void loadSeasonCoverages(setSeasonCoverages, setCoverageStatus);
    }
  }, [authState.authStatus, authState.currentUser?.role, authState.season]);

  useEffect(() => {
    if (!Object.values(syncCooldownUntil).some((until) => until > syncClock)) {
      return;
    }
    const timerId = window.setInterval(() => setSyncClock(Date.now()), 1000);
    return () => window.clearInterval(timerId);
  }, [syncCooldownUntil, syncClock]);

  useEffect(() => {
    if (
      authState.authStatus === "authenticated"
      && authState.currentUser?.role === "ADMIN"
    ) {
      void loadFixtureTeams(authState.season);
    }
  }, [authState.authStatus, authState.currentUser?.role, authState.season]);

  useEffect(() => {
    if (fixtureSelection.status !== "ready") {
      return;
    }
    const frameId = window.requestAnimationFrame(() => {
      fixtureEditorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [fixtureSelection.status, fixtureSelection.fixtureId]);

  if (authState.authStatus === "checking") {
    return <section className="panel admin-page-panel">권한을 확인하는 중입니다.</section>;
  }

  if (authState.currentUser?.role !== "ADMIN") {
    return <Navigate to="/league/overview" replace />;
  }

  async function searchTeams(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<TeamAdmin[]>(`/api/v1/admin/teams?keyword=${encodeURIComponent(teamKeyword)}`);
      setTeams(result);
    });
  }

  async function selectTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = teamRequestIdRef.current + 1;
    teamRequestIdRef.current = requestId;
    setTeamSelection({ teamId, status: "loading", detail: null });
    setError("");
    setMessage("");
    try {
      const detail = await adminGet<TeamAdmin>(`/api/v1/admin/teams/${teamId}`);
      if (teamRequestIdRef.current === requestId && detail.teamId === teamId) {
        setTeamSelection({ teamId, status: "ready", detail });
      }
    } catch (nextError) {
      if (teamRequestIdRef.current === requestId) {
        setTeamSelection({ teamId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "팀 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function searchPlayers(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<PlayerAdmin[]>(`/api/v1/admin/players?keyword=${encodeURIComponent(playerKeyword)}`);
      setPlayers(result);
    });
  }

  async function selectPlayer(playerId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = playerRequestIdRef.current + 1;
    playerRequestIdRef.current = requestId;
    setPlayerSelection({ playerId, status: "loading", detail: null });
    setError("");
    setMessage("");
    try {
      const detail = await adminGet<PlayerAdmin>(`/api/v1/admin/players/${playerId}`);
      if (playerRequestIdRef.current === requestId && detail.playerId === playerId) {
        setPlayerSelection({ playerId, status: "ready", detail });
      }
    } catch (nextError) {
      if (playerRequestIdRef.current === requestId) {
        setPlayerSelection({ playerId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "선수 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function loadFixtureTeams(season: number) {
    const requestId = fixtureTeamRequestIdRef.current + 1;
    fixtureTeamRequestIdRef.current = requestId;
    fixtureListRequestIdRef.current += 1;
    fixtureRequestIdRef.current += 1;
    teamPlayerRequestIdRef.current += 1;
    setFixtureTeamStatus("loading");
    setTeamSelection({ teamId: null, status: "idle", detail: null });
    setSelectedPlayerTeamId(null);
    setTeamPlayers([]);
    setTeamPlayerStatus("idle");
    setPlayerSelection({ playerId: null, status: "idle", detail: null });
    setSelectedFixtureTeamId(null);
    setFixtures([]);
    setFixtureListStatus("idle");
    setFixtureSelection({ fixtureId: null, status: "idle", detail: null });
    try {
      const result = await adminGet<FixtureTeamOption[]>(`/api/v1/admin/fixture-teams?season=${season}`);
      if (fixtureTeamRequestIdRef.current === requestId) {
        setFixtureTeams(result);
        setFixtureTeamStatus("ready");
      }
    } catch (nextError) {
      if (fixtureTeamRequestIdRef.current === requestId) {
        setFixtureTeams([]);
        setFixtureTeamStatus("error");
        setError(nextError instanceof Error ? nextError.message : "시즌 참가 팀을 불러오지 못했습니다.");
      }
    }
  }

  async function selectPlayerTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = teamPlayerRequestIdRef.current + 1;
    teamPlayerRequestIdRef.current = requestId;
    playerRequestIdRef.current += 1;
    setSelectedPlayerTeamId(teamId);
    setTeamPlayers([]);
    setTeamPlayerStatus("loading");
    setPlayerSelection({ playerId: null, status: "idle", detail: null });
    setError("");
    setMessage("");
    try {
      const result = await fetchTeamPlayers(teamId, authState.season);
      if (teamPlayerRequestIdRef.current === requestId) {
        setTeamPlayers(result);
        setTeamPlayerStatus("ready");
      }
    } catch (nextError) {
      if (teamPlayerRequestIdRef.current === requestId) {
        setTeamPlayerStatus("error");
        setError(nextError instanceof Error ? nextError.message : "팀 선수 목록을 불러오지 못했습니다.");
      }
    }
  }

  async function selectFixtureTeam(teamId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = fixtureListRequestIdRef.current + 1;
    fixtureListRequestIdRef.current = requestId;
    fixtureRequestIdRef.current += 1;
    setSelectedFixtureTeamId(teamId);
    setFixtures([]);
    setFixtureListStatus("loading");
    setFixtureSelection({ fixtureId: null, status: "idle", detail: null });
    setError("");
    setMessage("");
    try {
      const response = await fetchFixtures({ season: authState.season, teamId, size: 100 });
      if (fixtureListRequestIdRef.current === requestId) {
        setFixtures(response.content ?? []);
        setFixtureListStatus("ready");
      }
    } catch (nextError) {
      if (fixtureListRequestIdRef.current === requestId) {
        setFixtureListStatus("error");
        setError(nextError instanceof Error ? nextError.message : "팀 경기 목록을 불러오지 못했습니다.");
      }
    }
  }

  async function selectFixture(fixtureId: number) {
    if (savingKey !== null) {
      return;
    }
    const requestId = fixtureRequestIdRef.current + 1;
    fixtureRequestIdRef.current = requestId;
    setFixtureSelection({ fixtureId, status: "loading", detail: null });
    setError("");
    setMessage("");
    try {
      const detail = await adminGet<FixtureDetailAdmin>(`/api/v1/admin/fixtures/${fixtureId}`);
      if (fixtureRequestIdRef.current === requestId && detail.fixture.fixtureId === fixtureId) {
        setFixtureSelection({ fixtureId, status: "ready", detail });
      }
    } catch (nextError) {
      if (fixtureRequestIdRef.current === requestId) {
        setFixtureSelection({ fixtureId, status: "error", detail: null });
        setError(nextError instanceof Error ? nextError.message : "경기 정보를 불러오지 못했습니다.");
      }
    }
  }

  async function reloadAuditLogs(page = auditPage) {
    await loadAuditLogs(page, setLogs, setAuditPage, setAuditTotalPages, setAuditTotalElements);
  }

  async function saveTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTeam || savingKey !== null) {
      return;
    }
    const teamId = selectedTeam.teamId;
    const body = formBody(event.currentTarget, teamFields);
    await runSave(`team:${teamId}`, async () => {
      const updated = await adminJson<TeamAdmin>(`/api/v1/admin/teams/${teamId}`, "PUT", body);
      clearApiMemoryCache();
      setTeamSelection({ teamId, status: "ready", detail: updated });
      setMessage("팀 정보를 저장했습니다.");
      await reloadAuditLogs();
    });
  }

  async function savePlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPlayer || savingKey !== null) {
      return;
    }
    const playerId = selectedPlayer.playerId;
    const body = formBody(event.currentTarget, playerFields);
    await runSave(`player:${playerId}`, async () => {
      const updated = await adminJson<PlayerAdmin>(`/api/v1/admin/players/${playerId}`, "PUT", body);
      clearApiMemoryCache();
      setPlayerSelection({ playerId, status: "ready", detail: updated });
      setMessage("선수 정보를 저장했습니다.");
      await reloadAuditLogs();
    });
  }

  async function saveFixture(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFixture) {
      return;
    }
    const fixtureId = selectedFixture.fixture.fixtureId;
    await saveFixtureSection(fixtureId, `/api/v1/admin/fixtures/${fixtureId}`, event.currentTarget, fixtureFields, "경기 기본 정보를 저장했습니다.");
  }

  async function saveFixtureSection(fixtureId: number, url: string, form: HTMLFormElement, fields: FieldConfig[], successMessage: string, method = "PUT") {
    await saveFixturePayload(fixtureId, url, formBody(form, fields), successMessage, method);
  }

  async function saveFixturePayload(fixtureId: number, url: string, body: Record<string, unknown>, successMessage: string, method = "PUT") {
    if (!canSaveFixture(fixtureId) || savingKey !== null) {
      setError("현재 열려 있는 경기와 저장 대상이 일치하지 않습니다. 경기를 다시 선택해주세요.");
      return;
    }
    await runSave(`fixture:${fixtureId}`, async () => {
      if (!canSaveFixture(fixtureId)) {
        setError("현재 열려 있는 경기와 저장 대상이 일치하지 않습니다. 경기를 다시 선택해주세요.");
        return;
      }
      const updated = await adminJson<FixtureDetailAdmin>(url, method, body);
      clearApiMemoryCache();
      if (canSaveFixture(fixtureId) && updated.fixture.fixtureId === fixtureId) {
        setFixtureSelection({ fixtureId, status: "ready", detail: updated });
      }
      setMessage(successMessage);
      await reloadAuditLogs();
    });
  }

  function canSaveFixture(fixtureId: number) {
    return fixtureSelection.status === "ready"
      && fixtureSelection.fixtureId === fixtureId
      && fixtureSelection.detail?.fixture.fixtureId === fixtureId;
  }

  async function runSave(key: string, action: () => Promise<void>) {
    if (savingKey !== null) {
      return;
    }
    setSavingKey(key);
    try {
      await runRequest(action);
    } finally {
      setSavingKey(null);
    }
  }

  function manualSyncCooldownKey(task: string) {
    if (task === "seasons") {
      return task;
    }
    return `${task}:${authState.season}`;
  }

  function fixtureDetailCooldownKey(fixtureId: string) {
    return `fixture-detail:${fixtureId}`;
  }

  function syncCooldownSeconds(key: string) {
    return Math.max(0, Math.ceil(((syncCooldownUntil[key] ?? 0) - syncClock) / 1000));
  }

  function markSyncCooldown(key: string) {
    const now = Date.now();
    setSyncClock(now);
    setSyncCooldownUntil((current) => ({
      ...current,
      [key]: now + MANUAL_SYNC_COOLDOWN_MS,
    }));
  }

  async function runSync(task: string) {
    const availability = syncAvailability(task, authState.season, seasonCoverages, coverageStatus);
    if (!availability.enabled) {
      setSyncMessage(availability.message);
      return;
    }
    const cooldownKey = manualSyncCooldownKey(task);
    const cooldownSeconds = syncCooldownSeconds(cooldownKey);
    if (cooldownSeconds > 0) {
      setSyncMessage(`${cooldownSeconds}초 후 다시 요청할 수 있습니다.`);
      return;
    }
    const league = 39;
    const season = authState.season;
    const urls: Record<string, string | null> = {
      seasons: `/api/v1/admin/sync/seasons?league=${league}`,
      teams: `/api/v1/admin/sync/teams?league=${league}&season=${season}`,
      standings: `/api/v1/admin/sync/standings?league=${league}&season=${season}`,
      fixtures: `/api/v1/admin/sync/fixtures?league=${league}&season=${season}`,
      "fixture-details": `/api/v1/admin/sync/fixture-details?season=${season}`,
      players: `/api/v1/admin/sync/players?league=${league}&season=${season}&delayMs=7000`,
      injuries: `/api/v1/admin/sync/injuries?league=${league}&season=${season}`,
    };
    const url = urls[task];
    if (!url) {
      setSyncMessage("Fixture Detail 동기화는 먼저 경기를 선택해야 합니다.");
      return;
    }
    markSyncCooldown(cooldownKey);
    setSyncingTask(task);
    try {
      await runRequest(async () => {
        const result = await adminJson<{ message: string }>(url, "POST");
        clearApiMemoryCache();
        setSyncMessage(result.message);
        if (task === "seasons") {
          await loadSeasonCoverages(setSeasonCoverages, setCoverageStatus);
        }
        await loadSyncStatuses(authState.season, setSyncStatuses);
        await reloadAuditLogs();
      });
    } finally {
      setSyncingTask(null);
    }
  }

  async function syncFixtureDetail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fixtureId = syncFixtureId.trim();
    if (!fixtureId) {
      setSyncMessage("Fixture ID를 입력해주세요.");
      return;
    }
    const cooldownKey = fixtureDetailCooldownKey(fixtureId);
    const cooldownSeconds = syncCooldownSeconds(cooldownKey);
    if (cooldownSeconds > 0) {
      setSyncMessage(`${cooldownSeconds}초 후 다시 요청할 수 있습니다.`);
      return;
    }
    markSyncCooldown(cooldownKey);
    setSyncingTask("fixture-detail");
    try {
      await runRequest(async () => {
        const result = await adminJson<{ message: string }>(`/api/v1/admin/sync/fixture-details/${encodeURIComponent(fixtureId)}`, "POST");
        clearApiMemoryCache();
        setSyncMessage(result.message);
        await reloadAuditLogs();
      });
    } finally {
      setSyncingTask(null);
    }
  }

  const syncStatusByTask = new Map(syncStatuses.map((status) => [status.task, status]));
  const selectedCoverage = seasonCoverages.find((coverage) => coverage.seasonYear === authState.season) ?? null;
  const fixtureDetailCooldownSeconds = syncFixtureId.trim() ? syncCooldownSeconds(fixtureDetailCooldownKey(syncFixtureId.trim())) : 0;

  async function runRequest(action: () => Promise<void>) {
    setError("");
    setMessage("");
    try {
      await action();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : "요청을 처리하지 못했습니다.");
    }
  }

  return (
    <section className="admin-page">
      <div className="admin-page-heading">
        <div>
          <p className="eyebrow">Admin</p>
          <h2>관리자 페이지</h2>
        </div>
        <span className="status-pill">{authState.currentUser.email}</span>
      </div>

      {message ? <div className="notice">{message}</div> : null}
      {error ? <div className="notice error">{error}</div> : null}

      <nav className="admin-tabs" aria-label="관리 메뉴">
        <button
          type="button"
          className={`admin-tab${activeTab === "team" ? " active" : ""}`}
          onClick={() => setActiveTab("team")}
        >
          팀
        </button>
        <button
          type="button"
          className={`admin-tab${activeTab === "player" ? " active" : ""}`}
          onClick={() => setActiveTab("player")}
        >
          선수
        </button>
        <button
          type="button"
          className={`admin-tab${activeTab === "fixture" ? " active" : ""}`}
          onClick={() => setActiveTab("fixture")}
        >
          경기
        </button>
      </nav>

      {activeTab === "team" ? (
        <EditorPanel title="Team Editor" eyebrow="Teams">
          <label className="admin-fixture-team-select">
            <span>{authState.season} 시즌 팀</span>
            <select
              value={teamSelection.teamId ?? ""}
              onChange={(event) => {
                const teamId = Number(event.currentTarget.value);
                if (Number.isFinite(teamId) && teamId > 0) {
                  void selectTeam(teamId);
                }
              }}
              disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
            >
              <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
              {fixtureTeams.map((team) => (
                <option key={team.teamId} value={team.teamId}>{team.name ?? `#${team.teamId}`}</option>
              ))}
            </select>
          </label>
          <p className="muted admin-sync-message">목록에 없는 팀은 아래 검색으로 찾을 수 있습니다.</p>
          <SearchRow value={teamKeyword} placeholder="Search team" onChange={setTeamKeyword} onSubmit={searchTeams} />
          <ResultList
            items={teams}
            getKey={(team) => team.teamId}
            render={(team) => `${team.name ?? "-"} #${team.teamId}`}
            onSelect={(team) => void selectTeam(team.teamId)}
            disabled={savingKey !== null}
          />
          {loadingTeamId !== null ? <p className="muted admin-sync-message">팀 정보를 불러오는 중입니다.</p> : null}
          {selectedTeam ? (
            <AdminForm
              title={selectedTeam.name ?? "Team"}
              fields={teamFields}
              value={selectedTeam}
              overrides={selectedTeam.manualOverrides}
              submitLabel="Save Team"
              onSubmit={saveTeam}
              disabled={savingKey !== null}
            />
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "player" ? (
        <EditorPanel title="Player Editor" eyebrow="Players">
          <div className="admin-fixture-browser">
            <label className="admin-fixture-team-select">
              <span>{authState.season} 시즌 팀</span>
              <select
                value={selectedPlayerTeamId ?? ""}
                onChange={(event) => {
                  const teamId = Number(event.currentTarget.value);
                  if (Number.isFinite(teamId) && teamId > 0) {
                    void selectPlayerTeam(teamId);
                  }
                }}
                disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
              >
                <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
                {fixtureTeams.map((team) => (
                  <option key={team.teamId} value={team.teamId}>{team.name ?? `#${team.teamId}`}</option>
                ))}
              </select>
            </label>
            {teamPlayerStatus === "loading" ? <p className="muted admin-sync-message">선수 목록을 불러오는 중입니다.</p> : null}
            {teamPlayerStatus === "error" ? <p className="muted admin-sync-message">선수 목록을 불러오지 못했습니다.</p> : null}
            {teamPlayerStatus === "ready" && teamPlayers.length === 0 ? <p className="muted admin-sync-message">선택한 팀의 선수가 없습니다.</p> : null}
            {teamPlayers.length > 0 ? (
              <ResultList
                items={teamPlayers}
                getKey={(player) => player.playerId}
                render={(player) => `${player.playerName ?? "-"} #${player.playerId}`}
                onSelect={(player) => void selectPlayer(player.playerId)}
                disabled={savingKey !== null}
              />
            ) : null}
          </div>
          <p className="muted admin-sync-message">현재 시즌 팀 목록에 없는 선수는 아래 검색으로 찾을 수 있습니다.</p>
          <SearchRow value={playerKeyword} placeholder="Search player" onChange={setPlayerKeyword} onSubmit={searchPlayers} />
          <ResultList
            items={players}
            getKey={(player) => player.playerId}
            render={(player) => `${player.name ?? "-"} #${player.playerId}`}
            onSelect={(player) => void selectPlayer(player.playerId)}
            disabled={savingKey !== null}
          />
          {loadingPlayerId !== null ? <p className="muted admin-sync-message">선수 정보를 불러오는 중입니다.</p> : null}
          {selectedPlayer ? (
            <AdminForm
              title={selectedPlayer.name ?? "Player"}
              fields={playerFields}
              value={selectedPlayer}
              overrides={selectedPlayer.manualOverrides}
              submitLabel="Save Player"
              onSubmit={savePlayer}
              disabled={savingKey !== null}
            />
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "fixture" ? (
      <EditorPanel title="Fixture Editor" eyebrow="Fixtures">
        <div className="admin-fixture-browser">
          <label className="admin-fixture-team-select">
            <span>{authState.season} 시즌 팀</span>
            <select
              value={selectedFixtureTeamId ?? ""}
              onChange={(event) => {
                const teamId = Number(event.currentTarget.value);
                if (Number.isFinite(teamId) && teamId > 0) {
                  void selectFixtureTeam(teamId);
                }
              }}
              disabled={fixtureTeamStatus !== "ready" || savingKey !== null}
            >
              <option value="">{fixtureTeamStatus === "loading" ? "팀 목록 불러오는 중..." : "팀을 선택하세요"}</option>
              {fixtureTeams.map((team) => (
                <option key={team.teamId} value={team.teamId}>{team.name ?? `#${team.teamId}`}</option>
              ))}
            </select>
          </label>
          {fixtureTeamStatus === "error" ? <p className="muted admin-sync-message">팀 목록을 불러오지 못했습니다.</p> : null}
          {fixtureListStatus === "loading" ? <p className="muted admin-sync-message">경기 목록을 불러오는 중입니다.</p> : null}
          {fixtureListStatus === "ready" && fixtures.length === 0 ? <p className="muted admin-sync-message">선택한 팀의 경기가 없습니다.</p> : null}
          {fixtures.length > 0 ? (
            <div className="admin-fixture-list" aria-label="선택한 팀의 경기 목록">
              {fixtures.map((fixture) => (
                <button
                  type="button"
                  key={fixture.fixtureId}
                  className={fixtureSelection.fixtureId === fixture.fixtureId ? "active" : ""}
                  onClick={() => void selectFixture(fixture.fixtureId)}
                  disabled={savingKey !== null}
                >
                  <span className="admin-fixture-list-meta">
                    <time>{formatFixtureDate(fixture.fixtureDate)}</time>
                    <span>{fixture.round ? `${fixture.round}R` : "라운드 미정"}</span>
                    <span>{fixture.fixtureStatus ?? "-"}</span>
                  </span>
                  <strong>{fixture.homeTeamName ?? "-"} <em>{formatFixtureScore(fixture)}</em> {fixture.awayTeamName ?? "-"}</strong>
                  <small>Fixture #{fixture.fixtureId}</small>
                </button>
              ))}
            </div>
          ) : null}
        </div>
        {loadingFixtureId !== null ? <p className="muted admin-sync-message">경기 정보를 불러오는 중입니다.</p> : null}
        {selectedFixture ? (
          <div ref={fixtureEditorRef}>
            <FixtureEditor
              key={selectedFixture.fixture.fixtureId}
              detail={selectedFixture}
              onSaveFixture={saveFixture}
              onSaveSection={saveFixtureSection}
              onSavePayload={saveFixturePayload}
              disabled={savingKey !== null}
            />
          </div>
        ) : null}
      </EditorPanel>
      ) : null}

      <details className="panel admin-page-panel admin-utility-section">
        <summary>
          <span>
            <span className="eyebrow">API-Football</span>
            <strong>Manual Sync</strong>
          </span>
        </summary>
        <div className="admin-sync-actions">
          {syncTasks.map((item) => {
            const status = syncStatusByTask.get(item.task);
            const isSyncing = syncingTask === item.task;
            const availability = syncAvailability(item.task, authState.season, seasonCoverages, coverageStatus);
            const cooldownSeconds = syncCooldownSeconds(manualSyncCooldownKey(item.task));
            return (
              <div className="admin-sync-action" key={item.task}>
                <button type="button" onClick={() => void runSync(item.task)} disabled={syncingTask !== null || cooldownSeconds > 0 || !availability.enabled}>
                  {isSyncing ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
                  {item.label}
                </button>
                <span>Last sync: {formatDateTime(status?.lastSyncedAt ?? null)}</span>
                {cooldownSeconds > 0 ? <span className="admin-sync-warning">{cooldownSeconds}초 후 재요청 가능</span> : null}
                {!availability.enabled ? <span className="admin-sync-warning">{availability.message}</span> : null}
              </div>
            );
          })}
        </div>
        {coverageStatus === "error" ? (
          <p className="muted admin-sync-message">시즌 지원 범위를 불러오지 못해 수동 싱크 요청을 잠시 막았습니다.</p>
        ) : null}
        {coverageStatus === "ready" && !selectedCoverage ? (
          <p className="muted admin-sync-message">선택한 시즌은 API-Football coverage 정보가 없어 수동 싱크를 요청할 수 없습니다.</p>
        ) : null}
        <form className="admin-fixture-sync-form" onSubmit={syncFixtureDetail}>
          <label>
            <span>Fixture ID</span>
            <input
              type="number"
              min={1}
              inputMode="numeric"
              value={syncFixtureId}
              onChange={(event) => setSyncFixtureId(event.currentTarget.value)}
              placeholder="Fixture ID"
            />
          </label>
          <button type="submit" disabled={syncingTask !== null || fixtureDetailCooldownSeconds > 0}>
            {syncingTask === "fixture-detail" ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
            Update Fixture Detail
          </button>
        </form>
        {fixtureDetailCooldownSeconds > 0 ? <p className="muted admin-sync-message">{fixtureDetailCooldownSeconds}초 후 Fixture Detail을 다시 요청할 수 있습니다.</p> : null}
        {syncMessage ? <p className="muted admin-sync-message">{syncMessage}</p> : null}
      </details>

      <details className="panel admin-page-panel admin-utility-section">
        <summary>
          <span>
            <span className="eyebrow">Audit</span>
            <strong>Recent Admin Logs</strong>
          </span>
          <button type="button" className="section-retry-button" onClick={() => void reloadAuditLogs()}>
            Refresh
          </button>
        </summary>
        <div className="admin-log-list">
          {logs.map((log) => (
            <article className="admin-log-item" key={log.id}>
              <span className="status-pill">{log.type}</span>
              <div>
                <strong>{log.message}</strong>
                {log.details ? <p className="muted">{log.details}</p> : null}
                <p className="muted">
                  {log.adminEmail ?? "-"} · {formatDateTime(log.createdAt)}
                </p>
              </div>
              <span className="status-pill">{log.success ? "OK" : "FAIL"}</span>
            </article>
          ))}
        </div>
        <div className="admin-pagination">
          <button type="button" disabled={auditPage <= 0} onClick={() => void reloadAuditLogs(auditPage - 1)}>
            Previous
          </button>
          <span>
            Page {auditTotalPages === 0 ? 0 : auditPage + 1} / {auditTotalPages} · {auditTotalElements} logs
          </span>
          <button type="button" disabled={auditPage + 1 >= auditTotalPages} onClick={() => void reloadAuditLogs(auditPage + 1)}>
            Next
          </button>
        </div>
      </details>
    </section>
  );
}

function EditorPanel({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow: string;
  children: React.ReactNode;
}) {
  return (
    <section className="panel admin-page-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </div>
      <div className="admin-search-form">{children}</div>
    </section>
  );
}

function SearchRow({
  value,
  placeholder,
  onChange,
  onSubmit,
}: {
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="admin-search-row" onSubmit={onSubmit}>
      <input
        type="search"
        value={value}
        placeholder={placeholder}
        maxLength={ADMIN_SEARCH_KEYWORD_MAX_LENGTH}
        onChange={(event) => onChange(event.target.value)}
      />
      <button type="submit">Search</button>
    </form>
  );
}

function ResultList<T>({
  items,
  getKey,
  render,
  onSelect,
  disabled,
}: {
  items: T[];
  getKey: (item: T) => string | number;
  render: (item: T) => string;
  onSelect: (item: T) => void;
  disabled?: boolean;
}) {
  if (!items.length) {
    return null;
  }

  return (
    <div className="admin-result-list">
      {items.map((item) => (
        <button type="button" key={getKey(item)} onClick={() => onSelect(item)} disabled={disabled}>
          {render(item)}
        </button>
      ))}
    </div>
  );
}

function AdminForm({
  title,
  fields,
  value,
  overrides,
  submitLabel,
  onSubmit,
  disabled,
}: {
  title: string;
  fields: FieldConfig[];
  value: Record<string, unknown>;
  overrides?: AdminOverride[];
  submitLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  disabled?: boolean;
}) {
  const overrideSet = new Set((overrides ?? []).map((override) => override.fieldName));
  return (
    <form className="admin-edit-form" onSubmit={onSubmit}>
      <h3>{title}</h3>
      <div className="admin-form-grid">
        {fields.map((field) => (
          <AdminField key={field.name} field={field} value={value[field.name]} overridden={overrideSet.has(field.name)} />
        ))}
      </div>
      <button type="submit" disabled={disabled}>{submitLabel}</button>
    </form>
  );
}

function FixtureEditor({
  detail,
  onSaveFixture,
  onSaveSection,
  onSavePayload,
  disabled,
}: {
  detail: FixtureDetailAdmin;
  onSaveFixture: (event: FormEvent<HTMLFormElement>) => void;
  onSaveSection: (fixtureId: number, url: string, form: HTMLFormElement, fields: FieldConfig[], successMessage: string, method?: string) => Promise<void>;
  onSavePayload: (fixtureId: number, url: string, body: Record<string, unknown>, successMessage: string, method?: string) => Promise<void>;
  disabled?: boolean;
}) {
  const fixtureId = detail.fixture.fixtureId;
  const [addingEvent, setAddingEvent] = useState(false);
  const [newEventValue, setNewEventValue] = useState(() => newFixtureEventValue(detail));

  useEffect(() => {
    if (!addingEvent) {
      setNewEventValue(newFixtureEventValue(detail));
    }
  }, [addingEvent, detail]);

  return (
    <div className="fixture-admin-editor">
      <NestedAdminSection title="Fixture Info" count={1}>
        <AdminForm
          title={`${detail.fixture.homeTeamName ?? "-"} vs ${detail.fixture.awayTeamName ?? "-"}`}
          fields={fixtureFields}
          value={detail.fixture}
          submitLabel="Save Fixture"
          onSubmit={onSaveFixture}
          disabled={disabled}
        />
      </NestedAdminSection>
      <NestedAdminSection title="Events" count={detail.events.length}>
        <button type="button" className="admin-add-button" onClick={() => setAddingEvent((current) => !current)} disabled={disabled}>
          {addingEvent ? "Cancel New Event" : "Add Event"}
        </button>
        {addingEvent ? (
          <details className="nested-admin-item" open>
            <summary>New Event</summary>
            <EventAdminForm
              title="New Event"
              detail={detail}
              value={newEventValue}
              submitLabel="Create Event"
              onSubmit={(body) => {
                void onSavePayload(
                  fixtureId,
                  `/api/v1/admin/fixtures/${fixtureId}/events`,
                  body,
                  "이벤트를 추가했습니다.",
                  "POST",
                ).then(() => setAddingEvent(false));
              }}
              disabled={disabled}
            />
          </details>
        ) : null}
        {detail.events.map((event) => (
          <details className="nested-admin-item" key={event.eventSequence}>
            <summary>#{event.eventSequence} {event.eventType ?? ""} {event.playerName ?? ""}</summary>
            <EventAdminForm
              title={`#${event.eventSequence} ${event.eventType ?? ""} ${event.playerName ?? ""}`}
              detail={detail}
              value={event}
              submitLabel="Save Event"
              onSubmit={(body) => {
                void onSavePayload(
                  fixtureId,
                  `/api/v1/admin/fixtures/${fixtureId}/events/${event.eventSequence}`,
                  body,
                  "이벤트를 저장했습니다.",
                );
              }}
              disabled={disabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="Lineups" count={detail.lineups.length}>
        {detail.lineups.map((lineup) => (
          <details className="nested-admin-item" key={`${lineup.teamId}-${lineup.playerId}`}>
            <summary>{lineup.teamName ?? "-"} · {lineup.playerName ?? "-"}</summary>
            <AdminForm
            title={`${lineup.teamName ?? "-"} · ${lineup.playerName ?? "-"}`}
            fields={lineupFields}
            value={lineup}
            submitLabel="Save Lineup"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSaveSection(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/lineups/${lineup.teamId}/${lineup.playerId}`,
                submitEvent.currentTarget,
                lineupFields,
                "라인업을 저장했습니다.",
              );
            }}
            disabled={disabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="Team Stats" count={detail.teamStats.length}>
        {detail.teamStats.map((stat) => (
          <details className="nested-admin-item" key={stat.teamId}>
            <summary>{stat.teamName ?? "Team"}</summary>
            <AdminForm
            title={stat.teamName ?? "Team"}
            fields={teamStatFields}
            value={stat}
            submitLabel="Save Team Stat"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSaveSection(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/team-stats/${stat.teamId}`,
                submitEvent.currentTarget,
                teamStatFields,
                "팀 경기 통계를 저장했습니다.",
              );
            }}
            disabled={disabled}
            />
          </details>
        ))}
      </NestedAdminSection>
      <NestedAdminSection title="Player Stats" count={detail.playerStats.length}>
        {detail.playerStats.map((stat) => (
          <details className="nested-admin-item" key={stat.playerId}>
            <summary>{stat.playerName ?? "-"} · {stat.teamName ?? "-"}</summary>
            <AdminForm
            title={`${stat.playerName ?? "-"} · ${stat.teamName ?? "-"}`}
            fields={playerStatFields}
            value={stat}
            submitLabel="Save Player Stat"
            onSubmit={(submitEvent) => {
              submitEvent.preventDefault();
              void onSaveSection(
                fixtureId,
                `/api/v1/admin/fixtures/${fixtureId}/player-stats/${stat.playerId}`,
                submitEvent.currentTarget,
                playerStatFields,
                "선수 경기 통계를 저장했습니다.",
              );
            }}
            disabled={disabled}
            />
          </details>
        ))}
      </NestedAdminSection>
    </div>
  );
}

function NestedAdminSection({ title, count, children }: { title: string; count?: number; children: React.ReactNode }) {
  return (
    <details className="nested-admin-section">
      <summary>
        <span>{title}</span>
        {typeof count === "number" ? <span className="small-count">{count}</span> : null}
      </summary>
      <div className="nested-admin-grid">{children}</div>
    </details>
  );
}

function newFixtureEventValue(detail: FixtureDetailAdmin): Record<string, unknown> {
  return {
    teamId: detail.fixture.homeTeamId,
    playerId: null,
    assistPlayerId: null,
    elapsed: 0,
    extra: null,
    eventType: "Goal",
    eventDetail: "Normal Goal",
    comments: "",
  };
}

function EventAdminForm({
  title,
  detail,
  value,
  submitLabel,
  onSubmit,
  disabled,
}: {
  title: string;
  detail: FixtureDetailAdmin;
  value: Record<string, unknown>;
  submitLabel: string;
  onSubmit: (body: Record<string, unknown>) => void;
  disabled?: boolean;
}) {
  const [draft, setDraft] = useState(() => eventDraftValue(value));

  useEffect(() => {
    setDraft(eventDraftValue(value));
  }, [value]);

  const selectedType = normalizeEventType(draft.eventType);
  const fields = eventFieldsWithOptions(detail, selectedType);
  const formValue: Record<string, unknown> = {
    ...draft,
    eventType: selectedType,
    eventDetail: validEventDetail(selectedType, draft.eventDetail),
  };

  function updateDraft(field: FieldConfig, nextValue: string) {
    setDraft((current) => {
      const next = { ...current, [field.name]: nextValue };
      if (field.name !== "eventType") {
        return next;
      }
      const nextType = normalizeEventType(nextValue);
      return {
        ...next,
        eventType: nextType,
        eventDetail: validEventDetail(nextType, current.eventDetail),
      };
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(payloadFromDraft(formValue, fields));
  }

  return (
    <form className="admin-edit-form" onSubmit={submit}>
      <h3>{title}</h3>
      <div className="admin-form-grid">
        {fields.map((field) => (
          <AdminField
            key={field.name === "eventDetail" ? `${field.name}-${selectedType}` : field.name}
            field={field}
            value={formValue[field.name]}
            onChange={(nextValue) => updateDraft(field, nextValue)}
          />
        ))}
      </div>
      <button type="submit" disabled={disabled}>{submitLabel}</button>
    </form>
  );
}

function eventDraftValue(value: Record<string, unknown>): Record<string, unknown> {
  const eventType = normalizeEventType(value.eventType);
  return {
    ...value,
    eventType,
    eventDetail: validEventDetail(eventType, value.eventDetail),
  };
}

function validEventDetail(eventType: string, value: unknown) {
  const options = eventDetailOptions(eventType);
  return typeof value === "string" && options.some((option) => option.value === value) ? value : options[0]?.value ?? "";
}

function payloadFromDraft(draft: Record<string, unknown>, fields: FieldConfig[]) {
  const body: Record<string, unknown> = {};
  fields.forEach((field) => {
    body[field.name] = coerceValue(draft[field.name], field.kind);
  });
  return body;
}

function eventFieldsWithOptions(detail: FixtureDetailAdmin, selectedType: string): FieldConfig[] {
  const teamOptions: FieldOption[] = [
    {
      value: Number(detail.fixture.homeTeamId),
      label: detail.fixture.homeTeamName ?? "Home",
    },
    {
      value: Number(detail.fixture.awayTeamId),
      label: detail.fixture.awayTeamName ?? "Away",
    },
  ].filter((option) => Number.isFinite(option.value));
  const playerOptions = fixturePlayerOptions(detail);

  return eventFields.map((field) => {
    if (field.name === "teamId") {
      return { ...field, options: teamOptions };
    }
    if (field.name === "playerId" || field.name === "assistPlayerId") {
      return { ...field, options: playerOptions };
    }
    if (field.name === "eventType") {
      return { ...field, options: eventTypeOptions };
    }
    if (field.name === "eventDetail") {
      return { ...field, options: eventDetailOptions(selectedType) };
    }
    return field;
  });
}

function eventDetailOptions(selectedType: string) {
  return eventDetailOptionsByType[selectedType] ?? [];
}

function normalizeEventType(value: unknown) {
  if (typeof value !== "string") {
    return "Goal";
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === "goal") {
    return "Goal";
  }
  if (normalized === "card") {
    return "Card";
  }
  if (normalized === "subst" || normalized === "substitution") {
    return "Subst";
  }
  if (normalized === "var") {
    return "Var";
  }
  return "Goal";
}

function fixturePlayerOptions(detail: FixtureDetailAdmin): FieldOption[] {
  const players = new Map<number, string>();
  detail.lineups.forEach((lineup) => {
    if (Number.isFinite(lineup.playerId)) {
      players.set(lineup.playerId, `${lineup.playerName ?? "-"} · ${lineup.teamName ?? "-"}`);
    }
  });
  detail.playerStats.forEach((stat) => {
    if (Number.isFinite(stat.playerId) && !players.has(stat.playerId)) {
      players.set(stat.playerId, `${stat.playerName ?? "-"} · ${stat.teamName ?? "-"}`);
    }
  });

  return Array.from(players.entries())
    .sort((left, right) => left[1].localeCompare(right[1]))
    .map(([value, label]) => ({ value, label }));
}

function AdminField({
  field,
  value,
  overridden,
  onChange,
}: {
  field: FieldConfig;
  value: unknown;
  overridden?: boolean;
  onChange?: (value: string) => void;
}) {
  if (field.kind === "select") {
    const selectValue = value === null || value === undefined ? "" : String(value);
    return (
      <label>
        <span>{field.label}{overridden ? " · manual" : ""}</span>
        <select
          name={field.name}
          value={onChange ? selectValue : undefined}
          defaultValue={onChange ? undefined : selectValue}
          onChange={(event) => onChange?.(event.currentTarget.value)}
        >
          <option value="">-</option>
          {(field.options ?? []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {field.help ? <small className="admin-field-help">{field.help}</small> : null}
      </label>
    );
  }

  if (field.kind === "boolean") {
    const booleanValue = value === null || value === undefined ? "" : String(value);
    return (
      <label>
        <span>{field.label}{overridden ? " · manual" : ""}</span>
        <select
          name={field.name}
          value={onChange ? booleanValue : undefined}
          defaultValue={onChange ? undefined : booleanValue}
          onChange={(event) => onChange?.(event.currentTarget.value)}
        >
          <option value="">-</option>
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
        {field.help ? <small className="admin-field-help">{field.help}</small> : null}
      </label>
    );
  }

  if (field.preview === "color") {
    return <ColorPreviewField field={field} value={value} overridden={overridden} />;
  }

  const textValue = inputValue(value, field.kind);
  return (
    <label>
      <span>{field.label}{overridden ? " · manual" : ""}</span>
      <input
        name={field.name}
        type={field.kind === "number" ? "number" : field.kind === "date" ? "date" : field.kind === "datetime" ? "datetime-local" : "text"}
        step={field.kind === "number" ? "any" : undefined}
        min={field.min}
        max={field.max}
        value={onChange ? textValue : undefined}
        defaultValue={onChange ? undefined : textValue}
        onChange={(event) => onChange?.(event.currentTarget.value)}
      />
      {field.help ? <small className="admin-field-help warning">{field.help}</small> : null}
    </label>
  );
}

function ColorPreviewField({ field, value, overridden }: { field: FieldConfig; value: unknown; overridden?: boolean }) {
  const [colorCode, setColorCode] = useState(normalizeHexCode(inputValue(value, field.kind)));
  const pickerColor = colorInputValue(colorCode);

  useEffect(() => {
    setColorCode(normalizeHexCode(inputValue(value, field.kind)));
  }, [field.kind, value]);

  return (
    <label>
      <span>{field.label}{overridden ? " · manual" : ""}</span>
      <div className="admin-color-field">
        <input
          name={field.name}
          type="text"
          value={colorCode}
          maxLength={6}
          onChange={(event) => setColorCode(normalizeHexCode(event.target.value))}
          placeholder="ffffff"
        />
        <input
          className={`admin-color-preview${pickerColor ? "" : " empty"}`}
          type="color"
          value={pickerColor || "#ffffff"}
          onChange={(event) => setColorCode(event.target.value.replace("#", "").toUpperCase())}
          aria-label={`${field.label} preview`}
        />
      </div>
      {field.help ? <small className="admin-field-help">{field.help}</small> : null}
    </label>
  );
}

function formBody(form: HTMLFormElement, fields: FieldConfig[]) {
  const body: Record<string, unknown> = {};
  const formData = new FormData(form);
  fields.forEach((field) => {
    const value = formData.get(field.name);
    body[field.name] = coerceValue(value, field.kind);
  });
  return body;
}

function coerceValue(value: unknown, kind: FieldKind = "text") {
  if (value === null || value === "") {
    return null;
  }
  const text = String(value);
  if (kind === "number") {
    return Number(text);
  }
  if (kind === "select") {
    return /^-?\d+$/.test(text) ? Number(text) : text;
  }
  if (kind === "boolean") {
    return text === "true" ? true : text === "false" ? false : null;
  }
  if (kind === "datetime") {
    return `${text}:00+09:00`;
  }
  return text;
}

function inputValue(value: unknown, kind: FieldKind = "text") {
  if (value === null || value === undefined) {
    return "";
  }
  if (kind === "datetime" && typeof value === "string") {
    return value.slice(0, 16);
  }
  return String(value);
}

function normalizeHexCode(value: string) {
  return value.replace("#", "").replace(/[^0-9a-f]/gi, "").slice(0, 6).toUpperCase();
}

function colorInputValue(value: string) {
  return /^[0-9A-F]{6}$/i.test(value) ? `#${value}` : "";
}

async function loadAuditLogs(
  page: number,
  setLogs: (logs: AuditLog[]) => void,
  setPage: (page: number) => void,
  setTotalPages: (totalPages: number) => void,
  setTotalElements: (totalElements: number) => void,
) {
  const response = await adminGet<AuditLogPage>(`/api/v1/admin/audit-logs?page=${page}&size=${AUDIT_LOG_PAGE_SIZE}`);
  setLogs(response.logs ?? []);
  setPage(response.page ?? page);
  setTotalPages(response.totalPages ?? 0);
  setTotalElements(response.totalElements ?? 0);
}

async function loadSyncStatuses(season: number, setSyncStatuses: (statuses: SyncStatus[]) => void) {
  const response = await adminGet<{ statuses: SyncStatus[] }>(`/api/v1/admin/sync/statuses?season=${season}`);
  setSyncStatuses(response.statuses ?? []);
}

async function loadSeasonCoverages(
  setSeasonCoverages: (coverages: LeagueSeasonCoverage[]) => void,
  setCoverageStatus: (status: CoverageStatus) => void,
) {
  setCoverageStatus("loading");
  try {
    const response = await fetchLeagueSeasons();
    setSeasonCoverages(response.seasons ?? []);
    setCoverageStatus("ready");
  } catch {
    setSeasonCoverages([]);
    setCoverageStatus("error");
  }
}

function syncAvailability(
  task: string,
  season: number,
  coverages: LeagueSeasonCoverage[],
  coverageStatus: CoverageStatus,
) {
  if (task === "seasons") {
    return { enabled: true, message: "" };
  }
  if (coverageStatus === "loading") {
    return { enabled: false, message: "시즌 지원 범위를 확인 중입니다." };
  }
  if (coverageStatus === "error") {
    return { enabled: false, message: "시즌 지원 범위를 확인할 수 없습니다." };
  }

  const coverage = coverages.find((item) => item.seasonYear === season);
  if (!coverage) {
    return { enabled: false, message: "이 시즌의 API-Football 지원 정보가 없습니다." };
  }

  if (task === "standings" && coverage.standings === false) {
    return { enabled: false, message: "이 시즌은 순위 데이터를 제공하지 않습니다." };
  }
  if (task === "players" && coverage.players === false) {
    return { enabled: false, message: "이 시즌은 선수 데이터를 제공하지 않습니다." };
  }
  if (task === "injuries" && coverage.injuries === false) {
    return { enabled: false, message: "이 시즌은 부상자 데이터를 제공하지 않습니다." };
  }
  if (task === "fixture-details" && !supportsFixtureDetails(coverage)) {
    return { enabled: false, message: "이 시즌은 경기 상세 데이터를 제공하지 않습니다." };
  }

  return { enabled: true, message: "" };
}

function supportsFixtureDetails(coverage: LeagueSeasonCoverage) {
  return [coverage.events, coverage.lineups, coverage.fixtureStats, coverage.playerStats].some((value) => value !== false);
}

async function adminGet<T>(url: string): Promise<T> {
  return adminRequest<T>(url, { method: "GET" });
}

async function adminJson<T>(url: string, method: string, body?: unknown): Promise<T> {
  return adminRequest<T>(url, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function adminRequest<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `${url} failed (${response.status})`));
  }
  return response.status === 204 ? (null as T) : response.json();
}

async function errorMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

function formatFixtureDate(value: string | null) {
  if (!value) {
    return "일시 미정";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function formatFixtureScore(fixture: FixtureSummaryAdmin) {
  if (fixture.homeScore === null || fixture.awayScore === null) {
    return "vs";
  }
  return `${fixture.homeScore} : ${fixture.awayScore}`;
}

function labelize(value: string) {
  return value.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
}

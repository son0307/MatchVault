import { FormEvent, useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { Navigate } from "react-router-dom";
import { clearApiMemoryCache, fetchLeagueSeasons, type LeagueSeasonCoverage } from "../api";
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

type PlayerAdmin = Record<string, unknown> & {
  playerId: number;
  name: string | null;
  manualOverrides?: AdminOverride[];
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
  { name: "height", label: "Height" },
  { name: "weight", label: "Weight" },
  { name: "position", label: "Position" },
  { name: "number", label: "Number", kind: "number" },
  { name: "photoUrl", label: "Photo URL" },
];

const fixtureFields: FieldConfig[] = [
  { name: "fixtureDate", label: "Fixture Date", kind: "datetime", help: "UTC 기준으로 입력해주세요." },
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
  const [selectedTeam, setSelectedTeam] = useState<TeamAdmin | null>(null);
  const [playerKeyword, setPlayerKeyword] = useState("");
  const [players, setPlayers] = useState<PlayerAdmin[]>([]);
  const [selectedPlayer, setSelectedPlayer] = useState<PlayerAdmin | null>(null);
  const [fixtureKeyword, setFixtureKeyword] = useState("");
  const [fixtures, setFixtures] = useState<FixtureSummaryAdmin[]>([]);
  const [selectedFixture, setSelectedFixture] = useState<FixtureDetailAdmin | null>(null);
  const [syncFixtureId, setSyncFixtureId] = useState("");
  const [syncMessage, setSyncMessage] = useState("");
  const [syncingTask, setSyncingTask] = useState<string | null>(null);
  const [syncStatuses, setSyncStatuses] = useState<SyncStatus[]>([]);
  const [seasonCoverages, setSeasonCoverages] = useState<LeagueSeasonCoverage[]>([]);
  const [coverageStatus, setCoverageStatus] = useState<CoverageStatus>("loading");
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [auditPage, setAuditPage] = useState(0);
  const [auditTotalPages, setAuditTotalPages] = useState(0);
  const [auditTotalElements, setAuditTotalElements] = useState(0);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (authState.authStatus === "authenticated" && authState.currentUser?.role === "ADMIN") {
      void loadAuditLogs(0, setLogs, setAuditPage, setAuditTotalPages, setAuditTotalElements);
      void loadSyncStatuses(authState.season, setSyncStatuses);
      void loadSeasonCoverages(setSeasonCoverages, setCoverageStatus);
    }
  }, [authState.authStatus, authState.currentUser?.role, authState.season]);

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

  async function searchPlayers(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<PlayerAdmin[]>(`/api/v1/admin/players?keyword=${encodeURIComponent(playerKeyword)}`);
      setPlayers(result);
    });
  }

  async function searchFixtures(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runRequest(async () => {
      const result = await adminGet<FixtureSummaryAdmin[]>(
        `/api/v1/admin/fixtures?keyword=${encodeURIComponent(fixtureKeyword)}&season=${authState.season}`,
      );
      setFixtures(result);
    });
  }

  async function selectFixture(fixtureId: number) {
    await runRequest(async () => {
      setSelectedFixture(await adminGet<FixtureDetailAdmin>(`/api/v1/admin/fixtures/${fixtureId}`));
    });
  }

  async function reloadAuditLogs(page = auditPage) {
    await loadAuditLogs(page, setLogs, setAuditPage, setAuditTotalPages, setAuditTotalElements);
  }

  async function saveTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTeam) {
      return;
    }
    await runRequest(async () => {
      const updated = await adminJson<TeamAdmin>(`/api/v1/admin/teams/${selectedTeam.teamId}`, "PUT", formBody(event.currentTarget, teamFields));
      clearApiMemoryCache();
      setSelectedTeam(updated);
      setMessage("팀 정보를 저장했습니다.");
      await reloadAuditLogs();
    });
  }

  async function savePlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPlayer) {
      return;
    }
    await runRequest(async () => {
      const updated = await adminJson<PlayerAdmin>(`/api/v1/admin/players/${selectedPlayer.playerId}`, "PUT", formBody(event.currentTarget, playerFields));
      clearApiMemoryCache();
      setSelectedPlayer(updated);
      setMessage("선수 정보를 저장했습니다.");
      await reloadAuditLogs();
    });
  }

  async function saveFixture(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFixture) {
      return;
    }
    await saveFixtureSection(`/api/v1/admin/fixtures/${selectedFixture.fixture.fixtureId}`, event.currentTarget, fixtureFields, "경기 기본 정보를 저장했습니다.");
  }

  async function saveFixtureSection(url: string, form: HTMLFormElement, fields: FieldConfig[], successMessage: string, method = "PUT") {
    await runRequest(async () => {
      const updated = await adminJson<FixtureDetailAdmin>(url, method, formBody(form, fields));
      clearApiMemoryCache();
      setSelectedFixture(updated);
      setMessage(successMessage);
      await reloadAuditLogs();
    });
  }

  async function runSync(task: string) {
    const availability = syncAvailability(task, authState.season, seasonCoverages, coverageStatus);
    if (!availability.enabled) {
      setSyncMessage(availability.message);
      return;
    }
    const league = 39;
    const season = authState.season;
    const urls: Record<string, string | null> = {
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
    setSyncingTask(task);
    try {
      await runRequest(async () => {
        const result = await adminJson<{ message: string }>(url, "POST");
        clearApiMemoryCache();
        setSyncMessage(result.message);
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
          <SearchRow value={teamKeyword} placeholder="Search team" onChange={setTeamKeyword} onSubmit={searchTeams} />
          <ResultList
            items={teams}
            getKey={(team) => team.teamId}
            render={(team) => `${team.name ?? "-"} #${team.teamId}`}
            onSelect={setSelectedTeam}
          />
          {selectedTeam ? (
            <AdminForm
              title={selectedTeam.name ?? "Team"}
              fields={teamFields}
              value={selectedTeam}
              overrides={selectedTeam.manualOverrides}
              submitLabel="Save Team"
              onSubmit={saveTeam}
            />
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "player" ? (
        <EditorPanel title="Player Editor" eyebrow="Players">
          <SearchRow value={playerKeyword} placeholder="Search player" onChange={setPlayerKeyword} onSubmit={searchPlayers} />
          <ResultList
            items={players}
            getKey={(player) => player.playerId}
            render={(player) => `${player.name ?? "-"} #${player.playerId}`}
            onSelect={setSelectedPlayer}
          />
          {selectedPlayer ? (
            <AdminForm
              title={selectedPlayer.name ?? "Player"}
              fields={playerFields}
              value={selectedPlayer}
              overrides={selectedPlayer.manualOverrides}
              submitLabel="Save Player"
              onSubmit={savePlayer}
            />
          ) : null}
        </EditorPanel>
      ) : null}

      {activeTab === "fixture" ? (
      <EditorPanel title="Fixture Editor" eyebrow="Fixtures">
        <SearchRow value={fixtureKeyword} placeholder="Search fixture ID or team" onChange={setFixtureKeyword} onSubmit={searchFixtures} />
        <ResultList
          items={fixtures}
          getKey={(fixture) => fixture.fixtureId}
          render={(fixture) =>
            `${fixture.homeTeamName ?? "-"} vs ${fixture.awayTeamName ?? "-"} · ${fixture.fixtureStatus ?? "-"} · #${fixture.fixtureId}`
          }
          onSelect={(fixture) => void selectFixture(fixture.fixtureId)}
        />
        {selectedFixture ? (
          <FixtureEditor
            detail={selectedFixture}
            onSaveFixture={saveFixture}
            onSaveSection={saveFixtureSection}
          />
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
            return (
              <div className="admin-sync-action" key={item.task}>
                <button type="button" onClick={() => void runSync(item.task)} disabled={syncingTask !== null || !availability.enabled}>
                  {isSyncing ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
                  {item.label}
                </button>
                <span>Last sync: {formatDateTime(status?.lastSyncedAt ?? null)}</span>
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
          <button type="submit" disabled={syncingTask !== null}>
            {syncingTask === "fixture-detail" ? <LoaderCircle className="admin-loading-icon" aria-hidden="true" /> : null}
            Update Fixture Detail
          </button>
        </form>
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
      <input type="search" value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
      <button type="submit">Search</button>
    </form>
  );
}

function ResultList<T>({
  items,
  getKey,
  render,
  onSelect,
}: {
  items: T[];
  getKey: (item: T) => string | number;
  render: (item: T) => string;
  onSelect: (item: T) => void;
}) {
  if (!items.length) {
    return null;
  }

  return (
    <div className="admin-result-list">
      {items.map((item) => (
        <button type="button" key={getKey(item)} onClick={() => onSelect(item)}>
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
}: {
  title: string;
  fields: FieldConfig[];
  value: Record<string, unknown>;
  overrides?: AdminOverride[];
  submitLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
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
      <button type="submit">{submitLabel}</button>
    </form>
  );
}

function FixtureEditor({
  detail,
  onSaveFixture,
  onSaveSection,
}: {
  detail: FixtureDetailAdmin;
  onSaveFixture: (event: FormEvent<HTMLFormElement>) => void;
  onSaveSection: (url: string, form: HTMLFormElement, fields: FieldConfig[], successMessage: string, method?: string) => Promise<void>;
}) {
  const fixtureId = detail.fixture.fixtureId;
  const [addingEvent, setAddingEvent] = useState(false);
  const newEventValue = {
    teamId: detail.fixture.homeTeamId,
    playerId: null,
    assistPlayerId: null,
    elapsed: 0,
    extra: null,
    eventType: "Goal",
    eventDetail: "Normal Goal",
    comments: "",
  };
  return (
    <div className="fixture-admin-editor">
      <NestedAdminSection title="Fixture Info" count={1}>
        <AdminForm
          title={`${detail.fixture.homeTeamName ?? "-"} vs ${detail.fixture.awayTeamName ?? "-"}`}
          fields={fixtureFields}
          value={detail.fixture}
          submitLabel="Save Fixture"
          onSubmit={onSaveFixture}
        />
      </NestedAdminSection>
      <NestedAdminSection title="Events" count={detail.events.length}>
        <button type="button" className="admin-add-button" onClick={() => setAddingEvent((current) => !current)}>
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
              onSubmit={(submitEvent, fields) => {
                submitEvent.preventDefault();
                void onSaveSection(
                  `/api/v1/admin/fixtures/${fixtureId}/events`,
                  submitEvent.currentTarget,
                  fields,
                  "이벤트를 추가했습니다.",
                  "POST",
                ).then(() => setAddingEvent(false));
              }}
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
              onSubmit={(submitEvent, fields) => {
                submitEvent.preventDefault();
                void onSaveSection(
                  `/api/v1/admin/fixtures/${fixtureId}/events/${event.eventSequence}`,
                  submitEvent.currentTarget,
                  fields,
                  "이벤트를 저장했습니다.",
                );
              }}
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
                `/api/v1/admin/fixtures/${fixtureId}/lineups/${lineup.teamId}/${lineup.playerId}`,
                submitEvent.currentTarget,
                lineupFields,
                "라인업을 저장했습니다.",
              );
            }}
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
                `/api/v1/admin/fixtures/${fixtureId}/team-stats/${stat.teamId}`,
                submitEvent.currentTarget,
                teamStatFields,
                "팀 경기 통계를 저장했습니다.",
              );
            }}
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
                `/api/v1/admin/fixtures/${fixtureId}/player-stats/${stat.playerId}`,
                submitEvent.currentTarget,
                playerStatFields,
                "선수 경기 통계를 저장했습니다.",
              );
            }}
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

function EventAdminForm({
  title,
  detail,
  value,
  submitLabel,
  onSubmit,
}: {
  title: string;
  detail: FixtureDetailAdmin;
  value: Record<string, unknown>;
  submitLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>, fields: FieldConfig[]) => void;
}) {
  const initialType = normalizeEventType(value.eventType);
  const [selectedType, setSelectedType] = useState(initialType);

  useEffect(() => {
    setSelectedType(initialType);
  }, [initialType]);

  const fields = eventFieldsWithOptions(detail, selectedType);
  const formValue: Record<string, unknown> = {
    ...value,
    eventType: selectedType,
    eventDetail:
      typeof value.eventDetail === "string" && eventDetailOptions(selectedType).some((option) => option.value === value.eventDetail)
        ? value.eventDetail
        : eventDetailOptions(selectedType)[0]?.value ?? "",
  };

  return (
    <form className="admin-edit-form" onSubmit={(event) => onSubmit(event, fields)}>
      <h3>{title}</h3>
      <div className="admin-form-grid">
        {fields.map((field) => (
          <AdminField
            key={field.name === "eventDetail" ? `${field.name}-${selectedType}` : field.name}
            field={field}
            value={formValue[field.name]}
            onChange={field.name === "eventType" ? setSelectedType : undefined}
          />
        ))}
      </div>
      <button type="submit">{submitLabel}</button>
    </form>
  );
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
    return (
      <label>
        <span>{field.label}{overridden ? " · manual" : ""}</span>
        <select name={field.name} defaultValue={value === null || value === undefined ? "" : String(value)}>
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

  return (
    <label>
      <span>{field.label}{overridden ? " · manual" : ""}</span>
      <input
        name={field.name}
        type={field.kind === "number" ? "number" : field.kind === "date" ? "date" : field.kind === "datetime" ? "datetime-local" : "text"}
        step={field.kind === "number" ? "any" : undefined}
        min={field.min}
        max={field.max}
        defaultValue={inputValue(value, field.kind)}
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

function coerceValue(value: FormDataEntryValue | null, kind: FieldKind = "text") {
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

function labelize(value: string) {
  return value.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
}

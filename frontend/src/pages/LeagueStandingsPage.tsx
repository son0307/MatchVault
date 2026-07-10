import { useEffect, useMemo, useRef, useState } from "react";
import { RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import {
  fetchStandings,
  fetchSyncStatuses,
  requestPublicSync,
  type CurrentUser,
  type RecentForm,
  type StandingRecord,
  type SyncStatus,
  type TeamStanding,
} from "../api";
import { displayLocalizedName } from "../teamNames";

type StandingMode = "all" | "home" | "away" | "recent";

const standingModes: Array<{ label: string; value: StandingMode }> = [
  { label: "모두", value: "all" },
  { label: "홈", value: "home" },
  { label: "원정", value: "away" },
  { label: "최근 5경기", value: "recent" },
];

export function LeagueStandingsPage({ currentUser, season }: { currentUser: CurrentUser | null; season: number }) {
  const [standings, setStandings] = useState<TeamStanding[]>([]);
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
  const [syncMessage, setSyncMessage] = useState("");
  const [syncCooldownUntil, setSyncCooldownUntil] = useState(0);
  const [mode, setMode] = useState<StandingMode>("all");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const standingsRequestIdRef = useRef(0);

  const rows = useMemo(
    () =>
      standings
        .map((standing) => toStandingRow(standing, mode))
        .sort(compareStandingRows)
        .map((row, index) => ({ ...row, displayRank: index + 1 })),
    [standings, mode],
  );

  async function loadStandings(targetSeason = season) {
    const requestId = standingsRequestIdRef.current + 1;
    standingsRequestIdRef.current = requestId;
    setIsLoading(true);
    setErrorMessage("");

    try {
      const nextStandings = await fetchStandings(targetSeason);
      if (requestId === standingsRequestIdRef.current) {
        setStandings(nextStandings);
      }
    } catch (error) {
      if (requestId !== standingsRequestIdRef.current) {
        return;
      }
      setStandings([]);
      setErrorMessage(
        error instanceof Error ? error.message : "순위 정보를 불러오지 못했습니다.",
      );
    } finally {
      if (requestId === standingsRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }

  async function loadSyncStatus(targetSeason = season) {
    try {
      const response = await fetchSyncStatuses(targetSeason);
      setSyncStatus(response.statuses.find((status) => status.task === "standings") ?? null);
    } catch {
      setSyncStatus(null);
    }
  }

  async function refreshStandingsSync() {
    if (!currentUser || Date.now() < syncCooldownUntil) {
      return;
    }
    setSyncMessage("");
    setSyncCooldownUntil(Date.now() + 30_000);
    try {
      await requestPublicSync("standings", season);
      setSyncMessage("Latest standings data was requested.");
      await Promise.all([loadStandings(season), loadSyncStatus(season)]);
    } catch (error) {
      setSyncMessage(error instanceof Error ? error.message : "Standings sync request failed.");
      await loadSyncStatus(season);
    }
  }

  useEffect(() => {
    void loadStandings(season);
    void loadSyncStatus(season);
  }, [season]);

  const isStale = isSyncStatusStale(syncStatus);
  const cooldownSeconds = Math.max(0, Math.ceil((syncCooldownUntil - Date.now()) / 1000));

  return (
    <section className="league-content">
      <div className="standings-toolbar">
        <div className="segmented-control" aria-label="순위 범위">
          {standingModes.map((item) => (
            <button
              className={mode === item.value ? "active" : ""}
              key={item.value}
              type="button"
              onClick={() => setMode(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="toolbar" aria-label="시즌과 새로고침">
          <button
            className="icon-button"
            type="button"
            onClick={() => void loadStandings()}
            aria-label="새로고침"
            title="새로고침"
          >
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className={`data-freshness ${isStale ? "stale" : ""}`}>
        <span>{syncFreshnessText(syncStatus, "Standings")}</span>
        {isStale && currentUser ? (
          <button type="button" onClick={() => void refreshStandingsSync()} disabled={cooldownSeconds > 0}>
            {cooldownSeconds > 0 ? `${cooldownSeconds}s` : "Refresh data"}
          </button>
        ) : null}
      </div>
      {syncMessage ? <div className="notice">{syncMessage}</div> : null}

      {errorMessage ? <div className="notice error">{errorMessage}</div> : null}

      <article className="panel standings-panel">
        {isLoading ? (
          <div className="empty-state">순위 정보를 불러오는 중입니다.</div>
        ) : (
          <>
            <StandingsTable rows={rows} />
            <QualificationLegend />
          </>
        )}
      </article>
    </section>
  );
}

function StandingsTable({ rows }: { rows: StandingRow[] }) {
  if (!rows.length) {
    return <div className="empty-state">순위 데이터가 없습니다.</div>;
  }

  return (
    <div className="standings-table-wrap">
      <table className="standings-table">
        <thead>
          <tr>
            <th>#</th>
            <th className="team-column">팀</th>
            <th>경기</th>
            <th>승</th>
            <th>무</th>
            <th>패</th>
            <th>득실</th>
            <th>+/-</th>
            <th>승점</th>
            <th>기록</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.teamId}>
              <td className={`rank-cell ${row.qualificationClass}`}>
                <span>{row.displayRank}</span>
              </td>
              <td className="team-cell">
                {row.logo ? (
                  <img src={row.logo} alt="" className="team-logo" />
                ) : (
                  <span className="team-logo placeholder" aria-hidden="true" />
                )}
                <Link className="team-name-link" to={`/teams/${row.teamId}`}>
                  {row.teamName}
                </Link>
              </td>
              <td>{row.played}</td>
              <td>{row.win}</td>
              <td>{row.draw}</td>
              <td>{row.lose}</td>
              <td>{row.goalsText}</td>
              <td>{row.goalsDiffText}</td>
              <td className="points-cell">{row.points}</td>
              <td>
                <ResultChips results={row.results} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ResultChips({ results }: { results: string[] }) {
  if (!results.length) {
    return <span className="muted">-</span>;
  }

  return (
    <div className="result-chips" aria-label={`최근 기록 ${results.join("")}`}>
      {results.map((result, index) => (
        <span className={`result-chip ${result.toLowerCase()}`} key={`${result}-${index}`}>
          {translateResult(result)}
        </span>
      ))}
    </div>
  );
}

function isSyncStatusStale(status: SyncStatus | null) {
  if (!status) {
    return false;
  }
  if (status.status === "FAILED" || status.status === "RETRY_PENDING") {
    return true;
  }
  const lastSuccess = lastSuccessfulSyncTime(status);
  return lastSuccess ? Date.now() - Date.parse(lastSuccess) > 24 * 60 * 60 * 1000 : false;
}

function syncFreshnessText(status: SyncStatus | null, label: string) {
  const lastSuccess = lastSuccessfulSyncTime(status);
  if (!lastSuccess) {
    return `${label}: no sync timestamp`;
  }
  return `${label}: data as of ${formatDateTime(lastSuccess)}`;
}

function lastSuccessfulSyncTime(status: SyncStatus | null) {
  if (!status) {
    return null;
  }
  if (status.lastSuccessAt) {
    return status.lastSuccessAt;
  }
  return status.status === "OK" || status.status === "STALE" ? status.lastSyncedAt : null;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

type StandingRow = {
  teamId: number;
  displayRank: number;
  teamName: string;
  logo: string | null;
  played: number;
  win: number;
  draw: number;
  lose: number;
  goalsFor: number;
  goalsAgainst: number;
  goalsDiff: number;
  goalsText: string;
  goalsDiffText: string;
  points: number;
  results: string[];
  qualificationClass: string;
};

function toStandingRow(standing: TeamStanding, mode: StandingMode): StandingRow {
  const source = standingRecordForMode(standing, mode);
  const formResults = parseFormResults(standing.form);
  const recentSummary = summarizeResults(formResults);
  const goalsFor = valueOf(source?.goals?.goalsFor);
  const goalsAgainst = valueOf(source?.goals?.goalsAgainst);
  const goalsDiff =
    mode === "recent"
      ? valueOf(standing.recentForm?.goalsDiff)
      : goalsFor - goalsAgainst;

  return {
    teamId: standing.team?.id ?? standing.rank ?? 0,
    displayRank: standing.rank ?? 0,
    teamName: displayLocalizedName(standing.team?.nameKo, standing.team?.name),
    logo: standing.team?.logo ?? null,
    played: mode === "recent" ? recentSummary.played : valueOf(source?.played),
    win: mode === "recent" ? recentSummary.win : valueOf(source?.win),
    draw: mode === "recent" ? recentSummary.draw : valueOf(source?.draw),
    lose: mode === "recent" ? recentSummary.lose : valueOf(source?.lose),
    goalsFor,
    goalsAgainst,
    goalsDiff,
    goalsText: `${goalsFor}-${goalsAgainst}`,
    goalsDiffText: formatDiff(goalsDiff),
    points:
      mode === "recent"
        ? recentSummary.points
        : standingPointsForMode(standing, mode),
    results: formResults,
    qualificationClass: qualificationClassOf(standing.description),
  };
}

function standingRecordForMode(
  standing: TeamStanding,
  mode: StandingMode,
): StandingRecord | RecentForm | null {
  if (mode === "home") {
    return standing.home;
  }
  if (mode === "away") {
    return standing.away;
  }
  if (mode === "recent") {
    return standing.recentForm;
  }
  return standing.all;
}

function standingPointsForMode(standing: TeamStanding, mode: StandingMode) {
  const record = standingRecordForMode(standing, mode);
  if (mode === "all") {
    return valueOf(standing.points);
  }
  return valueOf(record?.win) * 3 + valueOf(record?.draw);
}

function formatDiff(value: number) {
  return value > 0 ? `+${value}` : String(value);
}

function compareStandingRows(a: StandingRow, b: StandingRow) {
  return (
    b.points - a.points ||
    b.goalsDiff - a.goalsDiff ||
    b.goalsFor - a.goalsFor ||
    String(a.teamName).localeCompare(String(b.teamName))
  );
}

function parseFormResults(form: string | null) {
  return String(form ?? "")
    .toUpperCase()
    .split("")
    .filter((result) => result === "W" || result === "D" || result === "L");
}

function summarizeResults(results: string[]) {
  return results.reduce(
    (summary, result) => {
      summary.played++;
      if (result === "W") {
        summary.win++;
        summary.points += 3;
      } else if (result === "D") {
        summary.draw++;
        summary.points += 1;
      } else if (result === "L") {
        summary.lose++;
      }
      return summary;
    },
    { played: 0, win: 0, draw: 0, lose: 0, points: 0 },
  );
}

function valueOf(value: number | null | undefined) {
  return value ?? 0;
}

function translateResult(result: string) {
  if (result === "W") {
    return "승";
  }
  if (result === "D") {
    return "무";
  }
  if (result === "L") {
    return "패";
  }
  return result;
}

function qualificationClassOf(description: string | null) {
  const text = String(description ?? "").toLowerCase();
  if (text.includes("champions league")) {
    return "champions";
  }
  if (text.includes("europa league")) {
    return "europa";
  }
  if (text.includes("conference league")) {
    return "conference";
  }
  if (text.includes("relegation")) {
    return "relegation";
  }
  return "";
}

function QualificationLegend() {
  return (
    <div className="qualification-legend" aria-label="순위 색상 안내">
      <span><i className="champions" />챔피언스리그 진출</span>
      <span><i className="europa" />유로파리그 진출</span>
      <span><i className="conference" />컨퍼런스리그 진출</span>
      <span><i className="relegation" />강등</span>
    </div>
  );
}

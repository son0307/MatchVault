import { useEffect, useMemo, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { CalendarDays, Clock, Goal, Star, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import {
  ApiError,
  addFavoriteTeam,
  fetchFavoriteDashboard,
  fetchFixtures,
  fetchPlayerPanel,
  fetchTeamDetails,
  fetchTeamPlayers,
  removeFavoriteTeam,
  type FixtureSummary,
  type PlayerPanel,
  type PlayerSummary,
  type TeamDetails,
} from "../api";

type LoadState<T> = {
  data: T | null;
  error: string;
  isLoading: boolean;
};

type PlayerRankRow = {
  playerId: number;
  playerName: string | null;
  photoUrl: string | null;
  position: string | null;
  goals: number;
  assists: number;
  rating: number;
  minutes: number;
};

const FIXTURE_FETCH_SIZE = 100;
const TEAM_FIXTURE_PAGE_SIZE = 10;

export function TeamDetailPage({ season }: { season: number }) {
  const { teamId } = useParams();
  const numericTeamId = Number(teamId);
  const [teamState, setTeamState] = useState<LoadState<TeamDetails>>({
    data: null,
    error: "",
    isLoading: true,
  });
  const [playersState, setPlayersState] = useState<LoadState<PlayerSummary[]>>({
    data: null,
    error: "",
    isLoading: true,
  });
  const [fixturesState, setFixturesState] = useState<LoadState<FixtureSummary[]>>({
    data: null,
    error: "",
    isLoading: true,
  });
  const [rankRows, setRankRows] = useState<PlayerRankRow[]>([]);
  const [rankError, setRankError] = useState("");
  const [isLoadingRanks, setIsLoadingRanks] = useState(false);
  const [fixturePage, setFixturePage] = useState(0);
  const [isFavorite, setIsFavorite] = useState(false);
  const [isFavoriteLoading, setIsFavoriteLoading] = useState(false);
  const [favoriteError, setFavoriteError] = useState("");

  useEffect(() => {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      const error = "올바른 팀 ID가 아닙니다.";
      setTeamState({ data: null, error, isLoading: false });
      setPlayersState({ data: null, error, isLoading: false });
      setFixturesState({ data: null, error, isLoading: false });
      return;
    }

    let isCurrent = true;
    setTeamState({ data: null, error: "", isLoading: true });
    setPlayersState({ data: null, error: "", isLoading: true });
    setFixturesState({ data: null, error: "", isLoading: true });
    setRankRows([]);
    setRankError("");

    fetchTeamDetails(numericTeamId)
      .then((data) => {
        if (isCurrent) {
          setTeamState({ data, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setTeamState({
            data: null,
            error: error instanceof Error ? error.message : "팀 정보를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

    setFixturePage(0);

    fetchTeamPlayers(numericTeamId, season)
      .then((players) => {
        if (isCurrent) {
          setPlayersState({ data: players, error: "", isLoading: false });
          void loadPlayerRanks(players, numericTeamId, isCurrent);
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setPlayersState({
            data: null,
            error: error instanceof Error ? error.message : "팀 선수 목록을 불러오지 못했습니다.",
            isLoading: false,
          });
          setIsLoadingRanks(false);
        }
      });

    fetchFixtures({ season, teamId: numericTeamId, size: FIXTURE_FETCH_SIZE })
      .then((response) => {
        if (isCurrent) {
          setFixturesState({ data: response.content ?? [], error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setFixturesState({
            data: null,
            error: error instanceof Error ? error.message : "팀 경기 일정을 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

    async function loadPlayerRanks(players: PlayerSummary[], targetTeamId: number, isStillCurrent: boolean) {
      setIsLoadingRanks(true);
      setRankError("");

      try {
        const panels = await Promise.allSettled(players.map((player) => fetchPlayerPanel(player.playerId)));
        if (!isStillCurrent) {
          return;
        }

        const rows = panels
          .map((result, index) =>
            result.status === "fulfilled"
              ? toRankRow(players[index], result.value, targetTeamId, season)
              : toFallbackRankRow(players[index]),
          )
          .filter((row): row is PlayerRankRow => Boolean(row));

        setRankRows(rows);
        if (panels.some((result) => result.status === "rejected")) {
          setRankError("일부 선수 통계를 불러오지 못했습니다.");
        }
      } catch (error) {
        if (isStillCurrent) {
          setRankRows([]);
          setRankError(error instanceof Error ? error.message : "선수 통계를 불러오지 못했습니다.");
        }
      } finally {
        if (isStillCurrent) {
          setIsLoadingRanks(false);
        }
      }
    }

    return () => {
      isCurrent = false;
    };
  }, [numericTeamId, season]);

  useEffect(() => {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      setIsFavorite(false);
      return;
    }

    let isCurrent = true;
    setFavoriteError("");

    fetchFavoriteDashboard(season)
      .then((dashboard) => {
        if (isCurrent) {
          setIsFavorite(dashboard.teams.some((team) => team.teamId === numericTeamId));
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setIsFavorite(false);
          if (!(error instanceof ApiError && error.status === 401)) {
            setFavoriteError(error instanceof Error ? error.message : "즐겨찾기 상태를 확인하지 못했습니다.");
          }
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [numericTeamId, season]);

  async function toggleFavorite() {
    if (!Number.isFinite(numericTeamId) || numericTeamId <= 0) {
      return;
    }

    setIsFavoriteLoading(true);
    setFavoriteError("");
    try {
      const dashboard = isFavorite
        ? await removeFavoriteTeam(numericTeamId, season)
        : await addFavoriteTeam(numericTeamId, season);
      setIsFavorite(dashboard.teams.some((team) => team.teamId === numericTeamId));
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFavoriteError("로그인이 필요합니다.");
      } else {
        setFavoriteError(error instanceof Error ? error.message : "즐겨찾기 변경에 실패했습니다.");
      }
    } finally {
      setIsFavoriteLoading(false);
    }
  }

  if (teamState.isLoading) {
    return (
      <section className="league-content team-detail-page">
        <article className="panel placeholder-panel">팀 정보를 불러오는 중입니다.</article>
      </section>
    );
  }

  if (teamState.error || !teamState.data) {
    return (
      <section className="league-content team-detail-page">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Team Detail</p>
          <h2>팀 정보를 불러오지 못했습니다</h2>
          <p className="muted">{teamState.error || "잠시 후 다시 시도해 주세요."}</p>
          <Link className="primary-link fixture-back-link" to="/league/standings">
            순위표로
          </Link>
        </article>
      </section>
    );
  }

  return (
    <section className="league-content team-detail-page">
      <TeamHero
        favoriteError={favoriteError}
        isFavorite={isFavorite}
        isFavoriteLoading={isFavoriteLoading}
        onToggleFavorite={toggleFavorite}
        team={teamState.data}
      />
      <TeamFixturePanel fixturePage={fixturePage} fixturesState={fixturesState} setFixturePage={setFixturePage} />
      <TeamPlayersPanel playersState={playersState} />
      <TeamPlayerRanksPanel error={rankError} isLoading={isLoadingRanks} rows={rankRows} />
    </section>
  );
}

function TeamHero({
  team,
  isFavorite,
  isFavoriteLoading,
  favoriteError,
  onToggleFavorite,
}: {
  team: TeamDetails;
  isFavorite: boolean;
  isFavoriteLoading: boolean;
  favoriteError: string;
  onToggleFavorite: () => void;
}) {
  return (
    <article className="panel team-detail-hero">
      {team.logoUrl ? <img src={team.logoUrl} alt="" className="team-detail-logo" /> : <span className="team-detail-logo placeholder" aria-hidden="true" />}
      <div>
        <p className="eyebrow">{team.country ?? "Team"}</p>
        <div className="detail-title-row">
          <h2>{team.teamName ?? "-"}</h2>
          <FavoriteToggleButton
            isActive={isFavorite}
            isLoading={isFavoriteLoading}
            onClick={onToggleFavorite}
            typeLabel="팀"
          />
        </div>
        {favoriteError ? <p className="favorite-inline-error">{favoriteError}</p> : null}
        <div className="team-detail-meta">
          <span>{team.code ?? "코드 정보 없음"}</span>
          <span>{team.founded ? `${team.founded} 창단` : "창단 정보 없음"}</span>
          <span>{team.venue?.venueName ?? "경기장 정보 없음"}</span>
        </div>
      </div>
    </article>
  );
}

function FavoriteToggleButton({
  isActive,
  isLoading,
  onClick,
  typeLabel,
}: {
  isActive: boolean;
  isLoading: boolean;
  onClick: () => void;
  typeLabel: string;
}) {
  return (
    <button
      className={`favorite-toggle-button${isActive ? " active" : ""}`}
      disabled={isLoading}
      onClick={onClick}
      title={`${typeLabel} 즐겨찾기 ${isActive ? "해제" : "등록"}`}
      type="button"
    >
      <Star size={17} aria-hidden="true" fill={isActive ? "currentColor" : "none"} />
      <span>{isLoading ? "처리 중" : isActive ? "즐겨찾기 해제" : "즐겨찾기"}</span>
    </button>
  );
}

function TeamFixturePanel({
  fixturePage,
  fixturesState,
  setFixturePage,
}: {
  fixturePage: number;
  fixturesState: LoadState<FixtureSummary[]>;
  setFixturePage: Dispatch<SetStateAction<number>>;
}) {
  const orderedFixtures = useMemo(
    () => (fixturesState.data ?? []).slice().sort((a, b) => fixtureTime(a.fixtureDate) - fixtureTime(b.fixtureDate)),
    [fixturesState.data],
  );
  const totalPages = Math.max(1, Math.ceil(orderedFixtures.length / TEAM_FIXTURE_PAGE_SIZE));
  const currentPage = Math.min(fixturePage, totalPages - 1);
  const visibleFixtures = orderedFixtures.slice(
    currentPage * TEAM_FIXTURE_PAGE_SIZE,
    (currentPage + 1) * TEAM_FIXTURE_PAGE_SIZE,
  );
  const groupedFixtures = useMemo(() => groupFixturesByDate(visibleFixtures), [visibleFixtures]);

  return (
    <article className="panel team-fixtures-panel">
      <div className="detail-panel-heading player-panel-title">
        <CalendarDays size={19} aria-hidden="true" />
        <h2>경기 일정</h2>
      </div>
      {fixturesState.isLoading ? <div className="empty-state">경기 일정을 불러오는 중입니다.</div> : null}
      {fixturesState.error ? <div className="section-error">{fixturesState.error}</div> : null}
      {!fixturesState.isLoading && !fixturesState.error ? <TeamFixtureGroups groupedFixtures={groupedFixtures} /> : null}
      {!fixturesState.isLoading && !fixturesState.error && orderedFixtures.length > TEAM_FIXTURE_PAGE_SIZE ? (
        <div className="team-pager team-detail-pager">
          <button
            type="button"
            onClick={() => setFixturePage((page) => Math.max(0, page - 1))}
            disabled={currentPage === 0}
          >
            이전
          </button>
          <strong>{currentPage + 1} / {totalPages}</strong>
          <button
            type="button"
            onClick={() => setFixturePage((page) => Math.min(totalPages - 1, page + 1))}
            disabled={currentPage >= totalPages - 1}
          >
            다음
          </button>
        </div>
      ) : null}
    </article>
  );
}

function TeamFixtureGroups({ groupedFixtures }: { groupedFixtures: Array<[string, FixtureSummary[]]> }) {
  if (!groupedFixtures.length) {
    return <div className="empty-state">표시할 경기 일정이 없습니다.</div>;
  }

  return (
    <div className="team-detail-fixture-list">
      {groupedFixtures.map(([dateKey, fixtures]) => (
        <section className="team-detail-fixture-group" key={dateKey}>
          <h3>{dateGroupTitle(dateKey)}</h3>
          {fixtures.map((fixture) => (
            <Link className="team-detail-fixture-row" key={fixture.fixtureId} to={`/fixtures/${fixture.fixtureId}`}>
              <time>{formatTime(fixture.fixtureDate)}</time>
              <strong>{fixture.homeTeamName ?? "-"}</strong>
              <span>{scoreText(fixture)}</span>
              <strong>{fixture.awayTeamName ?? "-"}</strong>
              <em>{fixture.fixtureStatus ?? "예정"}</em>
            </Link>
          ))}
        </section>
      ))}
    </div>
  );
}

function TeamPlayersPanel({ playersState }: { playersState: LoadState<PlayerSummary[]> }) {
  const players = (playersState.data ?? []).slice().sort(comparePlayers);

  return (
    <article className="panel team-players-panel">
      <div className="detail-panel-heading player-panel-title">
        <Users size={19} aria-hidden="true" />
        <h2>등록 선수</h2>
      </div>
      {playersState.isLoading ? <div className="empty-state">선수 목록을 불러오는 중입니다.</div> : null}
      {playersState.error ? <div className="section-error">{playersState.error}</div> : null}
      {!playersState.isLoading && !playersState.error ? (
        players.length ? (
          <div className="team-player-grid">
            {players.map((player) => (
              <Link className="team-player-card" key={player.playerId} to={`/players/${player.playerId}`}>
                {player.photoUrl ? <img src={player.photoUrl} alt="" className="player-thumb" /> : <span className="player-thumb placeholder" aria-hidden="true" />}
                <div>
                  <strong>{player.playerName ?? "-"}</strong>
                  <p>
                    {player.backNumber ? `No. ${player.backNumber}` : "등번호 없음"} · {player.position ?? "Player"}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="empty-state">등록된 선수가 없습니다.</div>
        )
      ) : null}
    </article>
  );
}

function TeamPlayerRanksPanel({
  rows,
  isLoading,
  error,
}: {
  rows: PlayerRankRow[];
  isLoading: boolean;
  error: string;
}) {
  const rankGroups = [
    { label: "득점 순위", icon: Goal, rows: topRank(rows, "goals"), value: (row: PlayerRankRow) => `${row.goals}골` },
    { label: "도움 순위", icon: Star, rows: topRank(rows, "assists"), value: (row: PlayerRankRow) => `${row.assists}도움` },
    { label: "평점 순위", icon: Star, rows: topRank(rows, "rating"), value: (row: PlayerRankRow) => ratingText(row.rating) },
    { label: "출전 시간 순위", icon: Clock, rows: topRank(rows, "minutes"), value: (row: PlayerRankRow) => `${row.minutes}분` },
  ] satisfies Array<{
    label: string;
    icon: LucideIcon;
    rows: PlayerRankRow[];
    value: (row: PlayerRankRow) => string;
  }>;

  return (
    <article className="panel team-ranks-panel">
      <div className="detail-panel-heading player-panel-title">
        <Star size={19} aria-hidden="true" />
        <h2>선수 통계</h2>
      </div>
      {isLoading ? <div className="empty-state">선수 통계를 불러오는 중입니다.</div> : null}
      {error ? <div className="section-error">{error}</div> : null}
      {!isLoading && rows.length ? (
        <div className="team-rank-grid">
          {rankGroups.map((group) => (
            <section className="team-rank-card" key={group.label}>
              <h3>
                <group.icon size={17} aria-hidden={true} />
                {group.label}
              </h3>
              {group.rows.map((row, index) => (
                <Link className="team-rank-row" key={row.playerId} to={`/players/${row.playerId}`}>
                  <span>{index + 1}</span>
                  <strong>{row.playerName ?? "-"}</strong>
                  <em>{group.value(row)}</em>
                </Link>
              ))}
            </section>
          ))}
        </div>
      ) : null}
      {!isLoading && !rows.length && !error ? <div className="empty-state">표시할 선수 통계가 없습니다.</div> : null}
    </article>
  );
}

function toRankRow(player: PlayerSummary, panel: PlayerPanel, teamId: number, season: number): PlayerRankRow {
  const teamStats = panel.seasons
    ?.filter((seasonSummary) => seasonSummary.season === season)
    .flatMap((season) => season.teams ?? [])
    .find((team) => team.teamId === teamId);

  return {
    ...toFallbackRankRow(player),
    goals: teamStats?.goals ?? 0,
    assists: teamStats?.assists ?? 0,
    rating: teamStats?.averageRating ?? 0,
    minutes: teamStats?.minutesPlayed ?? 0,
  };
}

function toFallbackRankRow(player: PlayerSummary): PlayerRankRow {
  return {
    playerId: player.playerId,
    playerName: player.playerName,
    photoUrl: player.photoUrl,
    position: player.position,
    goals: 0,
    assists: 0,
    rating: 0,
    minutes: 0,
  };
}

function topRank(rows: PlayerRankRow[], field: "goals" | "assists" | "rating" | "minutes") {
  return rows
    .slice()
    .sort((a, b) => b[field] - a[field] || (a.playerName ?? "").localeCompare(b.playerName ?? ""))
    .slice(0, 5);
}

function comparePlayers(left: PlayerSummary, right: PlayerSummary) {
  return positionRank(left.position) - positionRank(right.position)
    || compareNullableNumber(left.backNumber, right.backNumber)
    || (left.playerName ?? "").localeCompare(right.playerName ?? "");
}

function positionRank(position: string | null) {
  const normalized = (position ?? "").toUpperCase();
  if (normalized === "G" || normalized === "GK" || normalized.includes("GOAL")) {
    return 0;
  }
  if (normalized === "D" || normalized.includes("DEF")) {
    return 1;
  }
  if (normalized === "M" || normalized.includes("MID")) {
    return 2;
  }
  if (normalized === "F" || normalized === "A" || normalized.includes("ATT") || normalized.includes("FOR")) {
    return 3;
  }
  return 4;
}

function compareNullableNumber(left: number | null | undefined, right: number | null | undefined) {
  if (left === right) {
    return 0;
  }
  if (left === null || left === undefined) {
    return 1;
  }
  if (right === null || right === undefined) {
    return -1;
  }
  return left - right;
}

function groupFixturesByDate(fixtures: FixtureSummary[]) {
  const groups = new Map<string, FixtureSummary[]>();
  fixtures.forEach((fixture) => {
    const key = dateKey(fixture.fixtureDate);
    groups.set(key, [...(groups.get(key) ?? []), fixture]);
  });
  return Array.from(groups.entries());
}

function dateKey(value: string | null) {
  if (!value) {
    return "unknown";
  }
  const date = parseFixtureDate(value);
  return date ? formatKoreaDate(date) : value.slice(0, 10);
}

function fixtureTime(value: string | null) {
  return parseFixtureDate(value)?.getTime() ?? Number.MAX_SAFE_INTEGER;
}

function parseFixtureDate(value: string | null) {
  if (!value) {
    return null;
  }
  const text = /Z$|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatKoreaDate(date: Date) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function dateGroupTitle(value: string) {
  if (value === "unknown") {
    return "날짜 미정";
  }
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
}

function formatTime(value: string | null) {
  const date = parseFixtureDate(value);
  if (!date) {
    return value?.slice(11, 16) || "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function scoreText(fixture: FixtureSummary) {
  return fixture.fixtureStatus === "SCHEDULED" ? "vs" : `${fixture.homeScore}:${fixture.awayScore}`;
}

function ratingText(value: number) {
  return value > 0 ? value.toFixed(1) : "-";
}

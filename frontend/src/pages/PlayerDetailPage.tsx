import { useEffect, useMemo, useRef, useState } from "react";
import { Activity, CalendarDays, ChevronDown, ChevronLeft, ChevronRight, Shirt, Star, UserRound } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import {
  ApiError,
  addFavoritePlayer,
  fetchFavoriteDashboard,
  fetchPlayerPanel,
  removeFavoritePlayer,
  type PlayerMatchStat,
  type PlayerPanel,
  type PlayerProfile,
  type PlayerSeasonSummary,
} from "../api";
import type { AuthStatus } from "../App";

type LoadState = {
  data: PlayerPanel | null;
  error: string;
  isLoading: boolean;
};

const MATCHES_PER_PAGE = 5;

export function PlayerDetailPage({ authStatus, season }: { authStatus: AuthStatus; season: number }) {
  const { playerId } = useParams();
  const numericPlayerId = Number(playerId);
  const loadRequestId = useRef(0);
  const [state, setState] = useState<LoadState>({
    data: null,
    error: "",
    isLoading: true,
  });
  const [isFavorite, setIsFavorite] = useState(false);
  const [isFavoriteLoading, setIsFavoriteLoading] = useState(false);
  const [favoriteError, setFavoriteError] = useState("");

  useEffect(() => {
    if (!Number.isFinite(numericPlayerId) || numericPlayerId <= 0) {
      setState({
        data: null,
        error: "올바른 선수 ID가 아닙니다.",
        isLoading: false,
      });
      return;
    }

    const requestId = loadRequestId.current + 1;
    loadRequestId.current = requestId;
    let isCurrent = true;
    const isLatest = () => isCurrent && loadRequestId.current === requestId;
    setState({ data: null, error: "", isLoading: true });

    fetchPlayerPanel(numericPlayerId)
      .then((data) => {
        if (isLatest()) {
          setState({ data, error: "", isLoading: false });
        }
      })
      .catch((error) => {
        if (isLatest()) {
          setState({
            data: null,
            error: error instanceof Error ? error.message : "선수 정보를 불러오지 못했습니다.",
            isLoading: false,
          });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [numericPlayerId]);

  useEffect(() => {
    if (!Number.isFinite(numericPlayerId) || numericPlayerId <= 0) {
      setIsFavorite(false);
      return;
    }
    if (authStatus !== "authenticated") {
      setIsFavorite(false);
      setFavoriteError("");
      return;
    }

    let isCurrent = true;
    setFavoriteError("");

    fetchFavoriteDashboard(season)
      .then((dashboard) => {
        if (isCurrent) {
          setIsFavorite(dashboard.players.some((player) => player.playerId === numericPlayerId));
        }
      })
      .catch((error) => {
        if (isCurrent) {
          setIsFavorite(false);
          if (!(error instanceof ApiError && error.status === 401)) {
            setFavoriteError("즐겨찾기 상태를 확인하지 못했습니다.");
          }
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [authStatus, numericPlayerId, season]);

  async function toggleFavorite() {
    if (!Number.isFinite(numericPlayerId) || numericPlayerId <= 0) {
      return;
    }
    if (authStatus !== "authenticated") {
      setFavoriteError("로그인이 필요합니다.");
      return;
    }

    setIsFavoriteLoading(true);
    setFavoriteError("");
    try {
      const dashboard = isFavorite
        ? await removeFavoritePlayer(numericPlayerId, season)
        : await addFavoritePlayer(numericPlayerId, season);
      setIsFavorite(dashboard.players.some((player) => player.playerId === numericPlayerId));
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFavoriteError("로그인이 필요합니다.");
      } else {
        setFavoriteError("즐겨찾기 변경에 실패했습니다.");
      }
    } finally {
      setIsFavoriteLoading(false);
    }
  }

  if (state.isLoading) {
    return (
      <section className="league-content player-detail-page">
        <article className="panel placeholder-panel">선수 정보를 불러오는 중입니다.</article>
      </section>
    );
  }

  if (state.error || !state.data?.profile) {
    return (
      <section className="league-content player-detail-page">
        <article className="panel placeholder-panel">
          <p className="eyebrow">Player Detail</p>
          <h2>선수 정보를 불러오지 못했습니다.</h2>
          <p className="muted">{state.error || "잠시 후 다시 시도해 주세요."}</p>
          <Link className="primary-link fixture-back-link" to="/league/overview">
            리그 홈으로
          </Link>
        </article>
      </section>
    );
  }

  const seasons = state.data.seasons ?? [];
  const matches = state.data.matches ?? [];

  return (
    <section className="league-content player-detail-page">
      <PlayerHero
        favoriteError={favoriteError}
        isFavorite={isFavorite}
        isFavoriteLoading={isFavoriteLoading}
        onToggleFavorite={toggleFavorite}
        profile={state.data.profile}
        seasons={seasons}
      />
      <SeasonSummaryPanel matches={matches} seasons={seasons} />
      <RecentMatchesPanel matches={matches} />
    </section>
  );
}

function PlayerHero({
  profile,
  seasons,
  isFavorite,
  isFavoriteLoading,
  favoriteError,
  onToggleFavorite,
}: {
  profile: PlayerProfile;
  seasons: PlayerSeasonSummary[];
  isFavorite: boolean;
  isFavoriteLoading: boolean;
  favoriteError: string;
  onToggleFavorite: () => void;
}) {
  const primaryTeam = profile.teamName
    ? {
        id: profile.teamId,
        logoUrl: profile.teamLogoUrl,
        name: profile.teamName,
      }
    : latestTeam(seasons);

  return (
    <article className="panel player-hero">
      <div className="player-photo-wrap">
        {profile.photoUrl ? (
          <img src={profile.photoUrl} alt="" className="player-photo" />
        ) : (
          <span className="player-photo placeholder" aria-hidden="true">
            <UserRound size={44} />
          </span>
        )}
      </div>
      <div className="player-hero-main">
        <p className="eyebrow">{profile.position ?? "Player"}</p>
        <div className="detail-title-row">
          <h2>{profile.playerName ?? "-"}</h2>
          <FavoriteToggleButton
            isActive={isFavorite}
            isLoading={isFavoriteLoading}
            onClick={onToggleFavorite}
            typeLabel="선수"
          />
        </div>
        {favoriteError ? <p className="favorite-inline-error">{favoriteError}</p> : null}
        <div className="player-meta-list">
          <span>{profile.nationality ?? "국적 정보 없음"}</span>
          <span>{profile.age ? `${profile.age}세` : "나이 정보 없음"}</span>
          <span>{profile.height ?? "신장 정보 없음"}</span>
          <span>{profile.weight ?? "체중 정보 없음"}</span>
        </div>
      </div>
      <div className="player-team-card">
        {primaryTeam?.logoUrl ? <img src={primaryTeam.logoUrl} alt="" className="team-logo large" /> : null}
        <div>
          {primaryTeam?.id ? (
            <Link className="team-name-link" to={`/teams/${primaryTeam.id}`}>
              {primaryTeam.name ?? "소속팀 정보 없음"}
            </Link>
          ) : (
            <strong>{primaryTeam?.name ?? "소속팀 정보 없음"}</strong>
          )}
          <p>
            {profile.backNumber ? `No. ${profile.backNumber}` : "등번호 정보 없음"}
            {profile.birthDate ? ` · ${formatDateOnly(profile.birthDate)}` : ""}
          </p>
        </div>
      </div>
    </article>
  );
}

function latestTeam(seasons: PlayerSeasonSummary[]) {
  const latestSeason = seasons.find((season) => season.teams?.length);
  const latestTeamSummary = latestSeason?.teams[0];
  if (!latestTeamSummary) {
    return null;
  }

  return {
    id: latestTeamSummary.teamId,
    logoUrl: latestTeamSummary.teamLogoUrl,
    name: latestTeamSummary.teamName,
  };
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

function SeasonSummaryPanel({
  seasons,
  matches,
}: {
  seasons: PlayerSeasonSummary[];
  matches: PlayerMatchStat[];
}) {
  const [expandedSeason, setExpandedSeason] = useState<number | null>(null);

  if (!seasons.length) {
    return (
      <article className="panel detail-panel">
        <div className="empty-state">시즌 요약 정보가 없습니다.</div>
      </article>
    );
  }

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading player-panel-title">
        <Shirt size={19} aria-hidden="true" />
        <h2>시즌 요약</h2>
      </div>
      <div className="player-season-accordion">
        {seasons.map((season) => {
          const seasonKey = season.season ?? -1;
          const isOpen = expandedSeason === seasonKey;

          return (
            <SeasonAccordionItem
              isOpen={isOpen}
              key={seasonKey}
              matches={matches.filter((match) => match.season === season.season)}
              onToggle={() => setExpandedSeason(isOpen ? null : seasonKey)}
              season={season}
            />
          );
        })}
      </div>
    </article>
  );
}

function SeasonAccordionItem({
  season,
  matches,
  isOpen,
  onToggle,
}: {
  season: PlayerSeasonSummary;
  matches: PlayerMatchStat[];
  isOpen: boolean;
  onToggle: () => void;
}) {
  const teamGroups = useMemo(
    () => groupSeasonMatchesByTeam(matches, season.teams ?? []),
    [matches, season.teams],
  );

  return (
    <section className={`player-season-item${isOpen ? " open" : ""}`}>
      <button className="player-season-toggle" type="button" onClick={onToggle} aria-expanded={isOpen}>
        <div>
          <strong>{season.season ?? "-"} 시즌</strong>
          <span>
            {numberText(season.totalFixtures)}경기 · {numberText(season.goals)}골 · {numberText(season.assists)}도움
          </span>
        </div>
        <div className="player-season-toggle-meta">
          <span>평점 {ratingText(season.averageRating)}</span>
          <ChevronDown size={18} aria-hidden="true" />
        </div>
      </button>

      {isOpen ? (
        <div className="player-season-body">
          <div className="player-stat-chips">
            <StatChip label="경기" value={season.totalFixtures} />
            <StatChip label="출전 시간" value={season.minutesPlayed} />
            <StatChip label="슈팅" value={season.shots} />
            <StatChip label="유효 슈팅" value={season.shotsOnTarget} />
            <StatChip label="키패스" value={season.keyPasses} />
            <StatChip label="경고" value={season.yellowCards} />
            <StatChip label="퇴장" value={season.redCards} />
          </div>

          <SeasonTeamGroups groups={teamGroups} />
        </div>
      ) : null}
    </section>
  );
}

function StatChip({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{numberText(value)}</strong>
    </div>
  );
}

type SeasonTeamMatchGroup = {
  key: string;
  teamId: number | null;
  teamName: string | null;
  teamLogoUrl: string | null;
  summary: PlayerSeasonSummary["teams"][number] | null;
  matches: PlayerMatchStat[];
};

function groupSeasonMatchesByTeam(
  matches: PlayerMatchStat[],
  teams: PlayerSeasonSummary["teams"],
): SeasonTeamMatchGroup[] {
  const summariesById = new Map(teams.map((team) => [team.teamId, team]));
  const summariesByName = new Map(teams.map((team) => [team.teamName ?? "", team]));
  const groups = new Map<string, SeasonTeamMatchGroup>();

  sortMatchesByDateAsc(matches).forEach((match) => {
    const summary = match.teamId ? summariesById.get(match.teamId) ?? null : summariesByName.get(match.teamName ?? "") ?? null;
    const key = match.teamId ? `team-${match.teamId}` : `team-${match.teamName ?? "unknown"}`;
    const group = groups.get(key) ?? {
      key,
      teamId: match.teamId,
      teamName: match.teamName ?? summary?.teamName ?? null,
      teamLogoUrl: summary?.teamLogoUrl ?? null,
      summary,
      matches: [],
    };

    group.matches.push(match);
    groups.set(key, group);
  });

  teams.forEach((team) => {
    const key = `team-${team.teamId}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        teamId: team.teamId,
        teamName: team.teamName,
        teamLogoUrl: team.teamLogoUrl,
        summary: team,
        matches: [],
      });
    }
  });

  return Array.from(groups.values());
}

function sortMatchesByDateAsc(matches: PlayerMatchStat[]) {
  return matches.slice().sort((left, right) => {
    const leftTime = matchTime(left);
    const rightTime = matchTime(right);

    return leftTime - rightTime || left.fixtureId - right.fixtureId;
  });
}

function matchTime(match: PlayerMatchStat) {
  if (!match.fixtureDate) {
    return Number.MAX_SAFE_INTEGER;
  }

  const date = new Date(hasExplicitTimeZone(match.fixtureDate) ? match.fixtureDate : `${match.fixtureDate}+09:00`);
  return Number.isNaN(date.getTime()) ? Number.MAX_SAFE_INTEGER : date.getTime();
}

function SeasonTeamGroups({ groups }: { groups: SeasonTeamMatchGroup[] }) {
  if (!groups.length) {
    return <div className="empty-state compact">해당 시즌 경기 기록이 없습니다.</div>;
  }

  return (
    <div className="player-season-team-groups">
      {groups.map((group) => (
        <SeasonTeamGroup group={group} key={group.key} />
      ))}
    </div>
  );
}

function SeasonTeamGroup({ group }: { group: SeasonTeamMatchGroup }) {
  const [page, setPage] = useState(1);
  const pageCount = Math.max(1, Math.ceil(group.matches.length / MATCHES_PER_PAGE));
  const currentPage = Math.min(page, pageCount);
  const visibleMatches = group.matches.slice((currentPage - 1) * MATCHES_PER_PAGE, currentPage * MATCHES_PER_PAGE);
  const summary = group.summary;

  useEffect(() => {
    setPage(1);
  }, [group.key]);

  return (
    <section className="player-season-team-group">
      <div className="player-team-row season-team-heading">
        {group.teamLogoUrl ? <img src={group.teamLogoUrl} alt="" className="team-logo" /> : <span className="team-logo placeholder" aria-hidden="true" />}
        {group.teamId ? (
          <Link className="team-name-link" to={`/teams/${group.teamId}`}>
            {group.teamName ?? "-"}
          </Link>
        ) : (
          <strong>{group.teamName ?? "-"}</strong>
        )}
        <span>
          {summary ? `${summary.totalFixtures}경기 · ${summary.goals}G ${summary.assists}A` : `${group.matches.length}경기`}
        </span>
      </div>

      <SeasonMatchList matches={visibleMatches} />
      {group.matches.length > MATCHES_PER_PAGE ? (
        <div className="player-season-pager">
          <button type="button" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1}>
            <ChevronLeft size={16} aria-hidden="true" />
            이전
          </button>
          <strong>
            {currentPage} / {pageCount}
          </strong>
          <button
            type="button"
            onClick={() => setPage((value) => Math.min(pageCount, value + 1))}
            disabled={currentPage === pageCount}
          >
            다음
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      ) : null}
    </section>
  );
}

function SeasonMatchList({ matches }: { matches: PlayerMatchStat[] }) {
  if (!matches.length) {
    return <div className="empty-state compact">이 팀 소속 경기 기록이 없습니다.</div>;
  }

  return (
    <div className="player-match-list season">
      {matches.map((match) => (
        <PlayerMatchRow match={match} key={match.fixtureId} />
      ))}
    </div>
  );
}

function RecentMatchesPanel({ matches }: { matches: PlayerMatchStat[] }) {
  if (!matches.length) {
    return (
      <article className="panel detail-panel">
        <div className="empty-state">최근 경기 기록이 없습니다.</div>
      </article>
    );
  }

  return (
    <article className="panel detail-panel">
      <div className="detail-panel-heading player-panel-title">
        <CalendarDays size={19} aria-hidden="true" />
        <h2>최근 경기</h2>
      </div>
      <div className="player-match-list">
        {matches.slice(0, 8).map((match) => (
          <PlayerMatchRow match={match} key={match.fixtureId} />
        ))}
      </div>
    </article>
  );
}

function PlayerMatchRow({ match }: { match: PlayerMatchStat }) {
  const homeTeamName = match.homeTeamName ?? inferHomeTeam(match);
  const awayTeamName = match.awayTeamName ?? inferAwayTeam(match);
  const homeScore = match.homeScore ?? inferHomeScore(match);
  const awayScore = match.awayScore ?? inferAwayScore(match);

  return (
    <Link className="player-match-row" to={`/fixtures/${match.fixtureId}`}>
      <time>{formatDate(match.fixtureDate)}</time>
      <div className="player-match-teams" aria-label="홈팀과 원정팀">
        <strong>
          <span>홈</span>
          {homeTeamName ?? "-"}
        </strong>
        <span>
          {numberText(homeScore)}:{numberText(awayScore)}
        </span>
        <strong>
          <span>원정</span>
          {awayTeamName ?? "-"}
        </strong>
      </div>
      <div className="player-match-stats">
        <span>{numberText(match.minutesPlayed)}분</span>
        <span>{numberText(match.goals)}G</span>
        <span>{numberText(match.assists)}A</span>
        <span>
          <Activity size={14} aria-hidden="true" />
          {ratingText(match.rating)}
        </span>
      </div>
    </Link>
  );
}

function inferHomeTeam(match: PlayerMatchStat) {
  return match.teamName;
}

function inferAwayTeam(match: PlayerMatchStat) {
  return match.opponentTeamName;
}

function inferHomeScore(match: PlayerMatchStat) {
  return match.teamScore;
}

function inferAwayScore(match: PlayerMatchStat) {
  return match.opponentScore;
}

function formatDate(value: string | null) {
  if (!value) {
    return "-";
  }

  const date = new Date(hasExplicitTimeZone(value) ? value : `${value}+09:00`);
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 10) || "-";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
  }).format(date);
}

function formatDateOnly(value: string) {
  const date = new Date(`${value}T00:00:00+09:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(date);
}

function hasExplicitTimeZone(value: string) {
  return /(?:z|[+-]\d{2}:?\d{2})$/i.test(value);
}

function ratingText(value: number | null | undefined) {
  if (value === null || value === undefined || value === 0) {
    return "-";
  }
  return value.toFixed(1);
}

function numberText(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : String(value);
}

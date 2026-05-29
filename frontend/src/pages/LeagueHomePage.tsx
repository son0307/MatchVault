import { useEffect, useMemo, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Star, Trophy } from "lucide-react";
import { Link } from "react-router-dom";
import type { AuthStatus } from "../App";
import {
  ApiError,
  fetchFavoriteDashboard,
  fetchFixturesByDate,
  fetchStandings,
  type FavoriteDashboard,
  type FavoritePlayerCard,
  type FavoriteTeamCard,
  type FixtureSummary,
  type TeamStanding,
} from "../api";

export function LeagueHomePage({ authStatus, season }: { authStatus: AuthStatus; season: number }) {
  const [selectedDate, setSelectedDate] = useState(todayKoreaDateKey());
  const [standings, setStandings] = useState<TeamStanding[]>([]);
  const [fixtures, setFixtures] = useState<FixtureSummary[]>([]);
  const [favorites, setFavorites] = useState<FavoriteDashboard | null>(null);
  const [favoritesNeedLogin, setFavoritesNeedLogin] = useState(false);
  const [isLoadingStandings, setIsLoadingStandings] = useState(true);
  const [isLoadingFixtures, setIsLoadingFixtures] = useState(true);
  const [isLoadingFavorites, setIsLoadingFavorites] = useState(true);
  const [standingsError, setStandingsError] = useState("");
  const [fixturesError, setFixturesError] = useState("");
  const [favoritesError, setFavoritesError] = useState("");

  const rankingRows = useMemo(
    () =>
      standings
        .slice()
        .sort((a, b) => valueOf(a.rank) - valueOf(b.rank))
        .slice(0, 20),
    [standings],
  );

  useEffect(() => {
    void loadStandings();
  }, [season]);

  useEffect(() => {
    if (authStatus === "checking") {
      setIsLoadingFavorites(true);
      setFavoritesError("");
      return;
    }

    if (authStatus === "guest") {
      setFavorites(null);
      setFavoritesNeedLogin(true);
      setFavoritesError("");
      setIsLoadingFavorites(false);
      return;
    }

    void loadFavorites();
  }, [authStatus, season]);

  useEffect(() => {
    void loadFixtures(selectedDate);
  }, [selectedDate, season]);

  async function loadStandings() {
    setIsLoadingStandings(true);
    setStandingsError("");
    try {
      setStandings(await fetchStandings(season));
    } catch (error) {
      setStandings([]);
      setStandingsError(error instanceof Error ? error.message : "팀 랭킹을 불러오지 못했습니다.");
    } finally {
      setIsLoadingStandings(false);
    }
  }

  async function loadFixtures(dateKey: string) {
    setIsLoadingFixtures(true);
    setFixturesError("");
    try {
      const response = await fetchFixturesByDate(season, dateKey);
      setFixtures(response.content ?? []);
    } catch (error) {
      setFixtures([]);
      setFixturesError(error instanceof Error ? error.message : "경기 일정을 불러오지 못했습니다.");
    } finally {
      setIsLoadingFixtures(false);
    }
  }

  async function loadFavorites() {
    setIsLoadingFavorites(true);
    setFavoritesError("");
    setFavoritesNeedLogin(false);
    try {
      setFavorites(await fetchFavoriteDashboard(season));
    } catch (error) {
      setFavorites(null);
      if (error instanceof ApiError && error.status === 401) {
        setFavoritesNeedLogin(true);
      } else {
        setFavoritesError(error instanceof Error ? error.message : "즐겨찾기를 불러오지 못했습니다.");
      }
    } finally {
      setIsLoadingFavorites(false);
    }
  }

  function moveDate(dayDelta: number) {
    setSelectedDate(addDaysToDateKey(selectedDate, dayDelta));
  }

  return (
    <section className="league-home">
      <div className="league-home-main">
        <article className="panel schedule-panel">
          <div className="panel-heading schedule-heading">
            <div>
              <div className="panel-title-with-icon">
                <CalendarDays size={20} aria-hidden="true" />
                <h2>경기 일정</h2>
              </div>
              <p className="panel-subtitle">{dateGroupTitle(selectedDate)}</p>
            </div>
            <div className="date-controls" aria-label="경기 날짜 이동">
              <button type="button" onClick={() => moveDate(-1)} aria-label="이전 날짜" title="이전 날짜">
                <ChevronLeft size={18} aria-hidden="true" />
              </button>
              <button type="button" onClick={() => setSelectedDate(todayKoreaDateKey())}>
                오늘
              </button>
              <button type="button" onClick={() => moveDate(1)} aria-label="다음 날짜" title="다음 날짜">
                <ChevronRight size={18} aria-hidden="true" />
              </button>
            </div>
          </div>
          {fixturesError ? <div className="section-error">{fixturesError}</div> : null}
          {isLoadingFixtures ? (
            <div className="empty-state">경기 일정을 불러오는 중입니다.</div>
          ) : (
            <FixtureSchedule fixtures={fixtures} />
          )}
        </article>

        <article className="panel favorites-panel">
          <div className="panel-heading">
            <div className="panel-title-with-icon">
              <Star size={20} aria-hidden="true" />
              <h2>즐겨찾기</h2>
            </div>
          </div>
          {favoritesError ? <div className="section-error">{favoritesError}</div> : null}
          {isLoadingFavorites ? (
            <div className="empty-state">즐겨찾기를 확인하는 중입니다.</div>
          ) : favoritesNeedLogin ? (
            <FavoriteLoginStateWithLink />
          ) : (
            <FavoritesPreview favorites={favorites} />
          )}
        </article>
      </div>

      <aside className="league-home-side">
        <article className="panel ranking-panel">
          <div className="panel-heading">
            <div className="panel-title-with-icon">
              <Trophy size={20} aria-hidden="true" />
              <h2>팀 랭킹</h2>
            </div>
          </div>
          {standingsError ? <div className="section-error">{standingsError}</div> : null}
          {isLoadingStandings ? (
            <div className="empty-state">팀 랭킹을 불러오는 중입니다.</div>
          ) : (
            <CompactRanking standings={rankingRows} />
          )}
        </article>
      </aside>
    </section>
  );
}

function CompactRanking({ standings }: { standings: TeamStanding[] }) {
  if (!standings.length) {
    return <div className="empty-state">랭킹 데이터가 없습니다.</div>;
  }

  return (
    <div className="compact-ranking">
      {standings.map((standing) => (
        <div className="compact-ranking-row" key={standing.team?.id ?? standing.rank}>
          <span className="compact-rank">{standing.rank ?? "-"}</span>
          {standing.team?.logo ? (
            <img src={standing.team.logo} alt="" className="team-logo" />
          ) : (
            <span className="team-logo placeholder" aria-hidden="true" />
          )}
          <strong>{standing.team?.name ?? "-"}</strong>
          <b>{standing.points ?? 0}</b>
        </div>
      ))}
    </div>
  );
}

function FixtureSchedule({ fixtures }: { fixtures: FixtureSummary[] }) {
  if (!fixtures.length) {
    return <div className="empty-state">선택한 날짜에 등록된 경기가 없습니다.</div>;
  }

  return (
    <div className="home-fixture-list">
      {fixtures.map((fixture) => (
        <Link className="home-fixture-card" key={fixture.fixtureId} to={`/fixtures/${fixture.fixtureId}`}>
          <time>{formatTime(fixture.fixtureDate)}</time>
          <div className="home-fixture-teams">
            <strong>{fixture.homeTeamName ?? "-"}</strong>
            <span>{scoreText(fixture)}</span>
            <strong>{fixture.awayTeamName ?? "-"}</strong>
          </div>
          <span className="status-pill">{fixture.fixtureStatus ?? "예정"}</span>
        </Link>
      ))}
    </div>
  );
}

function FavoriteLoginState() {
  return (
    <div className="favorite-empty">
      <strong>로그인 후 즐겨찾기를 확인할 수 있습니다.</strong>
      <p>좋아하는 팀과 선수를 저장하면 최근 경기, 다음 경기, 시즌 기록이 이곳에 표시됩니다.</p>
    </div>
  );
}

function FavoriteLoginStateWithLink() {
  return (
    <div className="favorite-empty">
      <strong>로그인 후 즐겨찾기를 확인할 수 있습니다.</strong>
      <p>좋아하는 팀과 선수를 저장하면 최근 경기, 다음 경기, 시즌 기록을 한곳에서 확인할 수 있습니다.</p>
      <Link className="favorite-login-link" to="/login">
        로그인
      </Link>
    </div>
  );
}

function FavoritesPreview({ favorites }: { favorites: FavoriteDashboard | null }) {
  const teams = favorites?.teams ?? [];
  const players = favorites?.players ?? [];

  if (!teams.length && !players.length) {
    return (
      <div className="favorite-empty">
        <strong>아직 즐겨찾기한 팀이나 선수가 없습니다.</strong>
        <p>로그인 기능을 연결한 뒤 즐겨찾기 데이터가 이 카드에 표시됩니다.</p>
      </div>
    );
  }

  return (
    <div className="favorite-preview-grid">
      <section>
        <h3>팀</h3>
        <div className="favorite-mini-list">
          {teams.map((team) => (
            <FavoriteTeamItem team={team} key={team.teamId} />
          ))}
        </div>
      </section>
      <section>
        <h3>선수</h3>
        <div className="favorite-mini-list">
          {players.map((player) => (
            <FavoritePlayerItem player={player} key={player.playerId} />
          ))}
        </div>
      </section>
    </div>
  );
}

function FavoriteTeamItem({ team }: { team: FavoriteTeamCard }) {
  return (
    <article className="favorite-mini-card">
      <div className="favorite-mini-head">
        {team.logoUrl ? (
          <img src={team.logoUrl} alt="" className="team-logo" />
        ) : (
          <span className="team-logo placeholder" aria-hidden="true" />
        )}
        <div>
          <strong>{team.teamName ?? "-"}</strong>
          <p>{numberText(team.rank)}위 · {numberText(team.points)}점 · 최근 {team.form ?? "-"}</p>
        </div>
      </div>
      {team.liveFixture ? (
        <p className="favorite-line live">LIVE {team.liveFixture.elapsed ?? "-"}' · {team.liveFixture.homeTeamName} {team.liveFixture.homeScore}:{team.liveFixture.awayScore} {team.liveFixture.awayTeamName}</p>
      ) : team.nextFixture ? (
        <p className="favorite-line">다음 경기 · {team.nextFixture.homeTeamName} vs {team.nextFixture.awayTeamName}</p>
      ) : (
        <p className="favorite-line muted">예정된 경기 정보가 없습니다.</p>
      )}
    </article>
  );
}

function FavoritePlayerItem({ player }: { player: FavoritePlayerCard }) {
  return (
    <article className="favorite-mini-card">
      <div className="favorite-mini-head">
        {player.photoUrl ? (
          <img src={player.photoUrl} alt="" className="player-thumb" />
        ) : (
          <span className="player-thumb placeholder" aria-hidden="true" />
        )}
        <div>
          <strong>{player.playerName ?? "-"}</strong>
          <p>{player.position ?? "Player"} · {player.seasonStat?.teamName ?? "-"}</p>
        </div>
      </div>
      <p className="favorite-line">
        시즌 {numberText(player.seasonStat?.goals)}골 {numberText(player.seasonStat?.assists)}도움 · 평점 {numberText(player.seasonStat?.rating)}
      </p>
    </article>
  );
}

function todayKoreaDateKey() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function addDaysToDateKey(dateKey: string, dayDelta: number) {
  const date = new Date(`${dateKey}T00:00:00+09:00`);
  date.setUTCDate(date.getUTCDate() + dayDelta);

  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function dateGroupTitle(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00+09:00`);
  const today = new Date(`${todayKoreaDateKey()}T00:00:00+09:00`);
  const diffDays = Math.round((date.getTime() - today.getTime()) / 86400000);
  const formatted = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);

  if (diffDays === 0) {
    return `오늘, ${formatted}`;
  }
  if (diffDays === 1) {
    return `내일, ${formatted}`;
  }
  if (diffDays === -1) {
    return `어제, ${formatted}`;
  }
  return formatted;
}

function formatTime(value: string | null) {
  if (!value) {
    return "-";
  }

  const date = new Date(/Z$|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`);
  if (Number.isNaN(date.getTime())) {
    return value.slice(11, 16) || "-";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function scoreText(fixture: FixtureSummary) {
  if (fixture.fixtureStatus === "SCHEDULED") {
    return "vs";
  }
  return `${fixture.homeScore}:${fixture.awayScore}`;
}

function valueOf(value: number | null | undefined) {
  return value ?? 0;
}

function numberText(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : String(value);
}

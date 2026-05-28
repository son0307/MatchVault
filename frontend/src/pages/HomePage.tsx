import { useEffect, useMemo, useState } from "react";
import { CalendarDays, RefreshCw, Trophy } from "lucide-react";
import { NavLink } from "react-router-dom";
import { fetchHomeSummary, type HomeSummary } from "../api";

const DEFAULT_SEASON = 2025;

export function HomePage() {
  const [season, setSeason] = useState(DEFAULT_SEASON);
  const [summary, setSummary] = useState<HomeSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const topStandings = useMemo(
    () => (summary?.standings ?? []).slice(0, 6),
    [summary],
  );

  async function loadSummary(targetSeason = season) {
    setIsLoading(true);
    setErrorMessage("");

    try {
      setSummary(await fetchHomeSummary(targetSeason));
    } catch (error) {
      setSummary(null);
      setErrorMessage(
        error instanceof Error ? error.message : "홈 정보를 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadSummary(DEFAULT_SEASON);
  }, []);

  function updateSeason(value: string) {
    const nextSeason = Number(value);
    setSeason(nextSeason);
    if (Number.isFinite(nextSeason)) {
      void loadSummary(nextSeason);
    }
  }

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div>
          <p className="eyebrow">Premier League Dashboard</p>
          <h1>Soccer Streaming</h1>
        </div>
        <div className="toolbar" aria-label="시즌과 새로고침">
          <NavLink className="primary-link" to="/league/standings">
            리그 순위
          </NavLink>
          <label className="season-field">
            <span>Season</span>
            <input
              type="number"
              value={season}
              min="2000"
              max="2100"
              onChange={(event) => updateSeason(event.target.value)}
            />
          </label>
          <button
            className="icon-button"
            type="button"
            onClick={() => void loadSummary()}
            aria-label="새로고침"
            title="새로고침"
          >
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </div>
      </header>

      {errorMessage ? <div className="notice error">{errorMessage}</div> : null}

      <section className="summary-grid">
        <article className="panel">
          <div className="panel-heading">
            <CalendarDays size={20} aria-hidden="true" />
            <h2>오늘 경기</h2>
          </div>
          {isLoading ? (
            <div className="empty-state">경기 정보를 불러오는 중입니다.</div>
          ) : (
            <FixtureList fixtures={summary?.todayFixtures ?? []} />
          )}
        </article>

        <article className="panel">
          <div className="panel-heading">
            <Trophy size={20} aria-hidden="true" />
            <h2>상위 순위</h2>
          </div>
          {isLoading ? (
            <div className="empty-state">순위 정보를 불러오는 중입니다.</div>
          ) : (
            <HomeStandingList standings={topStandings} />
          )}
        </article>
      </section>
    </main>
  );
}

function FixtureList({ fixtures }: { fixtures: HomeSummary["todayFixtures"] }) {
  if (!fixtures.length) {
    return <div className="empty-state">오늘 등록된 경기가 없습니다.</div>;
  }

  return (
    <div className="fixture-list">
      {fixtures.map((fixture) => (
        <article className="fixture-row" key={fixture.fixtureId}>
          <time>{formatTime(fixture.fixtureDate)}</time>
          <div className="fixture-teams">
            <strong>{fixture.homeTeamName ?? "-"}</strong>
            <span>vs</span>
            <strong>{fixture.awayTeamName ?? "-"}</strong>
          </div>
          <div className="score-box">
            {fixture.homeScore}:{fixture.awayScore}
          </div>
          <span className="status-pill">{fixture.fixtureStatus ?? "예정"}</span>
        </article>
      ))}
    </div>
  );
}

function HomeStandingList({ standings }: { standings: HomeSummary["standings"] }) {
  if (!standings.length) {
    return <div className="empty-state">순위 데이터가 없습니다.</div>;
  }

  return (
    <div className="standing-list">
      {standings.map((standing) => (
        <article className="standing-row" key={standing.team?.id ?? standing.rank}>
          <span className="rank">{standing.rank ?? "-"}</span>
          {standing.team?.logo ? (
            <img src={standing.team.logo} alt="" className="team-logo" />
          ) : (
            <span className="team-logo placeholder" aria-hidden="true" />
          )}
          <strong>{standing.team?.name ?? "-"}</strong>
          <span className="muted">{standing.all?.played ?? 0}경기</span>
          <b>{standing.points ?? 0}점</b>
        </article>
      ))}
    </div>
  );
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

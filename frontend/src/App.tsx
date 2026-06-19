import { Children, useEffect, useRef, useState, type ReactNode } from "react";
import { Search, X } from "lucide-react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useNavigate } from "react-router-dom";
import {
  ApiError,
  fetchCurrentUser,
  fetchLeagueSeasons,
  logout,
  searchGlobal,
  type CurrentUser,
  type FixtureSearchResult,
  type LeagueSeasonCoverage,
  type PlayerSearchResult,
  type SearchResponse,
  type SearchScope,
  type TeamSearchResult,
} from "./api";
import { formatFixtureDateTime } from "./dateUtils";
import { AuthPage } from "./pages/AuthPage";
import { AdminPage } from "./pages/AdminPage";
import { FixtureDetailPage } from "./pages/FixtureDetailPage";
import { LeagueFixturesPage } from "./pages/LeagueFixturesPage";
import { LeagueHomePage } from "./pages/LeagueHomePage";
import { LeaguePlayerStatsPage } from "./pages/LeaguePlayerStatsPage";
import { LeagueStandingsPage } from "./pages/LeagueStandingsPage";
import { MyPage } from "./pages/MyPage";
import { PlayerDetailPage } from "./pages/PlayerDetailPage";
import { TeamDetailPage } from "./pages/TeamDetailPage";

export type AuthStatus = "checking" | "authenticated" | "guest";

export type LeagueAuthState = {
  authStatus: AuthStatus;
  currentUser: CurrentUser | null;
  season: number;
  setSeason: (season: number) => void;
  setCurrentUser: (user: CurrentUser | null) => void;
  setAuthStatus: (status: AuthStatus) => void;
};

type LeagueLayoutProps = {
  children: ReactNode | ((state: LeagueAuthState) => ReactNode);
};

const DEFAULT_SEASON = 2025;

const leagueTabs = [
  { label: "홈", to: "/league/overview", enabled: true },
  { label: "순위", to: "/league/standings", enabled: true },
  { label: "경기", to: "/league/fixtures", enabled: true },
  { label: "플레이어 통계", to: "/league/player-stats", enabled: true },
  { label: "팀 통계", to: "/league/team-stats", enabled: false },
];

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/league/overview" replace />} />
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/signup" element={<AuthPage mode="signup" />} />
        <Route path="/league" element={<Navigate to="/league/overview" replace />} />
        <Route
          path="/league/overview"
          element={
            <LeagueLayout>
              {(authState) => <LeagueHomePage authStatus={authState.authStatus} season={authState.season} />}
            </LeagueLayout>
          }
        />
        <Route
          path="/league/standings"
          element={
            <LeagueLayout>{(authState) => <LeagueStandingsPage season={authState.season} />}</LeagueLayout>
          }
        />
        <Route
          path="/league/fixtures"
          element={<LeagueLayout>{(authState) => <LeagueFixturesPage season={authState.season} />}</LeagueLayout>}
        />
        <Route
          path="/league/player-stats"
          element={<LeagueLayout>{(authState) => <LeaguePlayerStatsPage season={authState.season} />}</LeagueLayout>}
        />
        <Route
          path="/fixtures/:fixtureId"
          element={
            <LeagueLayout>
              {(authState) => <FixtureDetailPage authStatus={authState.authStatus} season={authState.season} />}
            </LeagueLayout>
          }
        />
        <Route
          path="/mypage"
          element={
            <LeagueLayout>{(authState) => <MyPage authState={authState} season={authState.season} />}</LeagueLayout>
          }
        />
        <Route
          path="/admin"
          element={
            <LeagueLayout>{(authState) => <AdminPage authState={authState} />}</LeagueLayout>
          }
        />
        <Route
          path="/players/:playerId"
          element={
            <LeagueLayout>
              {(authState) => <PlayerDetailPage authStatus={authState.authStatus} season={authState.season} />}
            </LeagueLayout>
          }
        />
        <Route
          path="/teams/:teamId"
          element={
            <LeagueLayout>
              {(authState) => <TeamDetailPage authStatus={authState.authStatus} season={authState.season} />}
            </LeagueLayout>
          }
        />
        <Route path="/league/*" element={<Navigate to="/league/overview" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

const searchTabs: { label: string; value: SearchScope }[] = [
  { label: "전체", value: "all" },
  { label: "팀", value: "team" },
  { label: "선수", value: "player" },
  { label: "경기", value: "fixture" },
];

function GlobalSearch() {
  const navigate = useNavigate();
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<SearchScope>("all");
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (isExpanded) {
      inputRef.current?.focus();
    }
  }, [isExpanded]);

  useEffect(() => {
    function closeOnOutsideClick(event: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setIsExpanded(false);
      }
    }

    document.addEventListener("mousedown", closeOnOutsideClick);
    return () => document.removeEventListener("mousedown", closeOnOutsideClick);
  }, []);

  useEffect(() => {
    const keyword = query.trim();
    if (!isExpanded || !keyword) {
      setResult(null);
      setError("");
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError("");

    const timer = window.setTimeout(() => {
      searchGlobal(keyword, scope)
        .then((nextResult) => {
          if (isCurrent) {
            setResult(nextResult);
          }
        })
        .catch((nextError: unknown) => {
          if (isCurrent) {
            setResult(null);
            setError(nextError instanceof Error ? nextError.message : "검색 결과를 불러오지 못했습니다.");
          }
        })
        .finally(() => {
          if (isCurrent) {
            setIsLoading(false);
          }
        });
    }, 400);

    return () => {
      isCurrent = false;
      window.clearTimeout(timer);
    };
  }, [isExpanded, query, scope]);

  function closeSearch() {
    setIsExpanded(false);
  }

  function moveTo(path: string) {
    navigate(path);
    setIsExpanded(false);
    setQuery("");
    setResult(null);
  }

  const hasQuery = Boolean(query.trim());

  return (
    <div className={`global-search${isExpanded ? " expanded" : ""}`} ref={rootRef}>
      {isExpanded ? (
        <div className="global-search-panel">
          <div className="global-search-input-row">
            <Search size={18} aria-hidden="true" />
            <input
              ref={inputRef}
              type="search"
              value={query}
              autoComplete="off"
              placeholder="팀, 선수, 경기 검색"
              aria-label="팀, 선수, 경기 검색"
              onChange={(event) => setQuery(event.target.value)}
            />
            <button type="button" className="global-search-close" aria-label="검색 닫기" onClick={closeSearch}>
              <X size={17} aria-hidden="true" />
            </button>
          </div>
          <div className="global-search-tabs" role="tablist" aria-label="검색 범위">
            {searchTabs.map((tab) => (
              <button
                type="button"
                className={`global-search-tab${scope === tab.value ? " active" : ""}`}
                key={tab.value}
                onClick={() => setScope(tab.value)}
              >
                {tab.label}
              </button>
            ))}
          </div>
          {hasQuery ? (
            <div className="global-search-results" role="region" aria-live="polite">
              {isLoading ? <div className="global-search-state">검색 중...</div> : null}
              {!isLoading && error ? <div className="global-search-state error">{error}</div> : null}
              {!isLoading && !error && result ? <SearchResults result={result} onSelect={moveTo} /> : null}
            </div>
          ) : null}
        </div>
      ) : (
        <button type="button" className="global-search-trigger" aria-label="검색 열기" onClick={() => setIsExpanded(true)}>
          <Search size={19} aria-hidden="true" />
        </button>
      )}
    </div>
  );
}

function SearchResults({ result, onSelect }: { result: SearchResponse; onSelect: (path: string) => void }) {
  const teams = Array.isArray(result.teams) ? result.teams : [];
  const players = Array.isArray(result.players) ? result.players : [];
  const fixtures = Array.isArray(result.fixtures) ? result.fixtures : [];

  if (!teams.length && !players.length && !fixtures.length) {
    return <div className="global-search-state">검색 결과가 없습니다.</div>;
  }

  return (
    <>
      <SearchSection title="팀">
        {teams.map((team) => (
          <TeamSearchButton key={team.teamId} team={team} onSelect={onSelect} />
        ))}
      </SearchSection>
      <SearchSection title="선수">
        {players.map((player) => (
          <PlayerSearchButton key={player.playerId} player={player} onSelect={onSelect} />
        ))}
      </SearchSection>
      <SearchSection title="경기">
        {fixtures.map((fixture) => (
          <FixtureSearchButton key={fixture.fixtureId} fixture={fixture} onSelect={onSelect} />
        ))}
      </SearchSection>
    </>
  );
}

function SearchSection({ title, children }: { title: string; children: ReactNode }) {
  if (Children.count(children) === 0) {
    return null;
  }

  return (
    <section className="global-search-section">
      <h2>{title}</h2>
      <div className="global-search-list">{children}</div>
    </section>
  );
}

function TeamSearchButton({ team, onSelect }: { team: TeamSearchResult; onSelect: (path: string) => void }) {
  return (
    <button type="button" className="global-search-result" onClick={() => onSelect(`/teams/${team.teamId}`)}>
      <SearchImage src={team.logoUrl} alt={team.teamName} className="team-logo" />
      <span>
        <strong>{team.teamName ?? "-"}</strong>
        <small>{team.code ?? "Team"}</small>
      </span>
    </button>
  );
}

function PlayerSearchButton({ player, onSelect }: { player: PlayerSearchResult; onSelect: (path: string) => void }) {
  return (
    <button type="button" className="global-search-result" onClick={() => onSelect(`/players/${player.playerId}`)}>
      <SearchImage src={player.photoUrl} alt={player.playerName} className="player-thumb" />
      <span>
        <strong>{player.playerName ?? "-"}</strong>
        <small>{player.position ?? "Player"}</small>
      </span>
    </button>
  );
}

function FixtureSearchButton({
  fixture,
  onSelect,
}: {
  fixture: FixtureSearchResult;
  onSelect: (path: string) => void;
}) {
  return (
    <button
      type="button"
      className="global-search-result fixture"
      onClick={() => onSelect(`/fixtures/${fixture.fixtureId}`)}
    >
      <span>
        <strong>
          {fixture.homeTeamName ?? "-"} vs {fixture.awayTeamName ?? "-"}
        </strong>
        <small>
          {formatFixtureDateTime(fixture.fixtureDate, "날짜 미정")} · {fixture.fixtureStatus ?? "Fixture"}
        </small>
      </span>
      <b>{scoreText(fixture.homeScore, fixture.awayScore)}</b>
    </button>
  );
}

function SearchImage({
  src,
  alt,
  className,
}: {
  src: string | null;
  alt: string | null;
  className: string;
}) {
  return src ? <img className={className} src={src} alt={alt ?? ""} /> : <span className={`${className} placeholder`} />;
}

function scoreText(homeScore: number | null, awayScore: number | null) {
  if (homeScore === null || awayScore === null) {
    return "-";
  }

  return `${homeScore}:${awayScore}`;
}

function LeagueLayout({ children }: LeagueLayoutProps) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authStatus, setAuthStatus] = useState<AuthStatus>("checking");
  const [season, setSeason] = useState(DEFAULT_SEASON);
  const [authError, setAuthError] = useState("");
  const [seasonOptions, setSeasonOptions] = useState<LeagueSeasonCoverage[]>([]);
  const [seasonError, setSeasonError] = useState("");
  const [isLoadingSeasons, setIsLoadingSeasons] = useState(true);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    void loadCurrentUser();
    void loadLeagueSeasons();
  }, []);

  async function loadCurrentUser() {
    try {
      const user = await fetchCurrentUser();
      setCurrentUser(user);
      setAuthStatus("authenticated");
      setAuthError("");
    } catch (error) {
      setCurrentUser(null);
      setAuthStatus("guest");
      if (error instanceof ApiError && error.status === 401) {
        setAuthError("");
      } else {
        setAuthError("로그인 상태를 확인하지 못했습니다. 리그 정보는 계속 볼 수 있습니다.");
      }
    }
  }

  async function loadLeagueSeasons() {
    setIsLoadingSeasons(true);
    setSeasonError("");
    try {
      const response = await fetchLeagueSeasons();
      setSeasonOptions(response.seasons ?? []);
      if (Number.isInteger(response.currentSeason)) {
        setSeason(response.currentSeason);
      }
    } catch (error) {
      setSeasonOptions([]);
      setSeasonError(error instanceof Error ? error.message : "시즌 정보를 불러오지 못했습니다.");
    } finally {
      setIsLoadingSeasons(false);
    }
  }

  async function handleLogout() {
    setIsLoggingOut(true);
    setAuthError("");
    try {
      await logout();
      setCurrentUser(null);
      setAuthStatus("guest");
    } catch (error) {
      setAuthStatus(currentUser ? "authenticated" : "guest");
      setAuthError(error instanceof Error ? error.message : "로그아웃에 실패했습니다. 다시 시도해 주세요.");
    } finally {
      setIsLoggingOut(false);
    }
  }

  function renderAuthAction() {
    if (authStatus === "checking") {
      return <span className="auth-checking">로그인 확인 중</span>;
    }

    if (authStatus === "authenticated" && currentUser) {
      return (
        <div className="auth-status">
          {currentUser.role === "ADMIN" ? (
            <NavLink className="home-link" to="/admin">
              관리
            </NavLink>
          ) : null}
          <NavLink className="home-link" to="/mypage">
            {currentUser.nickname || currentUser.email}
          </NavLink>
          <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
            {isLoggingOut ? "로그아웃 중" : "로그아웃"}
          </button>
        </div>
      );
    }

    return (
      <NavLink className="home-link" to="/login">
        로그인
      </NavLink>
    );
  }

  function updateSeason(value: string) {
    const nextSeason = Number(value);
    if (Number.isInteger(nextSeason)) {
      setSeason(nextSeason);
    }
  }

  const authState: LeagueAuthState = { authStatus, currentUser, season, setSeason, setCurrentUser, setAuthStatus };

  return (
    <main className="app-shell league-shell">
      <header className="league-hero">
        <div>
          <p className="eyebrow">England</p>
          <h1>Premier League</h1>
        </div>
        <div className="league-header-actions">
          <GlobalSearch />
          <label className="season-field compact league-season-field">
            <span>현재 시즌</span>
            <select
              value={season}
              disabled={isLoadingSeasons || seasonOptions.length === 0}
              onChange={(event) => updateSeason(event.target.value)}
            >
              {seasonOptions.length ? (
                seasonOptions.map((option) => (
                  <option key={option.seasonYear} value={option.seasonYear}>
                    {option.label ?? option.seasonYear}
                  </option>
                ))
              ) : (
                <option value={season}>{season}</option>
              )}
            </select>
          </label>
          {renderAuthAction()}
        </div>
      </header>

      {authError ? <div className="notice error auth-error">{authError}</div> : null}
      {seasonError ? <div className="notice error auth-error">{seasonError}</div> : null}

      <nav className="league-tabs" aria-label="리그 메뉴">
        {leagueTabs.map((tab) =>
          tab.enabled ? (
            <NavLink
              className={({ isActive }) => `league-tab${isActive ? " active" : ""}`}
              key={tab.to}
              to={tab.to}
            >
              {tab.label}
            </NavLink>
          ) : (
            <span className="league-tab disabled" key={tab.to}>
              {tab.label}
            </span>
          ),
        )}
      </nav>

      {typeof children === "function" ? children(authState) : children}
    </main>
  );
}

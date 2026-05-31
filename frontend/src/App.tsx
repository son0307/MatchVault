import { useEffect, useState, type ReactNode } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes } from "react-router-dom";
import { ApiError, fetchCurrentUser, logout, type CurrentUser } from "./api";
import { AuthPage } from "./pages/AuthPage";
import { FixtureDetailPage } from "./pages/FixtureDetailPage";
import { LeagueFixturesPage } from "./pages/LeagueFixturesPage";
import { LeagueHomePage } from "./pages/LeagueHomePage";
import { LeagueStandingsPage } from "./pages/LeagueStandingsPage";
import { PlayerDetailPage } from "./pages/PlayerDetailPage";
import { TeamDetailPage } from "./pages/TeamDetailPage";

export type AuthStatus = "checking" | "authenticated" | "guest";

type LeagueState = {
  authStatus: AuthStatus;
  currentUser: CurrentUser | null;
  season: number;
};

type LeagueLayoutProps = {
  children: ReactNode | ((state: LeagueState) => ReactNode);
  onSeasonChange: (season: number) => void;
  season: number;
};

const DEFAULT_SEASON = 2025;

const leagueTabs = [
  { label: "홈", to: "/league/overview", enabled: true },
  { label: "순위", to: "/league/standings", enabled: true },
  { label: "경기", to: "/league/fixtures", enabled: true },
  { label: "플레이어 통계", to: "/league/player-stats", enabled: false },
  { label: "팀 통계", to: "/league/team-stats", enabled: false },
];

export function App() {
  const [season, setSeason] = useState(DEFAULT_SEASON);

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
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              {(state) => <LeagueHomePage authStatus={state.authStatus} season={state.season} />}
            </LeagueLayout>
          }
        />
        <Route
          path="/league/standings"
          element={
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              <LeagueStandingsPage season={season} />
            </LeagueLayout>
          }
        />
        <Route
          path="/league/fixtures"
          element={
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              <LeagueFixturesPage season={season} />
            </LeagueLayout>
          }
        />
        <Route
          path="/fixtures/:fixtureId"
          element={
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              <FixtureDetailPage />
            </LeagueLayout>
          }
        />
        <Route
          path="/players/:playerId"
          element={
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              <PlayerDetailPage season={season} />
            </LeagueLayout>
          }
        />
        <Route
          path="/teams/:teamId"
          element={
            <LeagueLayout onSeasonChange={setSeason} season={season}>
              <TeamDetailPage season={season} />
            </LeagueLayout>
          }
        />
        <Route path="/league/*" element={<Navigate to="/league/overview" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LeagueLayout({ children, onSeasonChange, season }: LeagueLayoutProps) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authStatus, setAuthStatus] = useState<AuthStatus>("checking");
  const [authError, setAuthError] = useState("");
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    void loadCurrentUser();
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

  function handleSeasonChange(value: string) {
    const nextSeason = Number(value);
    if (Number.isInteger(nextSeason) && nextSeason >= 2000 && nextSeason <= 2100) {
      onSeasonChange(nextSeason);
    }
  }

  function renderAuthAction() {
    if (authStatus === "checking") {
      return <span className="auth-checking">로그인 확인 중</span>;
    }

    if (authStatus === "authenticated" && currentUser) {
      return (
        <div className="auth-status">
          <span>{currentUser.nickname || currentUser.email}</span>
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

  const state = { authStatus, currentUser, season };

  return (
    <main className="app-shell league-shell">
      <header className="league-hero">
        <div>
          <p className="eyebrow">England</p>
          <h1>Premier League</h1>
        </div>
        <div className="league-header-actions">
          <label className="season-field compact league-season-field">
            <span>Season</span>
            <input
              type="number"
              value={season}
              min="2000"
              max="2100"
              onChange={(event) => handleSeasonChange(event.target.value)}
            />
          </label>
          {renderAuthAction()}
        </div>
      </header>

      {authError ? <div className="notice error auth-error">{authError}</div> : null}

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

      {typeof children === "function" ? children(state) : children}
    </main>
  );
}

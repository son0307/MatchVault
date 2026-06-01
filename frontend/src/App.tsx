import { useEffect, useState, type ReactNode } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes } from "react-router-dom";
import {
  ApiError,
  fetchCurrentUser,
  fetchLeagueSeasons,
  logout,
  type CurrentUser,
  type LeagueSeasonCoverage,
} from "./api";
import { AuthPage } from "./pages/AuthPage";
import { FixtureDetailPage } from "./pages/FixtureDetailPage";
import { LeagueFixturesPage } from "./pages/LeagueFixturesPage";
import { LeagueHomePage } from "./pages/LeagueHomePage";
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
  { label: "플레이어 통계", to: "/league/player-stats", enabled: false },
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

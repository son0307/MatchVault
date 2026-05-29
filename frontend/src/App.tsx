import { useEffect, useState, type ReactNode } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes } from "react-router-dom";
import { ApiError, fetchCurrentUser, logout, type CurrentUser } from "./api";
import { AuthPage } from "./pages/AuthPage";
import { FixtureDetailPage } from "./pages/FixtureDetailPage";
import { HomePage } from "./pages/HomePage";
import { LeagueFixturesPage } from "./pages/LeagueFixturesPage";
import { LeagueHomePage } from "./pages/LeagueHomePage";
import { LeagueStandingsPage } from "./pages/LeagueStandingsPage";

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
        <Route path="/dashboard" element={<HomePage />} />
        <Route path="/league" element={<Navigate to="/league/overview" replace />} />
        <Route
          path="/league/overview"
          element={
            <LeagueLayout>
              <LeagueHomePage />
            </LeagueLayout>
          }
        />
        <Route
          path="/league/standings"
          element={
            <LeagueLayout>
              <LeagueStandingsPage />
            </LeagueLayout>
          }
        />
        <Route
          path="/league/fixtures"
          element={
            <LeagueLayout>
              <LeagueFixturesPage />
            </LeagueLayout>
          }
        />
        <Route
          path="/fixtures/:fixtureId"
          element={
            <LeagueLayout>
              <FixtureDetailPage />
            </LeagueLayout>
          }
        />
        <Route path="/league/*" element={<Navigate to="/league/overview" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LeagueLayout({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authError, setAuthError] = useState("");
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const authKey = currentUser ? `user-${currentUser.id}` : "guest";

  useEffect(() => {
    void loadCurrentUser();
  }, []);

  async function loadCurrentUser() {
    try {
      setCurrentUser(await fetchCurrentUser());
      setAuthError("");
    } catch (error) {
      setCurrentUser(null);
      if (!(error instanceof ApiError && error.status === 401)) {
        setAuthError(error instanceof Error ? error.message : "로그인 상태를 확인하지 못했습니다.");
      }
    }
  }

  async function handleLogout() {
    setIsLoggingOut(true);
    setAuthError("");
    try {
      await logout();
      setCurrentUser(null);
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "로그아웃에 실패했습니다.");
    } finally {
      setIsLoggingOut(false);
    }
  }

  return (
    <main className="app-shell league-shell">
      <header className="league-hero">
        <div>
          <p className="eyebrow">England</p>
          <h1>Premier League</h1>
        </div>
        <div className="league-header-actions">
          <NavLink className="home-link" to="/dashboard">
            기존 대시보드
          </NavLink>
          {currentUser ? (
            <div className="auth-status">
              <span>{currentUser.nickname || currentUser.email}</span>
              <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
                로그아웃
              </button>
            </div>
          ) : (
            <NavLink className="home-link" to="/login">
              로그인
            </NavLink>
          )}
        </div>
      </header>

      {authError ? <div className="notice error">{authError}</div> : null}

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

      <div key={authKey}>{children}</div>
    </main>
  );
}

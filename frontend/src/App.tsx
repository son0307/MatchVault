import { BrowserRouter, Navigate, NavLink, Route, Routes } from "react-router-dom";
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
              <FixturePlaceholderPage />
            </LeagueLayout>
          }
        />
        <Route path="/league/*" element={<Navigate to="/league/overview" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LeagueLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="app-shell league-shell">
      <header className="league-hero">
        <div>
          <p className="eyebrow">England</p>
          <h1>Premier League</h1>
        </div>
        <NavLink className="home-link" to="/dashboard">
          기존 대시보드
        </NavLink>
      </header>

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

      {children}
    </main>
  );
}

function FixturePlaceholderPage() {
  return (
    <section className="league-content">
      <article className="panel placeholder-panel">
        <p className="eyebrow">Fixture Detail</p>
        <h2>경기 상세 페이지는 차후 구현 예정입니다.</h2>
        <p className="muted">일정 카드 연결을 먼저 확인할 수 있도록 임시 화면을 준비했습니다.</p>
      </article>
    </section>
  );
}

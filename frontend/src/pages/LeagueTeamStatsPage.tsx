import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  fetchLeagueTeamRankings,
  type LeagueTeamRankingRow,
  type LeagueTeamRankings,
} from "../api";

type RankingKey = keyof Pick<
  LeagueTeamRankings,
  "goalsFor" | "goalsAgainst" | "possession" | "yellowCards" | "redCards"
>;

type Metric = {
  label: string;
  value: (row: LeagueTeamRankingRow) => string;
};

type RankingCategory = {
  key: RankingKey;
  label: string;
  description: string;
  primary: Metric;
  secondary: Metric[];
};

const categories: RankingCategory[] = [
  {
    key: "goalsFor",
    label: "득점",
    description: "시즌 누적 득점이 많은 팀부터 보여줍니다.",
    primary: metric("득점", (row) => `${row.goalsFor}`),
    secondary: [
      metric("경기당", (row) => row.goalsForPerMatch.toFixed(2)),
      metric("경기", (row) => `${row.played}`),
    ],
  },
  {
    key: "goalsAgainst",
    label: "실점",
    description: "시즌 누적 실점이 적은 팀부터 보여줍니다.",
    primary: metric("실점", (row) => `${row.goalsAgainst}`),
    secondary: [
      metric("경기당", (row) => row.goalsAgainstPerMatch.toFixed(2)),
      metric("경기", (row) => `${row.played}`),
    ],
  },
  {
    key: "possession",
    label: "평균 점유율",
    description: "점유율 데이터가 있는 경기의 시즌 평균을 보여줍니다.",
    primary: metric("점유율", (row) => formatPercentage(row.averagePossession)),
    secondary: [
      metric("득점", (row) => `${row.goalsFor}`),
      metric("실점", (row) => `${row.goalsAgainst}`),
    ],
  },
  {
    key: "yellowCards",
    label: "경고",
    description: "팀 경기 통계에 기록된 시즌 누적 경고 수를 보여줍니다.",
    primary: metric("경고", (row) => `${row.yellowCards}`),
    secondary: [
      metric("퇴장", (row) => `${row.redCards}`),
      metric("경기", (row) => `${row.played}`),
    ],
  },
  {
    key: "redCards",
    label: "퇴장",
    description: "팀 경기 통계에 기록된 시즌 누적 퇴장 수를 보여줍니다.",
    primary: metric("퇴장", (row) => `${row.redCards}`),
    secondary: [
      metric("경고", (row) => `${row.yellowCards}`),
      metric("경기", (row) => `${row.played}`),
    ],
  },
];

export function LeagueTeamStatsPage({ season }: { season: number }) {
  const [activeKey, setActiveKey] = useState<RankingKey>("goalsFor");
  const [rankings, setRankings] = useState<LeagueTeamRankings | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const requestIdRef = useRef(0);

  const category = useMemo(
    () => categories.find((item) => item.key === activeKey) ?? categories[0],
    [activeKey],
  );
  const rows = rankings?.[activeKey] ?? [];

  async function loadRankings(targetSeason = season) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setRankings(null);
    setIsLoading(true);
    setErrorMessage("");

    try {
      const response = await fetchLeagueTeamRankings(targetSeason);
      if (requestId === requestIdRef.current) {
        setRankings(response);
      }
    } catch (error) {
      if (requestId !== requestIdRef.current) {
        return;
      }
      setErrorMessage(error instanceof Error ? error.message : "팀 통계 순위를 불러오지 못했습니다.");
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadRankings(season);
  }, [season]);

  return (
    <section className="league-content player-rankings-page">
      <div className="player-rankings-heading">
        <div>
          <p className="eyebrow">{season} 시즌</p>
          <h2>팀 통계 순위</h2>
          <p>{category.description}</p>
        </div>
      </div>

      <div className="segmented-control player-ranking-tabs" aria-label="팀 통계 종류">
        {categories.map((item) => (
          <button
            className={activeKey === item.key ? "active" : ""}
            key={item.key}
            type="button"
            onClick={() => setActiveKey(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <article className="panel player-ranking-panel">
        {isLoading ? <div className="empty-state">팀 통계 순위를 불러오는 중입니다.</div> : null}
        {errorMessage ? (
          <div className="section-error inline-retry">
            <span>{errorMessage}</span>
            <button type="button" onClick={() => void loadRankings()}>
              다시 불러오기
            </button>
          </div>
        ) : null}
        {!isLoading && !errorMessage && !rows.length ? (
          <div className="empty-state">이 시즌에 표시할 {category.label} 기록이 없습니다.</div>
        ) : null}
        {!isLoading && !errorMessage && rows.length ? (
          <RankingTable category={category} rows={rows} />
        ) : null}
      </article>
    </section>
  );
}

function RankingTable({
  category,
  rows,
}: {
  category: RankingCategory;
  rows: LeagueTeamRankingRow[];
}) {
  return (
    <div className="player-ranking-table-wrap">
      <table className="player-ranking-table team-ranking-table">
        <thead>
          <tr>
            <th>순위</th>
            <th className="player-ranking-player-column">팀</th>
            <th className="player-ranking-primary-column">{category.primary.label}</th>
            {category.secondary.map((item) => (
              <th className="player-ranking-secondary-column" key={item.label}>
                {item.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.teamId}>
              <td className="player-ranking-rank">{row.rank}</td>
              <td>
                <TeamIdentity row={row} />
              </td>
              <td className="player-ranking-primary">{category.primary.value(row)}</td>
              {category.secondary.map((item) => (
                <td className="player-ranking-secondary-column" key={item.label}>
                  {item.value(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TeamIdentity({ row }: { row: LeagueTeamRankingRow }) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <Link className="team-ranking-team" to={`/teams/${row.teamId}`}>
      {!imageFailed && row.teamLogoUrl ? (
        <img src={row.teamLogoUrl} alt="" onError={() => setImageFailed(true)} />
      ) : (
        <span className="player-ranking-photo-placeholder" aria-hidden="true" />
      )}
      <div>
        <strong>{row.teamName ?? "-"}</strong>
        <span className="player-ranking-mobile-meta">{row.played}경기</span>
      </div>
    </Link>
  );
}

function metric(label: string, value: (row: LeagueTeamRankingRow) => string): Metric {
  return { label, value };
}

function formatPercentage(value: number | null) {
  return value === null ? "-" : `${value.toFixed(2)}%`;
}

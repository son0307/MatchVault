const state = {
    teams: [],
    fixtures: [],
    standings: [],
    nextCursor: null,
    hasNext: false,
    selectedFixtureId: null,
    selectedTeamId: null,
};

const elements = {
    seasonInput: document.querySelector("#seasonInput"),
    refreshButton: document.querySelector("#refreshButton"),
    loadMoreFixtures: document.querySelector("#loadMoreFixtures"),
    teamCount: document.querySelector("#teamCount"),
    fixtureCount: document.querySelector("#fixtureCount"),
    standingCount: document.querySelector("#standingCount"),
    apiStatus: document.querySelector("#apiStatus"),
    fixturesList: document.querySelector("#fixturesList"),
    fixtureDetail: document.querySelector("#fixtureDetail"),
    detailTitle: document.querySelector("#detailTitle"),
    standingsTable: document.querySelector("#standingsTable"),
    teamsList: document.querySelector("#teamsList"),
    teamDetail: document.querySelector("#teamDetail"),
};

document.addEventListener("DOMContentLoaded", () => {
    elements.refreshButton.addEventListener("click", refreshAll);
    elements.loadMoreFixtures.addEventListener("click", () => loadFixtures(false));
    refreshAll();
});

async function refreshAll() {
    setApiStatus("Loading");
    state.fixtures = [];
    state.nextCursor = null;
    state.hasNext = false;
    state.selectedFixtureId = null;
    state.selectedTeamId = null;
    elements.fixtureDetail.className = "detail-empty";
    elements.fixtureDetail.textContent = "왼쪽 경기 목록에서 하나를 선택하면 저장된 통계, 이벤트, 라인업을 표시합니다.";
    elements.teamDetail.innerHTML = "";
    renderLoading();

    const results = await Promise.allSettled([
        loadTeams(),
        loadStandings(),
        loadFixtures(true),
    ]);

    const failed = results.filter((result) => result.status === "rejected");
    setApiStatus(failed.length ? "Partial" : "Ready");
}

function renderLoading() {
    elements.fixturesList.innerHTML = loadingMarkup();
    elements.standingsTable.innerHTML = `<tr><td colspan="6">데이터를 불러오는 중입니다.</td></tr>`;
    elements.teamsList.innerHTML = loadingMarkup();
    elements.teamCount.textContent = "-";
    elements.fixtureCount.textContent = "-";
    elements.standingCount.textContent = "-";
}

async function loadTeams() {
    try {
        const teams = await requestJson("/api/v1/teams");
        state.teams = Array.isArray(teams) ? teams : [];
        renderTeams();
    } catch (error) {
        elements.teamsList.innerHTML = errorMarkup(error);
        throw error;
    } finally {
        elements.teamCount.textContent = state.teams.length;
    }
}

async function loadStandings() {
    const season = encodeURIComponent(elements.seasonInput.value || "2024");
    try {
        const standings = await requestJson(`/api/v1/teams/standings?season=${season}`);
        state.standings = Array.isArray(standings) ? standings : [];
        renderStandings();
    } catch (error) {
        elements.standingsTable.innerHTML = `<tr><td colspan="6">${escapeHtml(error.message)}</td></tr>`;
        throw error;
    } finally {
        elements.standingCount.textContent = state.standings.length;
    }
}

async function loadFixtures(reset) {
    if (!reset && !state.hasNext) {
        return;
    }

    elements.loadMoreFixtures.disabled = true;
    const cursor = reset || !state.nextCursor ? "" : `&cursorId=${encodeURIComponent(state.nextCursor)}`;

    try {
        const response = await requestJson(`/api/v1/fixtures?size=10${cursor}`);
        const content = Array.isArray(response.content) ? response.content : [];
        state.fixtures = reset ? content : [...state.fixtures, ...content];
        state.nextCursor = response.nextCursor || null;
        state.hasNext = Boolean(response.hasNext);
        renderFixtures();
    } catch (error) {
        elements.fixturesList.innerHTML = errorMarkup(error);
        throw error;
    } finally {
        elements.fixtureCount.textContent = state.fixtures.length;
        elements.loadMoreFixtures.disabled = !state.hasNext;
    }
}

function renderFixtures() {
    if (!state.fixtures.length) {
        elements.fixturesList.innerHTML = emptyMarkup("저장된 경기 데이터가 없습니다.");
        return;
    }

    elements.fixturesList.innerHTML = state.fixtures.map((fixture) => {
        const isActive = fixture.fixtureId === state.selectedFixtureId ? " active" : "";
        const homeWinner = fixture.homeWinner ? " winner" : "";
        const awayWinner = fixture.awayWinner ? " winner" : "";

        return `
            <button class="fixture-card${isActive}" type="button" data-fixture-id="${fixture.fixtureId}">
                <span class="team-side">
                    <span class="team-name${homeWinner}">${escapeHtml(fixture.homeTeamName)}</span>
                    <span class="status-pill">Home</span>
                </span>
                <span class="score-box">
                    <span>${numberText(fixture.homeScore)}</span>
                    <span>:</span>
                    <span>${numberText(fixture.awayScore)}</span>
                </span>
                <span class="team-side away">
                    <span class="team-name${awayWinner}">${escapeHtml(fixture.awayTeamName)}</span>
                    <span class="status-pill">${escapeHtml(fixture.fixtureStatus || "Status")}</span>
                </span>
            </button>
        `;
    }).join("");

    document.querySelectorAll(".fixture-card").forEach((button) => {
        button.addEventListener("click", () => selectFixture(Number(button.dataset.fixtureId)));
    });
}

async function selectFixture(fixtureId) {
    state.selectedFixtureId = fixtureId;
    renderFixtures();

    const fixture = state.fixtures.find((item) => item.fixtureId === fixtureId);
    elements.detailTitle.textContent = fixture
        ? `${fixture.homeTeamName} vs ${fixture.awayTeamName}`
        : `Fixture ${fixtureId}`;
    elements.fixtureDetail.className = "detail-stack";
    elements.fixtureDetail.innerHTML = loadingMarkup();

    const [stats, events, lineups, playerStats] = await Promise.allSettled([
        requestJson(`/api/v1/fixtures/${fixtureId}/stats`),
        requestJson(`/api/v1/fixtures/${fixtureId}/events`),
        requestJson(`/api/v1/fixtures/${fixtureId}/lineups`),
        requestJson(`/api/v1/fixtures/${fixtureId}/player-stats`),
    ]);

    elements.fixtureDetail.innerHTML = [
        renderStats(settledValue(stats)),
        renderEvents(settledValue(events)),
        renderLineups(settledValue(lineups)),
        renderPlayerStats(settledValue(playerStats)),
    ].join("");
}

function renderStats(stats) {
    if (!stats || (!stats.homeTeamStat && !stats.awayTeamStat)) {
        return sectionMarkup("팀 통계", emptyMarkup("저장된 팀 통계가 없습니다."));
    }

    return sectionMarkup("팀 통계", `
        <div class="stat-grid">
            ${teamStatMarkup("Home", stats.homeTeamStat)}
            ${teamStatMarkup("Away", stats.awayTeamStat)}
        </div>
    `);
}

function teamStatMarkup(label, stat) {
    if (!stat) {
        return `<div class="stat-card"><strong>${label}</strong><p class="muted">통계 없음</p></div>`;
    }

    const rows = [
        ["Score", stat.score],
        ["Possession", `${numberText(stat.ballPossession)}%`],
        ["Shots", stat.totalShots],
        ["On target", stat.shotsOnTarget ?? stat.shotsOnGoal],
        ["Blocked", stat.blockedShots],
        ["Inside box", stat.shotsInsideBox],
        ["Passes", stat.totalPasses],
        ["Accurate passes", stat.passesAccurate],
        ["Pass accuracy", `${numberText(stat.passAccuracy)}%`],
        ["Corners", stat.cornerKicks],
        ["Offsides", stat.offsides],
        ["Fouls", stat.fouls],
        ["Saves", stat.goalkeeperSaves],
        ["xG", stat.expectedGoals],
        ["Cards", `${numberText(stat.yellowCards)}Y / ${numberText(stat.redCards)}R`],
    ];

    return `
        <div class="stat-card">
            <strong>${label} Team ${stat.teamId ? `<span class="muted">#${stat.teamId}</span>` : ""}</strong>
            ${rows.map(([name, value]) => `
                <div class="stat-row"><span>${name}</span><b>${numberText(value)}</b></div>
            `).join("")}
        </div>
    `;
}

function renderEvents(data) {
    const events = Array.isArray(data?.events) ? data.events : [];
    if (!events.length) {
        return sectionMarkup("경기 이벤트", emptyMarkup("저장된 이벤트가 없습니다."));
    }

    return sectionMarkup("경기 이벤트", `
        <div class="event-list">
            ${events.map((event) => `
                <article class="event-item">
                    <div class="event-meta">
                        <span class="small-pill">${event.time?.elapsed ?? "-"}'</span>
                        <span class="small-pill">${escapeHtml(event.type || "Event")}</span>
                        <strong>${escapeHtml(event.team?.name || "Unknown team")}</strong>
                    </div>
                    <p>${escapeHtml(event.player?.name || "-")} ${event.detail ? `· ${escapeHtml(event.detail)}` : ""}</p>
                    ${event.assist?.name ? `<p class="muted">Assist: ${escapeHtml(event.assist.name)}</p>` : ""}
                    ${event.comments ? `<p class="muted">${escapeHtml(event.comments)}</p>` : ""}
                </article>
            `).join("")}
        </div>
    `);
}

function renderLineups(data) {
    if (!data || (!data.homeTeam && !data.awayTeam)) {
        return sectionMarkup("라인업", emptyMarkup("저장된 라인업이 없습니다."));
    }

    return sectionMarkup("라인업", `
        <div class="stat-grid">
            ${lineupTeamMarkup(data.homeTeam)}
            ${lineupTeamMarkup(data.awayTeam)}
        </div>
    `);
}

function lineupTeamMarkup(team) {
    if (!team) {
        return `<div class="stat-card"><p class="muted">라인업 없음</p></div>`;
    }

    const starters = Array.isArray(team.starters) ? team.starters.slice(0, 11) : [];
    const substitutes = Array.isArray(team.substitutes) ? team.substitutes.slice(0, 6) : [];

    return `
        <div class="stat-card">
            <strong>${escapeHtml(team.teamName || "Team")} <span class="muted">${escapeHtml(team.formation || "")}</span></strong>
            <p class="muted">Coach: ${escapeHtml(team.coachName || "-")}</p>
            <div class="lineup-list">
                ${starters.map(playerMarkup).join("") || `<div class="lineup-item muted">선발 정보 없음</div>`}
            </div>
            <p class="muted">Substitutes</p>
            <div class="lineup-list">
                ${substitutes.map(playerMarkup).join("") || `<div class="lineup-item muted">교체 명단 없음</div>`}
            </div>
        </div>
    `;
}

function playerMarkup(player) {
    return `
        <div class="lineup-item">
            <strong>${player.backNumber ? `${player.backNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</strong>
            <span class="muted">${escapeHtml(player.position || "")}</span>
        </div>
    `;
}

function renderPlayerStats(data) {
    const groups = [data?.homeTeam, data?.awayTeam].filter(Boolean);
    if (!groups.length) {
        return sectionMarkup("선수별 경기 통계", emptyMarkup("저장된 선수별 경기 통계가 없습니다."));
    }

    return sectionMarkup("선수별 경기 통계", groups.map((group) => {
        const players = Array.isArray(group.players) ? group.players.slice(0, 8) : [];
        return `
            <div class="stat-card">
                <strong>${escapeHtml(group.teamName || "Team")}</strong>
                <div class="player-list">
                    ${players.map((player) => `
                        <div class="player-item">
                            <strong>${player.jerseyNumber ? `${player.jerseyNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</strong>
                            <p class="muted">
                                ${numberText(player.minutesPlayed)}분 · 평점 ${numberText(player.rating)} · 골 ${numberText(player.goals)} · 도움 ${numberText(player.assists)}
                            </p>
                        </div>
                    `).join("") || `<div class="player-item muted">선수 통계 없음</div>`}
                </div>
            </div>
        `;
    }).join(""));
}

function renderStandings() {
    if (!state.standings.length) {
        elements.standingsTable.innerHTML = `<tr><td colspan="6">해당 시즌 순위 데이터가 없습니다.</td></tr>`;
        return;
    }

    elements.standingsTable.innerHTML = state.standings.map((standing) => `
        <tr>
            <td>${numberText(standing.rank)}</td>
            <td>
                <div class="club-cell">
                    ${imageMarkup(standing.team?.logo, standing.team?.name, "club-logo")}
                    <strong>${escapeHtml(standing.team?.name || "-")}</strong>
                </div>
            </td>
            <td>${numberText(standing.all?.played)}</td>
            <td><strong>${numberText(standing.points)}</strong></td>
            <td>${numberText(standing.goalsDiff)}</td>
            <td>${escapeHtml(standing.form || "-")}</td>
        </tr>
    `).join("");
}

function renderTeams() {
    if (!state.teams.length) {
        elements.teamsList.innerHTML = emptyMarkup("저장된 팀 데이터가 없습니다.");
        return;
    }

    elements.teamsList.innerHTML = state.teams.map((team) => `
        <button class="team-button${team.teamId === state.selectedTeamId ? " active" : ""}" type="button" data-team-id="${team.teamId}">
            ${imageMarkup(team.logoUrl, team.teamName, "club-logo")}
            <span>${escapeHtml(team.teamName || "-")}</span>
        </button>
    `).join("");

    document.querySelectorAll(".team-button").forEach((button) => {
        button.addEventListener("click", () => selectTeam(Number(button.dataset.teamId)));
    });
}

async function selectTeam(teamId) {
    state.selectedTeamId = teamId;
    renderTeams();
    elements.teamDetail.innerHTML = loadingMarkup();

    const [details, players] = await Promise.allSettled([
        requestJson(`/api/v1/teams/${teamId}`),
        requestJson(`/api/v1/teams/${teamId}/players`),
    ]);

    const detail = settledValue(details);
    const roster = settledValue(players) || [];

    if (!detail) {
        elements.teamDetail.innerHTML = errorMarkup(new Error("팀 상세 정보를 불러오지 못했습니다."));
        return;
    }

    elements.teamDetail.innerHTML = `
        <div class="team-profile">
            <div class="team-profile-head">
                ${imageMarkup(detail.logoUrl, detail.teamName, "club-logo")}
                <div>
                    <h3>${escapeHtml(detail.teamName || "-")}</h3>
                    <p class="muted">${escapeHtml(detail.country || "-")} · Founded ${numberText(detail.founded)}</p>
                </div>
            </div>
            <div class="stat-row"><span>Venue</span><b>${escapeHtml(detail.venue?.venueName || "-")}</b></div>
            <div class="stat-row"><span>City</span><b>${escapeHtml(detail.venue?.venueCity || "-")}</b></div>
            <div class="stat-row"><span>Capacity</span><b>${numberText(detail.venue?.capacity)}</b></div>
            <div class="section-title">
                <h3>선수 명단</h3>
                <span class="small-pill">${Array.isArray(roster) ? roster.length : 0}</span>
            </div>
            <div class="player-list">
                ${Array.isArray(roster) && roster.length
                    ? roster.map((player) => `
                        <div class="player-item">
                            <strong>${player.backNumber ? `${player.backNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</strong>
                            <p class="muted">${escapeHtml(player.position || "-")}</p>
                        </div>
                    `).join("")
                    : `<div class="player-item muted">저장된 선수 데이터가 없습니다.</div>`}
            </div>
        </div>
    `;
}

async function requestJson(url) {
    const response = await fetch(url, {headers: {"Accept": "application/json"}});
    if (!response.ok) {
        throw new Error(`${url} 요청 실패 (${response.status})`);
    }
    return response.json();
}

function sectionMarkup(title, body) {
    return `
        <section>
            <div class="section-title"><h3>${title}</h3></div>
            ${body}
        </section>
    `;
}

function settledValue(result) {
    return result.status === "fulfilled" ? result.value : null;
}

function loadingMarkup() {
    return `<div class="loading">데이터를 불러오는 중입니다.</div>`;
}

function emptyMarkup(message) {
    return `<div class="empty">${escapeHtml(message)}</div>`;
}

function errorMarkup(error) {
    return `<div class="error">${escapeHtml(error.message || "데이터를 불러오지 못했습니다.")}</div>`;
}

function imageMarkup(src, alt, className) {
    if (!src) {
        return `<span class="${className}" aria-hidden="true"></span>`;
    }
    return `<img class="${className}" src="${escapeAttribute(src)}" alt="${escapeAttribute(alt || "team logo")}" loading="lazy">`;
}

function numberText(value) {
    return value === null || value === undefined || value === "" ? "-" : String(value);
}

function setApiStatus(status) {
    elements.apiStatus.textContent = status;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
    return escapeHtml(value).replaceAll("`", "&#096;");
}

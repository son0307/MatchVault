const state = {
    teams: [],
    fixtures: [],
    standings: [],
    nextCursor: null,
    hasNext: false,
    selectedFixtureDate: todayKoreaDateKey(),
    selectedFixtureId: null,
    selectedFixturePlayerStats: null,
    selectedTeamId: null,
    selectedPlayerId: null,
    liveFixtureId: null,
    liveSource: null,
    liveData: {
        snapshot: null,
        events: null,
        lineups: null,
        playerStats: null,
    },
};

const elements = {
    seasonInput: document.querySelector("#seasonInput"),
    refreshButton: document.querySelector("#refreshButton"),
    loadMoreFixtures: document.querySelector("#loadMoreFixtures"),
    previousFixtureDate: document.querySelector("#previousFixtureDate"),
    fixtureDateButton: document.querySelector("#fixtureDateButton"),
    fixtureDateInput: document.querySelector("#fixtureDateInput"),
    nextFixtureDate: document.querySelector("#nextFixtureDate"),
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
    playerDetail: document.querySelector("#playerDetail"),
    liveFixtureInput: document.querySelector("#liveFixtureInput"),
    liveConnectButton: document.querySelector("#liveConnectButton"),
    liveDisconnectButton: document.querySelector("#liveDisconnectButton"),
    liveConnectionStatus: document.querySelector("#liveConnectionStatus"),
    liveFixtureLabel: document.querySelector("#liveFixtureLabel"),
    liveFixtureDetail: document.querySelector("#liveFixtureDetail"),
};

document.addEventListener("DOMContentLoaded", () => {
    setupFixtureDateControls();
    elements.refreshButton.addEventListener("click", refreshAll);
    elements.loadMoreFixtures.addEventListener("click", () => loadFixtures(false));
    elements.previousFixtureDate.addEventListener("click", () => moveSelectedFixtureDate(-1));
    elements.nextFixtureDate.addEventListener("click", () => moveSelectedFixtureDate(1));
    elements.fixtureDateButton.addEventListener("click", openFixtureDatePicker);
    elements.fixtureDateInput.addEventListener("change", selectFixtureDateFromInput);
    elements.liveConnectButton.addEventListener("click", connectLiveFixture);
    elements.liveDisconnectButton.addEventListener("click", disconnectLiveFixture);
    renderFixtureDateControl();
    refreshAll();
});

function setupFixtureDateControls() {
    if (!elements.previousFixtureDate || !elements.fixtureDateButton || !elements.fixtureDateInput || !elements.nextFixtureDate) {
        const heading = document.querySelector(".fixtures-panel .panel-heading");
        const loadMoreButton = elements.loadMoreFixtures;

        if (heading && loadMoreButton) {
            const controls = document.createElement("div");
            controls.className = "fixture-date-controls";
            controls.setAttribute("aria-label", "경기 날짜 선택");
            controls.innerHTML = `
                <button id="previousFixtureDate" type="button" class="date-nav-button" aria-label="이전 날짜">‹</button>
                <button id="fixtureDateButton" type="button" class="fixture-date-button">오늘</button>
                <input id="fixtureDateInput" class="fixture-date-input" type="date" aria-label="경기 날짜">
                <button id="nextFixtureDate" type="button" class="date-nav-button" aria-label="다음 날짜">›</button>
            `;
            heading.insertBefore(controls, loadMoreButton);
            loadMoreButton.textContent = "더 보기";
        }

        elements.previousFixtureDate = document.querySelector("#previousFixtureDate");
        elements.fixtureDateButton = document.querySelector("#fixtureDateButton");
        elements.fixtureDateInput = document.querySelector("#fixtureDateInput");
        elements.nextFixtureDate = document.querySelector("#nextFixtureDate");
    }
}

function renderFixtureDateControl() {
    elements.fixtureDateInput.value = state.selectedFixtureDate;
    elements.fixtureDateButton.textContent = selectedFixtureDateLabel();
}

function moveSelectedFixtureDate(dayDelta) {
    state.selectedFixtureDate = addDaysToDateKey(state.selectedFixtureDate, dayDelta);
    state.selectedFixtureId = null;
    state.selectedFixturePlayerStats = null;
    renderFixtureDateControl();
    resetFixtureDetail();
    loadFixtures(true);
}

function openFixtureDatePicker() {
    if (typeof elements.fixtureDateInput.showPicker === "function") {
        elements.fixtureDateInput.showPicker();
        return;
    }

    elements.fixtureDateInput.click();
}

function selectFixtureDateFromInput() {
    if (!elements.fixtureDateInput.value || elements.fixtureDateInput.value === state.selectedFixtureDate) {
        return;
    }

    state.selectedFixtureDate = elements.fixtureDateInput.value;
    state.selectedFixtureId = null;
    renderFixtureDateControl();
    resetFixtureDetail();
    loadFixtures(true);
}

async function refreshAll() {
    setApiStatus("Loading");
    state.fixtures = [];
    state.nextCursor = null;
    state.hasNext = false;
    state.selectedFixtureId = null;
    state.selectedTeamId = null;
    state.selectedPlayerId = null;
    resetFixtureDetail();
    elements.teamDetail.innerHTML = "";
    elements.playerDetail.className = "detail-empty";
    elements.playerDetail.textContent = "팀 목록에서 선수를 선택하면 프로필과 시즌별/경기별 스탯을 표시합니다.";
    renderLoading();

    const results = await Promise.allSettled([
        loadTeams(),
        loadStandings(),
        loadFixtures(true),
    ]);

    const failed = results.filter((result) => result.status === "rejected");
    setApiStatus(failed.length ? "Partial" : "Ready");
}

function resetFixtureDetail() {
    elements.detailTitle.textContent = "경기 상세";
    elements.fixtureDetail.className = "detail-empty";
    elements.fixtureDetail.textContent = "왼쪽 경기 목록에서 하나를 선택하면 저장된 통계, 이벤트, 라인업을 표시합니다.";
    state.selectedFixturePlayerStats = null;
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
    const season = encodeURIComponent(elements.seasonInput.value || "2024");
    const date = encodeURIComponent(state.selectedFixtureDate);
    const cursor = reset || !state.nextCursor ? "" : `&cursorId=${encodeURIComponent(state.nextCursor)}`;

    try {
        // 선택한 날짜는 한국 시간 기준으로 서버에 전달하고, 서버가 UTC 저장 범위로 변환해 조회한다.
        const response = await requestJson(`/api/v1/fixtures?size=100&season=${season}&date=${date}${cursor}`);
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
        elements.fixturesList.innerHTML = emptyMarkup(`${selectedFixtureDateLabel()}에 예정된 경기 데이터가 없습니다.`);
        return;
    }

    const selectedDateFixtures = fixturesForSelectedDate(state.fixtures);
    if (!selectedDateFixtures.length) {
        elements.fixturesList.innerHTML = emptyMarkup(`${selectedFixtureDateLabel()}에 예정된 경기 데이터가 없습니다.`);
        return;
    }

    elements.fixturesList.innerHTML = `
        <section class="fixture-date-group" aria-label="${escapeAttribute(selectedFixtureDateLabel())}">
            <div class="fixture-date-heading">
                <strong>${escapeHtml(selectedFixtureDateLabel())}</strong>
                <span>${selectedDateFixtures.length}경기</span>
            </div>
            ${selectedDateFixtures.map(fixtureCardMarkup).join("")}
        </section>
    `;

    document.querySelectorAll(".fixture-card").forEach((button) => {
        button.addEventListener("click", () => selectFixture(Number(button.dataset.fixtureId)));
    });
}

function fixtureCardMarkup(fixture) {
    const isActive = fixture.fixtureId === state.selectedFixtureId ? " active" : "";
    const homeWinner = fixture.homeWinner ? " winner" : "";
    const awayWinner = fixture.awayWinner ? " winner" : "";

    return `
        <button class="fixture-card${isActive}" type="button" data-fixture-id="${fixture.fixtureId}">
            <span class="team-side">
                <span class="team-name${homeWinner}">${escapeHtml(fixture.homeTeamName)}</span>
                <span class="status-pill">Home</span>
            </span>
            <span class="fixture-time">
                <strong>${escapeHtml(timeText(fixture.fixtureDate))}</strong>
                <span>${escapeHtml(fixture.fixtureStatus || "Status")}</span>
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
}

function fixturesForSelectedDate(fixtures) {
    return fixtures
        .filter((fixture) => dateKey(fixture.fixtureDate) === state.selectedFixtureDate)
        .sort((a, b) => fixtureTimeValue(a) - fixtureTimeValue(b));
}

async function selectFixture(fixtureId) {
    state.selectedFixtureId = fixtureId;
    elements.liveFixtureInput.value = fixtureId;
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

    state.selectedFixturePlayerStats = settledValue(playerStats);
    elements.fixtureDetail.innerHTML = [
        renderStats(settledValue(stats)),
        renderEvents(settledValue(events)),
        renderLineups(settledValue(lineups)),
        renderPlayerStats(state.selectedFixturePlayerStats),
    ].join("");
    bindLineupPlayerButtons();
}

async function connectLiveFixture() {
    const fixtureId = Number(elements.liveFixtureInput.value);
    if (!fixtureId) {
        elements.liveFixtureDetail.innerHTML = errorMarkup(new Error("fixture ID를 입력하세요."));
        return;
    }

    disconnectLiveFixture();
    state.liveFixtureId = fixtureId;
    state.liveData = {
        snapshot: null,
        events: null,
        lineups: null,
        playerStats: null,
    };

    setLiveStatus("Connecting");
    elements.liveFixtureLabel.textContent = `Fixture #${fixtureId}`;
    elements.liveFixtureDetail.className = "detail-stack";
    elements.liveFixtureDetail.innerHTML = loadingMarkup();

    await loadLiveInitialState(fixtureId);
    openLiveStream(fixtureId);
}

function openLiveStream(fixtureId) {
    const source = new EventSource(`/api/v1/live/stream/fixtures/${fixtureId}`);
    state.liveSource = source;
    elements.liveConnectButton.disabled = true;
    elements.liveDisconnectButton.disabled = false;

    source.addEventListener("CONNECT", () => {
        setLiveStatus("Connected");
    });

    source.addEventListener("LIVE_SNAPSHOT", (event) => {
        state.liveData.snapshot = parseEventData(event);
        renderLiveFixture();
    });

    source.addEventListener("FIXTURE_EVENTS", (event) => {
        state.liveData.events = parseEventData(event);
        renderLiveFixture();
    });

    source.addEventListener("PLAYER_STATS", (event) => {
        state.liveData.playerStats = parseEventData(event);
        renderLiveFixture();
    });

    source.onerror = () => {
        setLiveStatus("Reconnecting");
    };
}

function disconnectLiveFixture() {
    if (state.liveSource) {
        state.liveSource.close();
        state.liveSource = null;
    }
    elements.liveConnectButton.disabled = false;
    elements.liveDisconnectButton.disabled = true;
    if (state.liveFixtureId) {
        setLiveStatus("Disconnected");
    }
}

async function loadLiveInitialState(fixtureId) {
    const [stats, events, lineups, playerStats] = await Promise.allSettled([
        requestJson(`/api/v1/live/fixtures/${fixtureId}/stats`),
        requestJson(`/api/v1/fixtures/${fixtureId}/events`),
        requestJson(`/api/v1/fixtures/${fixtureId}/lineups`),
        requestJson(`/api/v1/live/fixtures/${fixtureId}/player-stats`),
    ]);

    const statValue = settledValue(stats);
    state.liveData.snapshot = statValue
        ? {
            fixtureId: statValue.fixtureId,
            homeTeamStat: statValue.homeTeamStat,
            awayTeamStat: statValue.awayTeamStat,
        }
        : null;
    state.liveData.events = settledValue(events);
    state.liveData.lineups = settledValue(lineups);
    state.liveData.playerStats = settledValue(playerStats);
    renderLiveFixture();
}

function renderLiveFixture() {
    const snapshot = state.liveData.snapshot;
    const stats = snapshot
        ? {
            fixtureId: snapshot.fixtureId,
            homeTeamStat: snapshot.homeTeamStat,
            awayTeamStat: snapshot.awayTeamStat,
        }
        : null;

    elements.liveFixtureDetail.className = "detail-stack";
    elements.liveFixtureDetail.innerHTML = [
        renderLiveSnapshot(snapshot),
        renderStats(stats),
        renderEvents(state.liveData.events),
        renderLineups(state.liveData.lineups),
        renderPlayerStats(state.liveData.playerStats),
    ].join("");
}

function renderLiveSnapshot(snapshot) {
    if (!snapshot || !snapshot.fixtureId) {
        return sectionMarkup("경기 상태", emptyMarkup("아직 수신된 실시간 경기 상태가 없습니다."));
    }

    return sectionMarkup("경기 상태", `
        <div class="live-scoreboard">
            <span class="team-side">
                <span class="team-name">Home</span>
                <span class="status-pill">${escapeHtml(snapshot.statusShort || "LIVE")}</span>
            </span>
            <span class="score-box">
                <span>${numberText(snapshot.homeTeamStat?.score ?? snapshot.fulltimeHomeScore)}</span>
                <span>:</span>
                <span>${numberText(snapshot.awayTeamStat?.score ?? snapshot.fulltimeAwayScore)}</span>
            </span>
            <span class="team-side away">
                <span class="team-name">Away</span>
                <span class="status-pill">${numberText(snapshot.elapsed)}'</span>
            </span>
        </div>
        ${snapshot.latestEvent ? `
            <div class="event-item">
                <div class="event-meta">
                    <span class="small-pill">${snapshot.latestEvent.time?.elapsed ?? "-"}'</span>
                    <span class="small-pill">${escapeHtml(snapshot.latestEvent.type || "Event")}</span>
                    <strong>${escapeHtml(snapshot.latestEvent.team?.name || "Unknown team")}</strong>
                </div>
                <p>${escapeHtml(snapshot.latestEvent.player?.name || "-")} ${snapshot.latestEvent.detail ? `· ${escapeHtml(snapshot.latestEvent.detail)}` : ""}</p>
            </div>
        ` : ""}
    `);
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
    const absences = Array.isArray(team.absences) ? team.absences : [];

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
            <p class="muted">결장 선수</p>
            <div class="lineup-list">
                ${absences.map(absenceMarkup).join("") || `<div class="lineup-item muted">결장 정보 없음</div>`}
            </div>
        </div>
    `;
}

function playerMarkup(player) {
    return `
        <button class="lineup-item lineup-player-button" type="button" data-player-id="${player.playerId}">
            <strong>${player.backNumber ? `${player.backNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</strong>
            <span class="muted">${escapeHtml(player.position || "")}</span>
        </button>
    `;
}

function bindLineupPlayerButtons() {
    document.querySelectorAll(".lineup-player-button").forEach((button) => {
        button.addEventListener("click", () => openFixturePlayerStatModal(Number(button.dataset.playerId)));
    });
}

function openFixturePlayerStatModal(playerId) {
    const playerStat = findFixturePlayerStat(playerId);
    if (!playerStat) {
        showFixturePlayerStatModal(`
            <div class="modal-header">
                <div>
                    <p class="eyebrow">Player Stats</p>
                    <h3>선수 경기 스탯</h3>
                </div>
                <button class="modal-close-button" type="button" aria-label="닫기">×</button>
            </div>
            ${emptyMarkup("이 선수의 경기 스탯이 아직 저장되지 않았습니다.")}
        `);
        return;
    }

    showFixturePlayerStatModal(playerStatModalMarkup(playerStat));
}

function findFixturePlayerStat(playerId) {
    const groups = [state.selectedFixturePlayerStats?.homeTeam, state.selectedFixturePlayerStats?.awayTeam].filter(Boolean);
    for (const group of groups) {
        const player = Array.isArray(group.players)
            ? group.players.find((item) => Number(item.playerId) === Number(playerId))
            : null;
        if (player) {
            return {...player, teamName: group.teamName};
        }
    }
    return null;
}

function showFixturePlayerStatModal(markup) {
    closeFixturePlayerStatModal();
    document.body.insertAdjacentHTML("beforeend", `
        <div class="modal-backdrop" role="presentation">
            <section class="player-stat-modal" role="dialog" aria-modal="true" aria-label="선수 경기 스탯">
                ${markup}
            </section>
        </div>
    `);

    const backdrop = document.querySelector(".modal-backdrop");
    const closeButton = document.querySelector(".modal-close-button");
    backdrop.addEventListener("click", (event) => {
        if (event.target === backdrop) {
            closeFixturePlayerStatModal();
        }
    });
    closeButton?.addEventListener("click", closeFixturePlayerStatModal);
    document.addEventListener("keydown", closeFixturePlayerStatOnEscape);
}

function closeFixturePlayerStatModal() {
    document.querySelector(".modal-backdrop")?.remove();
    document.removeEventListener("keydown", closeFixturePlayerStatOnEscape);
}

function closeFixturePlayerStatOnEscape(event) {
    if (event.key === "Escape") {
        closeFixturePlayerStatModal();
    }
}

function playerStatModalMarkup(player) {
    const summaryRows = [
        ["출전 시간", `${numberText(player.minutesPlayed)}분`],
        ["평점", numberText(player.rating)],
        ["골", numberText(player.goals)],
        ["도움", numberText(player.assists)],
        ["슈팅", numberText(player.shotsTotal)],
        ["유효 슈팅", numberText(player.shotsOnTarget)],
        ["패스", successRatioText(player.passesAccurate, player.passesTotal, player.passAccuracy)],
        ["키패스", numberText(player.passesKey)],
        ["태클", numberText(player.tacklesTotal)],
        ["인터셉트", numberText(player.interceptions)],
        ["블록", numberText(player.blocks)],
        ["드리블 성공", successRatioText(player.dribblesSuccess, player.dribblesAttempts)],
        ["경합 성공", successRatioText(player.duelsWon, player.duelsTotal)],
        ["파울 유도", numberText(player.foulsDrawn)],
        ["파울", numberText(player.foulsCommitted)],
        ["카드", `${numberText(player.yellowCards)}Y / ${numberText(player.redCards)}R`],
    ];

    return `
        <div class="modal-header">
            <div>
                <p class="eyebrow">Player Stats</p>
                <h3>${player.jerseyNumber ? `${player.jerseyNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</h3>
                <p class="muted">${escapeHtml(player.teamName || "-")} · ${escapeHtml(player.position || "-")}${player.captain ? " · Captain" : ""}${player.substitute ? " · Substitute" : ""}</p>
            </div>
            <button class="modal-close-button" type="button" aria-label="닫기">×</button>
        </div>
        <div class="modal-stat-grid">
            ${summaryRows.map(([label, value]) => `
                <div class="modal-stat-row">
                    <span>${label}</span>
                    <b>${value}</b>
                </div>
            `).join("")}
        </div>
    `;
}

function successRatioText(success, total, knownRate) {
    const successText = numberText(success);
    const totalText = numberText(total);
    const rate = knownRate ?? successRate(success, total);

    return `${successText} / ${totalText}${rate === null ? "" : ` (${rate}%)`}`;
}

function successRate(success, total) {
    if (success === null || success === undefined || total === null || total === undefined || Number(total) <= 0) {
        return null;
    }
    return Math.round((Number(success) * 100) / Number(total));
}

function absenceMarkup(absence) {
    const reason = absence.reason || absence.absenceType || "사유 미상";

    return `
        <div class="lineup-item absence-item">
            <strong>${escapeHtml(absence.playerName || "-")}</strong>
            <span class="muted">${escapeHtml(reason)}</span>
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
    state.selectedPlayerId = null;
    renderTeams();
    elements.teamDetail.innerHTML = loadingMarkup();
    const season = encodeURIComponent(elements.seasonInput.value || "2024");

    const [details, players] = await Promise.allSettled([
        requestJson(`/api/v1/teams/${teamId}`),
        requestJson(`/api/v1/teams/${teamId}/players?season=${season}`),
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
                        <button class="player-item player-button" type="button" data-player-id="${player.playerId}">
                            ${imageMarkup(player.photoUrl, player.playerName, "player-thumb")}
                            <strong>${player.backNumber ? `${player.backNumber}. ` : ""}${escapeHtml(player.playerName || "-")}</strong>
                            <p class="muted">${escapeHtml(player.position || "-")}</p>
                        </button>
                    `).join("")
                    : `<div class="player-item muted">저장된 선수 데이터가 없습니다.</div>`}
            </div>
        </div>
    `;

    document.querySelectorAll(".player-button").forEach((button) => {
        button.addEventListener("click", () => selectPlayer(Number(button.dataset.playerId)));
    });
}

async function selectPlayer(playerId) {
    state.selectedPlayerId = playerId;
    document.querySelectorAll(".player-button").forEach((button) => {
        button.classList.toggle("active", Number(button.dataset.playerId) === playerId);
    });

    elements.playerDetail.className = "detail-stack";
    elements.playerDetail.innerHTML = loadingMarkup();

    try {
        // 선수 패널 전용 API로 프로필과 시즌/경기 스탯을 한 번에 가져온다.
        const panel = await requestJson(`/api/v1/players/${playerId}/panel`);
        renderPlayerPanel(panel);
    } catch (error) {
        elements.playerDetail.innerHTML = errorMarkup(error);
    }
}

function renderPlayerPanel(panel) {
    const profile = panel?.profile;
    if (!profile) {
        elements.playerDetail.innerHTML = emptyMarkup("선수 정보를 찾을 수 없습니다.");
        return;
    }

    const seasons = Array.isArray(panel.seasons) ? panel.seasons : [];
    const matches = Array.isArray(panel.matches) ? panel.matches : [];

    elements.playerDetail.innerHTML = `
        <div class="player-panel-grid">
            ${playerProfileMarkup(profile)}
            <div class="player-stat-stack">
                <div>
                    <div class="section-title"><h3>시즌별 기록</h3></div>
                    <div class="season-stat-list">
                        ${seasonAccordionsMarkup(seasons, matches)}
                    </div>
                </div>
            </div>
        </div>
    `;
}

function playerProfileMarkup(profile) {
    return `
        <article class="player-profile-card">
            ${imageMarkup(profile.photoUrl, profile.playerName, "player-photo")}
            <h3>${escapeHtml(profile.playerName || "-")}</h3>
            <p class="muted">${escapeHtml(profile.teamName || "-")} · ${escapeHtml(profile.position || "-")}</p>
            <div class="stat-row"><span>등번호</span><b>${numberText(profile.backNumber)}</b></div>
            <div class="stat-row"><span>나이</span><b>${numberText(profile.age)}</b></div>
            <div class="stat-row"><span>국적</span><b>${escapeHtml(profile.nationality || "-")}</b></div>
            <div class="stat-row"><span>신장</span><b>${escapeHtml(profile.height || "-")}</b></div>
            <div class="stat-row"><span>체중</span><b>${escapeHtml(profile.weight || "-")}</b></div>
        </article>
    `;
}

function seasonSummaryMarkup(season) {
    const teams = Array.isArray(season.teams) ? season.teams : [];

    return `
        <article class="season-stat-card">
            <strong>${seasonLabel(season.season)}</strong>
            <div class="season-stat-grid">
                <span><b>${numberText(season.totalFixtures)}</b>경기</span>
                <span><b>${numberText(season.minutesPlayed)}</b>분</span>
                <span><b>${numberText(season.goals)}</b>골</span>
                <span><b>${numberText(season.assists)}</b>도움</span>
                <span><b>${numberText(season.averageRating)}</b>평점</span>
                <span><b>${numberText(season.keyPasses)}</b>키패스</span>
            </div>
            ${teams.length > 1 ? teamSeasonBreakdownMarkup(teams) : ""}
        </article>
    `;
}

function teamSeasonBreakdownMarkup(teams) {
    return `
        <div class="team-season-list">
            ${teams.map((team) => `
                <article class="team-season-item">
                    <div class="team-season-head">
                        ${imageMarkup(team.teamLogoUrl, team.teamName, "club-logo")}
                        <strong>${escapeHtml(team.teamName || "-")}</strong>
                    </div>
                    <div class="season-stat-grid">
                        <span><b>${numberText(team.totalFixtures)}</b>경기</span>
                        <span><b>${numberText(team.minutesPlayed)}</b>분</span>
                        <span><b>${numberText(team.goals)}</b>골</span>
                        <span><b>${numberText(team.assists)}</b>도움</span>
                        <span><b>${numberText(team.averageRating)}</b>평점</span>
                        <span><b>${numberText(team.keyPasses)}</b>키패스</span>
                    </div>
                </article>
            `).join("")}
        </div>
    `;
}

function seasonAccordionsMarkup(seasons, matches) {
    const matchesBySeason = groupMatchesBySeason(matches);
    const seasonKeys = [
        ...new Set([
            ...seasons.map((season) => String(season.season ?? "unknown")),
            ...Object.keys(matchesBySeason),
        ]),
    ].sort((a, b) => seasonSortValue(b) - seasonSortValue(a));

    if (!seasonKeys.length) {
        return `<div class="player-item muted">시즌별 기록 없음</div>`;
    }

    return seasonKeys.map((seasonKey) => {
        const season = seasons.find((item) => String(item.season ?? "unknown") === String(seasonKey));
        const seasonMatches = matchesBySeason[seasonKey] || [];

        return `
            <details class="season-accordion">
                <summary>
                    <span>${seasonLabel(season?.season ?? seasonKey)}</span>
                </summary>
                ${season ? seasonSummaryMarkup(season) : ""}
                ${seasonMatchesMarkup(season, seasonMatches)}
            </details>
        `;
    }).join("");
}

function seasonMatchesMarkup(season, matches) {
    if (!matches.length) {
        return `<div class="match-stat-list"><div class="player-item muted">경기별 스탯 없음</div></div>`;
    }

    const teams = Array.isArray(season?.teams) ? season.teams : [];
    if (teams.length <= 1) {
        return `<div class="match-stat-list">${matches.map(matchStatMarkup).join("")}</div>`;
    }

    const matchesByTeam = groupMatchesByTeam(matches);

    return `
        <div class="team-match-list">
            ${teams.map((team) => {
                const teamMatches = matchesByTeam[String(team.teamId)] || [];
                return `
                    <section class="team-match-group">
                        <div class="team-match-heading">
                            ${imageMarkup(team.teamLogoUrl, team.teamName, "club-logo")}
                            <strong>${escapeHtml(team.teamName || "-")}</strong>
                        </div>
                        <div class="match-stat-list">
                            ${teamMatches.map(matchStatMarkup).join("") || `<div class="player-item muted">경기별 스탯 없음</div>`}
                        </div>
                    </section>
                `;
            }).join("")}
        </div>
    `;
}

function groupMatchesBySeason(matches) {
    return matches.reduce((groups, match) => {
        const key = match.season ?? "unknown";
        groups[key] = groups[key] || [];
        groups[key].push(match);
        return groups;
    }, {});
}

function groupMatchesByTeam(matches) {
    return matches.reduce((groups, match) => {
        const key = String(match.teamId ?? "unknown");
        groups[key] = groups[key] || [];
        groups[key].push(match);
        return groups;
    }, {});
}

function matchStatMarkup(match) {
    return `
        <article class="match-stat-item">
            <div>
                <strong>${escapeHtml(match.teamName || "-")} vs ${escapeHtml(match.opponentTeamName || "-")}</strong>
                <p class="muted">${dateText(match.fixtureDate)} · ${escapeHtml(match.round || `Fixture #${match.fixtureId}`)}</p>
            </div>
            <div class="match-stat-score">${numberText(match.teamScore)}:${numberText(match.opponentScore)}</div>
            <div class="match-stat-metrics">
                <span>골 <b>${numberText(match.goals)}</b></span>
                <span>도움 <b>${numberText(match.assists)}</b></span>
                <span>평점 <b>${numberText(match.rating)}</b></span>
            </div>
        </article>
    `;
}

function seasonLabel(season) {
    if (season === "unknown" || season === null || season === undefined) {
        return "시즌 미상";
    }

    const startYear = Number(season);
    if (!Number.isFinite(startYear)) {
        return `${escapeHtml(season)} 시즌`;
    }

    return `${startYear}/${String((startYear + 1) % 100).padStart(2, "0")} 시즌`;
}

function seasonSortValue(season) {
    if (season === "unknown") {
        return -1;
    }
    const value = Number(season);
    return Number.isFinite(value) ? value : -1;
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

function parseEventData(event) {
    try {
        return JSON.parse(event.data);
    } catch (error) {
        return null;
    }
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

function dateText(value) {
    return value ? String(value).slice(0, 10) : "-";
}

function dateKey(value) {
    const date = parseFixtureDate(value);
    if (!date) {
        return "unknown";
    }

    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(date);
}

function dateGroupTitle(value) {
    if (value === "unknown") {
        return "날짜 미정";
    }

    const date = new Date(`${value}T00:00:00+09:00`);
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    const today = new Date(`${todayKoreaDateKey()}T00:00:00+09:00`);

    const diffDays = Math.round((date.getTime() - today.getTime()) / 86400000);
    const weekday = new Intl.DateTimeFormat("ko-KR", {timeZone: "Asia/Seoul", weekday: "short"}).format(date);
    const formatted = new Intl.DateTimeFormat("ko-KR", {
        timeZone: "Asia/Seoul",
        month: "long",
        day: "numeric",
    }).format(date);

    if (diffDays === 0) {
        return `오늘, ${formatted} (${weekday})`;
    }
    if (diffDays === 1) {
        return `내일, ${formatted} (${weekday})`;
    }
    if (diffDays === -1) {
        return `어제, ${formatted} (${weekday})`;
    }

    return `${formatted} (${weekday})`;
}

function selectedFixtureDateLabel() {
    return dateGroupTitle(state.selectedFixtureDate);
}

function timeText(value) {
    const date = parseFixtureDate(value);
    if (!date) {
        return String(value).slice(11, 16) || "-";
    }

    return new Intl.DateTimeFormat("ko-KR", {
        timeZone: "Asia/Seoul",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
    }).format(date);
}

function fixtureTimeValue(fixture) {
    return parseFixtureDate(fixture.fixtureDate)?.getTime() ?? 0;
}

function parseFixtureDate(value) {
    if (!value) {
        return null;
    }

    const text = String(value);
    const isoText = /Z$|[+-]\d\d:\d\d$/.test(text) ? text : `${text}Z`;
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? null : date;
}

function todayKoreaDateKey() {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(new Date());
}

function addDaysToDateKey(dateKeyValue, dayDelta) {
    const date = new Date(`${dateKeyValue}T00:00:00+09:00`);
    date.setUTCDate(date.getUTCDate() + dayDelta);

    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(date);
}

function setApiStatus(status) {
    elements.apiStatus.textContent = status;
}

function setLiveStatus(status) {
    elements.liveConnectionStatus.textContent = status;
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

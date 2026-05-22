const adminState = {
    user: null,
    selectedTeam: null,
    selectedPlayer: null,
    syncStatuses: {},
};

const adminElements = {
    userLabel: document.querySelector("#adminUserLabel"),
    logoutButton: document.querySelector("#adminLogoutButton"),
    status: document.querySelector("#adminStatus"),
    teamKeyword: document.querySelector("#teamKeyword"),
    teamSearchButton: document.querySelector("#teamSearchButton"),
    teamResults: document.querySelector("#teamResults"),
    teamForm: document.querySelector("#teamForm"),
    playerKeyword: document.querySelector("#playerKeyword"),
    playerSearchButton: document.querySelector("#playerSearchButton"),
    playerResults: document.querySelector("#playerResults"),
    playerForm: document.querySelector("#playerForm"),
    syncLeague: document.querySelector("#syncLeague"),
    syncSeason: document.querySelector("#syncSeason"),
    syncDelayMs: document.querySelector("#syncDelayMs"),
    syncFixtureId: document.querySelector("#syncFixtureId"),
    syncResult: document.querySelector("#syncResult"),
    syncStatusLabels: document.querySelectorAll("[data-sync-status]"),
    refreshLogsButton: document.querySelector("#refreshLogsButton"),
    auditLogs: document.querySelector("#auditLogs"),
};

document.addEventListener("DOMContentLoaded", () => {
    adminElements.logoutButton.addEventListener("click", logout);
    adminElements.teamSearchButton.addEventListener("click", searchTeams);
    adminElements.playerSearchButton.addEventListener("click", searchPlayers);
    adminElements.teamForm.addEventListener("submit", saveTeam);
    adminElements.playerForm.addEventListener("submit", savePlayer);
    adminElements.refreshLogsButton.addEventListener("click", refreshAdminLogs);
    document.querySelectorAll("[data-sync-task]").forEach((button) => {
        button.addEventListener("click", () => runSync(button.dataset.syncTask));
    });
    initializeAdmin();
});

async function initializeAdmin() {
    try {
        adminState.user = await requestJson("/api/v1/auth/me");
        adminElements.userLabel.textContent = `${adminState.user.nickname || adminState.user.email} · ${adminState.user.role}`;
        adminElements.status.innerHTML = `<strong>관리자 권한 확인됨</strong><p class="muted">팀/선수 정보 수정과 수동 동기화를 실행할 수 있습니다.</p>`;
        await loadSyncStatuses();
        await loadAuditLogs();
    } catch (error) {
        adminElements.status.innerHTML = errorMarkup(error);
    }
}

async function searchTeams() {
    adminElements.teamResults.innerHTML = loadingMarkup();
    const keyword = encodeURIComponent(adminElements.teamKeyword.value || "");
    try {
        const teams = await requestJson(`/api/v1/admin/teams?keyword=${keyword}`);
        adminElements.teamResults.innerHTML = teams.map((team) => `
            <button class="admin-result-button" type="button" data-team-id="${team.teamId}">
                <span>${escapeHtml(team.name || "-")}</span>
                <span class="small-pill">#${team.teamId}</span>
            </button>
        `).join("") || emptyMarkup("검색 결과가 없습니다.");
        document.querySelectorAll("[data-team-id]").forEach((button) => {
            button.addEventListener("click", () => renderTeamForm(teams.find((team) => team.teamId === Number(button.dataset.teamId))));
        });
    } catch (error) {
        adminElements.teamResults.innerHTML = errorMarkup(error);
    }
}

async function searchPlayers() {
    adminElements.playerResults.innerHTML = loadingMarkup();
    const keyword = encodeURIComponent(adminElements.playerKeyword.value || "");
    try {
        const players = await requestJson(`/api/v1/admin/players?keyword=${keyword}`);
        adminElements.playerResults.innerHTML = players.map((player) => `
            <button class="admin-result-button" type="button" data-player-id="${player.playerId}">
                <span>${escapeHtml(player.name || "-")}</span>
                <span class="small-pill">#${player.playerId}</span>
            </button>
        `).join("") || emptyMarkup("검색 결과가 없습니다.");
        document.querySelectorAll("[data-player-id]").forEach((button) => {
            button.addEventListener("click", () => renderPlayerForm(players.find((player) => player.playerId === Number(button.dataset.playerId))));
        });
    } catch (error) {
        adminElements.playerResults.innerHTML = errorMarkup(error);
    }
}

function renderTeamForm(team) {
    adminState.selectedTeam = team;
    const overrides = overrideMap(team.manualOverrides);
    adminElements.teamForm.hidden = false;
    adminElements.teamForm.innerHTML = `
        <h3>${escapeHtml(team.name || "Team")}</h3>
        ${overrideSummaryMarkup(team.manualOverrides, "team")}
        <div class="admin-form-grid">
            ${inputMarkup("name", "Name", team.name, "text", overrides.name)}
            ${inputMarkup("code", "Code", team.code, "text", overrides.code)}
            ${inputMarkup("country", "Country", team.country, "text", overrides.country)}
            ${inputMarkup("founded", "Founded", team.founded, "number", overrides.founded)}
            ${inputMarkup("logoUrl", "Logo URL", team.logoUrl, "text", overrides.logoUrl)}
            ${inputMarkup("venueId", "Venue ID", team.venueId, "number", overrides.venueId)}
            ${inputMarkup("venueName", "Venue Name", team.venueName, "text", overrides.venueName)}
            ${inputMarkup("venueAddress", "Venue Address", team.venueAddress, "text", overrides.venueAddress)}
            ${inputMarkup("venueCity", "Venue City", team.venueCity, "text", overrides.venueCity)}
            ${inputMarkup("capacity", "Capacity", team.capacity, "number", overrides.capacity)}
            ${inputMarkup("surface", "Surface", team.surface, "text", overrides.surface)}
            ${inputMarkup("venueImageUrl", "Venue Image URL", team.venueImageUrl, "text", overrides.venueImageUrl)}
        </div>
        <button type="submit">Save Team</button>
    `;
    bindOverrideButtons(adminElements.teamForm, "team");
}

function renderPlayerForm(player) {
    adminState.selectedPlayer = player;
    const overrides = overrideMap(player.manualOverrides);
    adminElements.playerForm.hidden = false;
    adminElements.playerForm.innerHTML = `
        <h3>${escapeHtml(player.name || "Player")}</h3>
        ${overrideSummaryMarkup(player.manualOverrides, "player")}
        <div class="admin-form-grid">
            ${inputMarkup("name", "Name", player.name, "text", overrides.name)}
            ${inputMarkup("firstname", "Firstname", player.firstname, "text", overrides.firstname)}
            ${inputMarkup("lastname", "Lastname", player.lastname, "text", overrides.lastname)}
            ${inputMarkup("age", "Age", player.age, "number", overrides.age)}
            ${inputMarkup("birthDate", "Birth Date", player.birthDate, "date", overrides.birthDate)}
            ${inputMarkup("birthPlace", "Birth Place", player.birthPlace, "text", overrides.birthPlace)}
            ${inputMarkup("birthCountry", "Birth Country", player.birthCountry, "text", overrides.birthCountry)}
            ${inputMarkup("nationality", "Nationality", player.nationality, "text", overrides.nationality)}
            ${inputMarkup("height", "Height", player.height, "text", overrides.height)}
            ${inputMarkup("weight", "Weight", player.weight, "text", overrides.weight)}
            ${inputMarkup("position", "Position", player.position, "text", overrides.position)}
            ${inputMarkup("number", "Number", player.number, "number", overrides.number)}
            ${inputMarkup("photoUrl", "Photo URL", player.photoUrl, "text", overrides.photoUrl)}
        </div>
        <button type="submit">Save Player</button>
    `;
    bindOverrideButtons(adminElements.playerForm, "player");
}

async function saveTeam(event) {
    event.preventDefault();
    const body = formBody(adminElements.teamForm);
    try {
        adminState.selectedTeam = await putJson(`/api/v1/admin/teams/${adminState.selectedTeam.teamId}`, body);
        renderTeamForm(adminState.selectedTeam);
        await loadAuditLogs();
    } catch (error) {
        adminElements.teamResults.innerHTML = errorMarkup(error);
    }
}

async function savePlayer(event) {
    event.preventDefault();
    const body = formBody(adminElements.playerForm);
    try {
        adminState.selectedPlayer = await putJson(`/api/v1/admin/players/${adminState.selectedPlayer.playerId}`, body);
        renderPlayerForm(adminState.selectedPlayer);
        await loadAuditLogs();
    } catch (error) {
        adminElements.playerResults.innerHTML = errorMarkup(error);
    }
}

async function clearTeamOverride(fieldName) {
    const url = fieldName
        ? `/api/v1/admin/teams/${adminState.selectedTeam.teamId}/overrides/${encodeURIComponent(fieldName)}`
        : `/api/v1/admin/teams/${adminState.selectedTeam.teamId}/overrides`;
    adminState.selectedTeam = await deleteJson(url);
    renderTeamForm(adminState.selectedTeam);
    await loadAuditLogs();
}

async function clearPlayerOverride(fieldName) {
    const url = fieldName
        ? `/api/v1/admin/players/${adminState.selectedPlayer.playerId}/overrides/${encodeURIComponent(fieldName)}`
        : `/api/v1/admin/players/${adminState.selectedPlayer.playerId}/overrides`;
    adminState.selectedPlayer = await deleteJson(url);
    renderPlayerForm(adminState.selectedPlayer);
    await loadAuditLogs();
}

async function runSync(task) {
    adminElements.syncResult.textContent = "Running...";
    const league = encodeURIComponent(adminElements.syncLeague.value || "39");
    const season = encodeURIComponent(adminElements.syncSeason.value || "2025");
    const delayMs = encodeURIComponent(adminElements.syncDelayMs.value || "7000");
    const fixtureId = adminElements.syncFixtureId.value;

    const urls = {
        teams: `/api/v1/admin/sync/teams?league=${league}&season=${season}`,
        standings: `/api/v1/admin/sync/standings?league=${league}&season=${season}`,
        fixtures: `/api/v1/admin/sync/fixtures?league=${league}&season=${season}`,
        "fixture-details": `/api/v1/admin/sync/fixture-details?season=${season}`,
        "fixture-detail": fixtureId ? `/api/v1/admin/sync/fixture-details/${encodeURIComponent(fixtureId)}` : null,
        players: `/api/v1/admin/sync/players?league=${league}&season=${season}&delayMs=${delayMs}`,
        injuries: `/api/v1/admin/sync/injuries?league=${league}&season=${season}`,
    };

    if (!urls[task]) {
        adminElements.syncResult.textContent = "Fixture ID is required.";
        return;
    }

    try {
        const result = await requestJson(urls[task], {method: "POST"});
        adminElements.syncResult.textContent = result.message;
        await loadSyncStatuses();
        await loadAuditLogs();
    } catch (error) {
        adminElements.syncResult.textContent = error.message;
    }
}

async function refreshAdminLogs() {
    await loadSyncStatuses();
    await loadAuditLogs();
}

async function loadSyncStatuses() {
    try {
        const response = await requestJson("/api/v1/admin/sync/statuses");
        const statuses = Array.isArray(response.statuses) ? response.statuses : [];
        adminState.syncStatuses = Object.fromEntries(statuses.map((status) => [status.task, status]));
    } catch (error) {
        adminState.syncStatuses = {};
    }
    renderSyncStatuses();
}

function renderSyncStatuses() {
    adminElements.syncStatusLabels.forEach((element) => {
        const task = element.dataset.syncStatus;
        const status = adminState.syncStatuses[task];
        element.textContent = `갱신 일시: ${formatKoreaDateTime(status?.lastSyncedAt)}`;
    });
}

async function loadAuditLogs() {
    try {
        const response = await requestJson("/api/v1/admin/audit-logs");
        const logs = Array.isArray(response.logs) ? response.logs : [];
        adminElements.auditLogs.innerHTML = logs.map((log) => `
            <article class="admin-log-item">
                <span class="small-pill">${escapeHtml(log.type)}</span>
                <div>
                    <strong>${escapeHtml(log.message)}</strong>
                    ${log.details ? `<p class="muted">${escapeHtml(log.details)}</p>` : ""}
                    <p class="muted">${escapeHtml(log.adminEmail || "-")} · ${escapeHtml(log.createdAt || "-")}</p>
                </div>
                <span class="small-pill">${log.success ? "OK" : "FAIL"}</span>
            </article>
        `).join("") || emptyMarkup("아직 관리자 이력이 없습니다.");
    } catch (error) {
        adminElements.auditLogs.innerHTML = errorMarkup(error);
    }
}

async function logout() {
    await fetch("/api/v1/auth/logout", {method: "POST", credentials: "same-origin"});
    window.location.href = "/";
}

function inputMarkup(name, label, value, type = "text", override = null) {
    return `
        <label>${labelMarkup(label, override)}
            <input name="${name}" type="${type}" value="${escapeAttribute(value ?? "")}">
            ${override ? `<button class="ghost-button admin-clear-override" type="button" data-override-field="${escapeAttribute(name)}">해제</button>` : ""}
        </label>
    `;
}

function labelMarkup(label, override) {
    if (!override) {
        return escapeHtml(label);
    }
    return `
        <span class="admin-field-label">
            <span>${escapeHtml(label)}</span>
            <span class="small-pill">수동 수정 · ${formatKoreaDateTime(override.updatedAt)}</span>
        </span>
    `;
}

function overrideSummaryMarkup(overrides, type) {
    const items = Array.isArray(overrides) ? overrides : [];
    if (items.length === 0) {
        return "";
    }
    return `
        <div class="admin-override-summary">
            <span>${items.length}개 필드가 수동 수정 보호 중입니다.</span>
            <button class="ghost-button" type="button" data-clear-all-overrides="${type}">전체 해제</button>
        </div>
    `;
}

function bindOverrideButtons(form, type) {
    form.querySelectorAll("[data-override-field]").forEach((button) => {
        button.addEventListener("click", async () => {
            if (!confirm("현재 값은 유지되고 다음 전체 동기화 때 API 값으로 갱신됩니다. 이 필드의 수동 수정 보호를 해제할까요?")) {
                return;
            }
            try {
                if (type === "team") {
                    await clearTeamOverride(button.dataset.overrideField);
                } else {
                    await clearPlayerOverride(button.dataset.overrideField);
                }
            } catch (error) {
                form.insertAdjacentHTML("afterbegin", errorMarkup(error));
            }
        });
    });
    form.querySelectorAll("[data-clear-all-overrides]").forEach((button) => {
        button.addEventListener("click", async () => {
            if (!confirm("현재 값은 유지되고 다음 전체 동기화 때 API 값으로 갱신됩니다. 모든 수동 수정 보호를 해제할까요?")) {
                return;
            }
            try {
                if (type === "team") {
                    await clearTeamOverride(null);
                } else {
                    await clearPlayerOverride(null);
                }
            } catch (error) {
                form.insertAdjacentHTML("afterbegin", errorMarkup(error));
            }
        });
    });
}

function overrideMap(overrides) {
    const entries = Array.isArray(overrides) ? overrides : [];
    return Object.fromEntries(entries.map((override) => [override.fieldName, override]));
}

function formBody(form) {
    const body = {};
    new FormData(form).forEach((value, key) => {
        body[key] = value === "" ? null : value;
        if (["founded", "venueId", "capacity", "age", "number"].includes(key) && body[key] !== null) {
            body[key] = Number(body[key]);
        }
    });
    return body;
}

async function putJson(url, body) {
    return requestJson(url, {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
}

async function deleteJson(url) {
    return requestJson(url, {method: "DELETE"});
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        credentials: "same-origin",
        headers: {
            "Accept": "application/json",
            ...(options.headers || {}),
        },
    });
    if (!response.ok) {
        throw await responseError(response, `${url} failed (${response.status})`);
    }
    return response.status === 204 ? null : response.json();
}

async function responseError(response, fallbackMessage) {
    let message = fallbackMessage;
    try {
        const errorBody = await response.json();
        message = errorBody.message || message;
    } catch (error) {
        message = fallbackMessage;
    }
    return new Error(message);
}

function loadingMarkup() {
    return `<div class="loading">Loading...</div>`;
}

function emptyMarkup(message) {
    return `<div class="empty">${escapeHtml(message)}</div>`;
}

function errorMarkup(error) {
    return `<div class="error">${escapeHtml(error.message || "Request failed.")}</div>`;
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

function formatKoreaDateTime(value) {
    if (!value) {
        return "-";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "-";
    }

    return new Intl.DateTimeFormat("ko-KR", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
    }).format(date);
}

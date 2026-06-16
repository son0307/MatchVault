import http from "k6/http";
import { check, fail, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://matchvault.site").replace(/\/+$/, "");
const LOGIN_URL = `${BASE_URL}/api/v1/auth/login`;
const DASHBOARD_URL = `${BASE_URL}/api/v1/favorites/dashboard`;
const SEASON = __ENV.SEASON || "2025";
const VUS = Number(__ENV.VUS || "50");
const DURATION = __ENV.DURATION || "30s";
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || "0.1");
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "10s";
const ORIGIN_IP = __ENV.ORIGIN_IP || "";
const ORIGIN_HOST = __ENV.ORIGIN_HOST || "matchvault.site";

export const options = buildOptions();

function buildOptions() {
  const baseOptions = {
  scenarios: {
    favorites_dashboard: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: __ENV.GRACEFUL_STOP || "5s",
    },
  },
  thresholds: {
    "http_req_failed{endpoint:favorites_dashboard}": ["rate<0.01"],
    "http_req_duration{endpoint:favorites_dashboard}": ["p(95)<500", "p(99)<1000"],
  },
  };

  if (ORIGIN_IP) {
    baseOptions.hosts = {
      [ORIGIN_HOST]: ORIGIN_IP,
    };
  }

  return baseOptions;
}

export function setup() {
  const users = loadUsers();
  return {
    sessions: users.map((user, index) => login(user, index)),
  };
}

export default function (data) {
  const session = data.sessions[(__VU - 1) % data.sessions.length];
  const url = `${DASHBOARD_URL}?season=${encodeURIComponent(SEASON)}`;
  const requestId = `k6-${session.userIndex}-${__VU}-${__ITER}-${Date.now()}`;

  const response = http.get(url, {
    headers: {
      Accept: "application/json",
      Cookie: session.cookieHeader,
      "X-Load-Test-Request-Id": requestId,
    },
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint: "favorites_dashboard",
      user_index: session.userIndex,
    },
  });

  check(response, {
    "dashboard status is 200": (res) => res.status === 200,
    "dashboard has teams array": (res) => hasArrayField(res, "teams"),
    "dashboard has players array": (res) => hasArrayField(res, "players"),
    "dashboard response is under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(SLEEP_SECONDS);
}

function loadUsers() {
  if (__ENV.LOAD_TEST_USERS) {
    return __ENV.LOAD_TEST_USERS.split(",")
      .map((value) => value.trim())
      .filter(Boolean)
      .map(parseUser);
  }

  if (!__ENV.TEST_EMAIL || !__ENV.TEST_PASSWORD) {
    fail("Set TEST_EMAIL and TEST_PASSWORD, or provide LOAD_TEST_USERS=email:password,email2:password2.");
  }

  return [{
    email: __ENV.TEST_EMAIL,
    password: __ENV.TEST_PASSWORD,
  }];
}

function parseUser(value) {
  const separatorIndex = value.indexOf(":");
  if (separatorIndex <= 0) {
    fail(`Invalid LOAD_TEST_USERS entry: ${value}`);
  }

  return {
    email: value.slice(0, separatorIndex),
    password: value.slice(separatorIndex + 1),
  };
}

function login(user, index) {
  const response = http.post(
    LOGIN_URL,
    JSON.stringify({
      email: user.email,
      password: user.password,
    }),
    {
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      jar: new http.CookieJar(),
      timeout: REQUEST_TIMEOUT,
      tags: {
        endpoint: "auth_login",
        user_index: String(index + 1),
      },
    },
  );

  const ok = check(response, {
    "login status is 200": (res) => res.status === 200,
    "login returned session cookie": (res) => Boolean(sessionCookie(res)),
  });

  if (!ok) {
    fail(`Login failed for ${user.email}. status=${response.status}`);
  }

  return {
    cookieHeader: sessionCookie(response),
    userIndex: String(index + 1),
  };
}

function sessionCookie(response) {
  const cookie = response.cookies.JSESSIONID && response.cookies.JSESSIONID[0];
  if (!cookie || !cookie.value) {
    return "";
  }

  return `JSESSIONID=${cookie.value}`;
}

function hasArrayField(response, fieldName) {
  try {
    const body = response.json();
    return Array.isArray(body[fieldName]);
  } catch (error) {
    return false;
  }
}

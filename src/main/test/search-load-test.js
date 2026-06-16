import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://matchvault.site").replace(/\/+$/, "");
const SEARCH_URL = `${BASE_URL}/api/v1/search`;
const VUS = Number(__ENV.VUS || "50");
const DURATION = __ENV.DURATION || "30s";
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || "0.1");
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || "10s";
const SEARCH_TYPE = __ENV.SEARCH_TYPE || "all";
const SEARCH_TERMS = loadSearchTerms();
const ORIGIN_IP = __ENV.ORIGIN_IP || "";
const ORIGIN_HOST = __ENV.ORIGIN_HOST || "matchvault.site";

export const options = buildOptions();

function buildOptions() {
  const baseOptions = {
  scenarios: {
    search: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      gracefulStop: __ENV.GRACEFUL_STOP || "5s",
    },
  },
  thresholds: {
    "http_req_failed{endpoint:search}": ["rate<0.01"],
    "http_req_duration{endpoint:search}": ["p(95)<500", "p(99)<1000"],
  },
  };

  if (ORIGIN_IP) {
    baseOptions.hosts = {
      [ORIGIN_HOST]: ORIGIN_IP,
    };
  }

  return baseOptions;
}

export default function () {
  const keyword = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
  const requestId = `k6-search-${__VU}-${__ITER}-${Date.now()}`;
  const url = `${SEARCH_URL}?q=${encodeURIComponent(keyword)}&type=${encodeURIComponent(SEARCH_TYPE)}`;

  const response = http.get(url, {
    headers: {
      Accept: "application/json",
      "X-Load-Test-Request-Id": requestId,
    },
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint: "search",
      search_type: SEARCH_TYPE,
      keyword: keyword,
    },
  });

  check(response, {
    "search status is 200": (res) => res.status === 200,
    "search has teams array": (res) => hasArrayField(res, "teams"),
    "search has players array": (res) => hasArrayField(res, "players"),
    "search has fixtures array": (res) => hasArrayField(res, "fixtures"),
    "search response is under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(SLEEP_SECONDS);
}

function loadSearchTerms() {
  if (__ENV.SEARCH_TERMS) {
    const terms = __ENV.SEARCH_TERMS.split(",")
      .map((value) => value.trim())
      .filter(Boolean);
    if (terms.length > 0) {
      return terms;
    }
  }

  return [
    "arsenal",
    "chelsea",
    "liverpool",
    "manchester",
    "tottenham",
    "son",
    "salah",
    "haaland",
    "bruno",
    "palmer",
    "arsenal chelsea",
    "manchester united",
  ];
}

function hasArrayField(response, fieldName) {
  try {
    const body = response.json();
    return Array.isArray(body[fieldName]);
  } catch (error) {
    return false;
  }
}

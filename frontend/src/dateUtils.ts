const KOREA_TIME_ZONE = "Asia/Seoul";

const YEAR_FORMATTER = new Intl.DateTimeFormat("en", {
  timeZone: KOREA_TIME_ZONE,
  year: "numeric",
});

export function parseKoreaDateTime(value: string | null) {
  if (!value) {
    return null;
  }
  const text = /(?:z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}+09:00`;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function parseKoreaDateKey(value: string | null) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }

  const date = new Date(`${value}T00:00:00+09:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatFixtureDateTime(value: string | null, fallback = "-") {
  return formatFixtureDateValue(parseKoreaDateTime(value), fallback, true);
}

export function formatFixtureDate(value: string | null, fallback = "-") {
  return formatFixtureDateValue(parseKoreaDateTime(value), fallback, false);
}

export function formatFixtureDateKey(value: string | null, fallback = "-") {
  return formatFixtureDateValue(parseKoreaDateKey(value), fallback, false);
}

function formatFixtureDateValue(date: Date | null, fallback: string, includeTime: boolean) {
  if (!date) {
    return fallback;
  }

  const currentYear = Number(YEAR_FORMATTER.format(new Date()));
  const fixtureYear = Number(YEAR_FORMATTER.format(date));
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KOREA_TIME_ZONE,
    ...(fixtureYear === currentYear ? {} : { year: "numeric" }),
    month: "long",
    day: "numeric",
    weekday: "short",
    ...(includeTime
      ? {
          hour: "2-digit",
          minute: "2-digit",
          hour12: false,
        }
      : {}),
  }).format(date);
}

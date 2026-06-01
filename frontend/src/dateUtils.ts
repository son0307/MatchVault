const KOREA_TIME_ZONE = "Asia/Seoul";

export function parseKoreaDateTime(value: string | null) {
  if (!value) {
    return null;
  }
  const text = /(?:z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}+09:00`;
  const date = new Date(text);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatFixtureDateTime(value: string | null, fallback = "-") {
  const date = parseKoreaDateTime(value);
  if (!date) {
    return fallback;
  }

  const currentYear = Number(
    new Intl.DateTimeFormat("en", {
      timeZone: KOREA_TIME_ZONE,
      year: "numeric",
    }).format(new Date()),
  );
  const fixtureYear = Number(
    new Intl.DateTimeFormat("en", {
      timeZone: KOREA_TIME_ZONE,
      year: "numeric",
    }).format(date),
  );

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KOREA_TIME_ZONE,
    ...(fixtureYear === currentYear ? {} : { year: "numeric" }),
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

export function formatFixtureDate(value: string | null, fallback = "-") {
  const date = parseKoreaDateTime(value);
  if (!date) {
    return fallback;
  }

  const currentYear = Number(
    new Intl.DateTimeFormat("en", {
      timeZone: KOREA_TIME_ZONE,
      year: "numeric",
    }).format(new Date()),
  );
  const fixtureYear = Number(
    new Intl.DateTimeFormat("en", {
      timeZone: KOREA_TIME_ZONE,
      year: "numeric",
    }).format(date),
  );

  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KOREA_TIME_ZONE,
    ...(fixtureYear === currentYear ? {} : { year: "numeric" }),
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
}

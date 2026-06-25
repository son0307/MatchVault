const koreanTeamNamesById: Record<number, string> = {
  33: "맨체스터 유나이티드",
  34: "뉴캐슬 유나이티드",
  35: "본머스",
  36: "풀럼",
  39: "울버햄튼",
  40: "리버풀",
  41: "사우샘프턴",
  42: "아스널",
  44: "번리",
  45: "에버턴",
  46: "레스터 시티",
  47: "토트넘",
  48: "웨스트햄 유나이티드",
  49: "첼시",
  50: "맨체스터 시티",
  51: "브라이튼",
  52: "크리스탈 팰리스",
  55: "브렌트포드",
  57: "입스위치 타운",
  62: "셰필드 유나이티드",
  63: "리즈 유나이티드",
  65: "노팅엄 포레스트",
  66: "애스턴 빌라",
  746: "선덜랜드",
};

const koreanTeamNamesByEnglishName: Record<string, string> = {
  arsenal: "아스널",
  "aston villa": "애스턴 빌라",
  bournemouth: "본머스",
  brentford: "브렌트포드",
  brighton: "브라이튼",
  "brighton & hove albion": "브라이튼",
  burnley: "번리",
  chelsea: "첼시",
  "crystal palace": "크리스탈 팰리스",
  everton: "에버턴",
  fulham: "풀럼",
  ipswich: "입스위치 타운",
  "ipswich town": "입스위치 타운",
  leeds: "리즈 유나이티드",
  "leeds united": "리즈 유나이티드",
  leicester: "레스터 시티",
  "leicester city": "레스터 시티",
  liverpool: "리버풀",
  "manchester city": "맨체스터 시티",
  "manchester united": "맨체스터 유나이티드",
  "man united": "맨체스터 유나이티드",
  "man utd": "맨체스터 유나이티드",
  newcastle: "뉴캐슬 유나이티드",
  "newcastle united": "뉴캐슬 유나이티드",
  "nottingham forest": "노팅엄 포레스트",
  "sheffield united": "셰필드 유나이티드",
  southampton: "사우샘프턴",
  sunderland: "선덜랜드",
  tottenham: "토트넘",
  "tottenham hotspur": "토트넘",
  "west ham": "웨스트햄 유나이티드",
  "west ham united": "웨스트햄 유나이티드",
  wolves: "울버햄튼",
  "wolverhampton wanderers": "울버햄튼",
};

export function displayTeamName(teamId: number | null | undefined, fallbackName: string | null | undefined) {
  if (teamId && koreanTeamNamesById[teamId]) {
    return koreanTeamNamesById[teamId];
  }
  const normalizedName = fallbackName?.trim().toLowerCase();
  if (normalizedName && koreanTeamNamesByEnglishName[normalizedName]) {
    return koreanTeamNamesByEnglishName[normalizedName];
  }
  return fallbackName ?? "-";
}

export function displayLocalizedName(koreanName: string | null | undefined, fallbackName: string | null | undefined) {
  return koreanName?.trim() || fallbackName || "-";
}

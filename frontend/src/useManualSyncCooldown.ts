import { useEffect, useState } from "react";

const MANUAL_SYNC_COOLDOWN_MS = 30_000;
const STORAGE_PREFIX = "admin-sync-cooldown";

export function useManualSyncCooldown(task: string, season: number) {
  const storageKey = `${STORAGE_PREFIX}:${task}:${season}`;
  const [cooldownUntil, setCooldownUntil] = useState(() => readCooldown(storageKey));
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    setCooldownUntil(readCooldown(storageKey));
    setClock(Date.now());

    function handleStorage(event: StorageEvent) {
      if (event.key !== storageKey) {
        return;
      }
      setCooldownUntil(readCooldown(storageKey));
      setClock(Date.now());
    }

    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  }, [storageKey]);

  useEffect(() => {
    if (cooldownUntil <= clock) {
      return;
    }
    const timerId = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timerId);
  }, [cooldownUntil, clock]);

  function startCooldown() {
    const now = Date.now();
    const nextCooldownUntil = now + MANUAL_SYNC_COOLDOWN_MS;
    try {
      window.localStorage.setItem(storageKey, String(nextCooldownUntil));
    } catch {
      // The in-memory cooldown still protects this page when storage is unavailable.
    }
    setClock(now);
    setCooldownUntil(nextCooldownUntil);
  }

  return {
    cooldownUntil,
    cooldownSeconds: Math.max(0, Math.ceil((cooldownUntil - clock) / 1000)),
    startCooldown,
  };
}

function readCooldown(storageKey: string) {
  try {
    const storedUntil = Number(window.localStorage.getItem(storageKey));
    if (Number.isFinite(storedUntil) && storedUntil > Date.now()) {
      return storedUntil;
    }
    window.localStorage.removeItem(storageKey);
  } catch {
    // Fall back to an expired cooldown when storage is unavailable.
  }
  return 0;
}

// Heartbeats, open notifications and successful MVC reads do not reset this data watchdog.
export function liveFallback(refresh: () => void) {
  let closed = false;
  let polling: ReturnType<typeof setInterval> | undefined;
  let stale: ReturnType<typeof setTimeout> | undefined;
  function startPolling() {
    if (closed || polling !== undefined) return;
    polling = setInterval(refresh, 60_000);
    refresh();
  }
  function dataReceived() {
    if (closed) return;
    clearInterval(polling);
    polling = undefined;
    clearTimeout(stale);
    stale = setTimeout(startPolling, 180_000);
  }
  dataReceived();
  return {
    dataReceived,
    disconnected: startPolling,
    close() {
      closed = true;
      clearInterval(polling);
      clearTimeout(stale);
    },
  };
}

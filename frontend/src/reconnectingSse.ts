type Handlers = {
  onOpen: (readyState: number) => void;
  onError: (readyState: number) => void;
  events: Record<string, (event: MessageEvent) => void>;
};

let identityRequest: Promise<void> | undefined;
function ensureBrowserIdentity(): Promise<void> {
  if (identityRequest) return identityRequest;
  const issue = async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);
    try {
      const response = await fetch('/api/v1/live/identity', {
        credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
      });
      if (!response.ok) throw new Error('SSE identity unavailable');
    } finally { clearTimeout(timeout); }
  };
  // Serialize first issuance across same-origin tabs; the cookie is HttpOnly.
  identityRequest = (async () => {
    if (typeof navigator !== 'undefined' && navigator.locks) {
      await navigator.locks.request('sse-browser-identity', issue);
    } else { await issue(); }
  })().finally(() => { identityRequest = undefined; });
  return identityRequest;
}

// Own retries so browser retries and application timers cannot create duplicate connections.
export function reconnectingSse(url: string, handlers: Handlers) {
  let source: EventSource | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let stableTimer: ReturnType<typeof setTimeout> | undefined;
  let connectTimer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;
  let failures = 0;

  function retry(readyState: number) {
    handlers.onError(readyState);
    if (stopped) return;
    const cap = Math.min(10_000, 1000 * 2 ** Math.min(failures++, 4));
    retryTimer = setTimeout(connect, cap * (0.5 + Math.random() * 0.5));
  }

  async function connect() {
    if (stopped) return;
    try { await ensureBrowserIdentity(); }
    catch { if (!stopped) retry(0); return; }
    if (stopped) return;
    const current = new EventSource(url);
    source = current;
    const active = () => !stopped && source === current;
    const fail = () => {
      if (!active()) return;
      const readyState = current.readyState;
      source = null;
      current.close();
      clearTimeout(stableTimer);
      clearTimeout(connectTimer);
      retry(readyState);
    };
    current.onerror = fail;
    current.onopen = () => {
      if (!active()) return;
      clearTimeout(connectTimer);
      // An immediate open/close loop must not continually reset the backoff.
      stableTimer = setTimeout(() => { if (active()) failures = 0; }, 30_000);
      handlers.onOpen(current.readyState);
    };
    for (const [name, handler] of Object.entries(handlers.events)) {
      current.addEventListener(name, event => { if (active()) handler(event as MessageEvent); });
    }
    connectTimer = setTimeout(fail, 15_000);
  }

  connect();
  return {
    close() {
      stopped = true;
      clearTimeout(retryTimer);
      clearTimeout(stableTimer);
      clearTimeout(connectTimer);
      source?.close();
      source = null;
    },
  };
}

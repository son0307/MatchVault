import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "SSE_");
  const target = env.SSE_PROXY_TARGET || "http://127.0.0.1:8081";
  if (!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(target)) {
    throw new Error("SSE_PROXY_TARGET must be a local HTTP origin");
  }
  return {
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/v1/live/stream": {
        target,
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0,
        configure(proxy) {
          // A broken upstream stream must also end the browser-facing response.
          // pipe() alone leaves an already-started SSE response open on abort.
          proxy.on("proxyRes", (upstream, _request, downstream) => {
            const abort = () => { if (!downstream.destroyed) downstream.destroy(); };
            upstream.once("aborted", abort);
            upstream.once("error", abort);
            upstream.once("close", () => { if (!upstream.complete) abort(); });
          });
          proxy.on("error", (_error, _request, downstream) => {
            // Preserve a transport failure even before headers. Vite's synthetic HTTP 500
            // otherwise makes EventSource CLOSED and stops its native reconnect loop.
            if (!downstream.destroyed) downstream.destroy();
          });
        },
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  };
});

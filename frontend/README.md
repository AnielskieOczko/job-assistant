# job-assistant frontend

A Vite + React + TypeScript SPA for the job-assistant backend.

```bash
npm ci && npm run dev     # http://127.0.0.1:5173, proxies /api to the backend on :8080
npm run build             # emits into ../src/main/resources/static
npm run lint
```

The backend must be running (`./mvnw spring-boot:run` from the repo root) — the dev server proxies
to it rather than mocking it.

**See [`../docs/frontend.md`](../docs/frontend.md)** for the route map, why there is no CORS
configuration or `VITE_API_BASE`, the analysis polling contract, the response codes the UI has to
handle, and how the SPA is served from Spring in production.

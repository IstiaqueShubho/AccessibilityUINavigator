# Local LLM Reasoning Server

This small Python server exposes endpoints to invoke an LLM reasoning model and to register an Android callback URL so the server can call your Android app back.

Quick start

1. Install dependencies globally and run:

```powershell
python -m pip install -r requirements.txt
python main.py
```

2. (Optional) Set `OPENAI_API_KEY` in your environment to use OpenAI. If not set, the server uses a local mock adapter.

The server listens on `0.0.0.0:8000` by default.

Endpoints

- `GET /health` — basic health check.
- `POST /register_callback` — register your Android callback URL.
  - JSON: `{ "callback_url": "http://<android_ip>:<port>/callback", "name": "default" }`
- `POST /invoke` — synchronous model call. JSON: `{ "task": "Your task text" }` returns `{ "result": ... }`.
- `POST /invoke_async` — starts work in background then posts the response to the registered callback URL (if present). JSON: `{ "task": "...", "callback_name": "default", "request_id": "optional-id" }`

Android integration notes

- Ensure the Android device runs a reachable HTTP endpoint (or expose via ngrok) and provide that URL via `/register_callback`.
- From Android, call `POST http://<server_ip>:8000/invoke` or `invoke_async`.

Security & production

This is a minimal development example. For production, add authentication, rate limiting, input validation, and a persistent callback registry.

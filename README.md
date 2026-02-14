# API Usage & Quota Service

A small Ktor (Kotlin JVM) backend that tracks and enforces per-API-key usage quotas using **in-memory storage** and a **fixed time-window** model. Correct under concurrent requests; single instance, no database or external cache.

## Features

- **POST /v1/usage/consume** — consume units for the given API key (header `X-API-Key`, body `{ "units": <long> }`).
- **Responses:** 200 (accepted), 429 (quota exceeded), 400 (bad request), 403 (unknown/invalid API key).
- **Quota headers** on 200 and 429: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (epoch seconds).
- Optional JSON body with `limit`, `remaining`, `resetAtEpochSeconds`.

## Design choices

- **Fixed-window quota** — Each key has a limit and a duration (e.g. 100 units per hour). Usage is `used` since `windowStart`; when `now >= windowStart + window`, the window resets (`windowStart = now`, `used = 0`). Simple to reason about and implement.
- **Per-key locking** — `InMemoryUsageRepository` uses a `ConcurrentHashMap<String, KeyStateHolder>`. Each holder has a `ReentrantLock`. All load–reset–check–consume for a key happens under that key’s lock, so there is no global lock and no cross-key contention.
- **In-memory storage** — No DB or Redis; state lives in the JVM. Fits the “single instance, no external store” requirement and keeps the service self-contained.

## Tradeoffs

- **Boundary burst** — At the window boundary, a client can use up to `limit` just before reset and another `limit` just after, so short-term usage can reach up to 2× limit. Acceptable for many use cases; for stricter shaping you’d consider sliding window or token bucket.
- **Single instance** — State is local. Multiple instances would each have their own counters, so effective quota would be (limit × instances). Scaling horizontally would require a shared store (e.g. Redis) and possibly central or per-key coordination.

## What you’d do for scale

- **Algorithms** — **Sliding window** or **token bucket** to smooth bursts and avoid the 2× boundary effect. Sliding window can be implemented with a sorted set of timestamps or a small log; token bucket with a (last refill time, tokens) state and refill math.
- **Key management** — Move from a hardcoded map to a proper **key store** (DB or secrets manager), with key creation/rotation/revocation and optional per-key quota overrides.

## Building & running

| Task              | Description        |
|-------------------|--------------------|
| `./gradlew test`  | Run tests          |
| `./gradlew build` | Build project      |
| `./gradlew run`   | Run server (port 8080) |

Example:

```bash
curl -X POST http://localhost:8080/v1/usage/consume \
  -H "X-API-Key: client-a" \
  -H "Content-Type: application/json" \
  -d '{"units": 10}'
```

Configured keys (in `QuotaConfiguration.kt`): `client-a` (100/hour), `client-b` (10/minute).

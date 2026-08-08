## Summary

CampusHub shows solid Phase-1–9 engineering: JWT access/refresh with Redis versioning and blacklists, outbox-backed ES sync, RBAC via shared Redis authority snapshots, object-storage binding with ownership checks, and a careful frontend refresh-token single-flight. The highest-risk gaps are **spoofable client IP headers** (rate limits + Jimeng localhost gate), **GET `/api/resource/**` permitAll combined with `@PreAuthorize` yielding 403 instead of 401** (breaks token refresh), and a few **resource lifecycle consistency** issues (favorites pagination, fileUrl-only bind, resubmit skipping moderation). Auth core (token rotation, identity-header stripping at the gateway, internal service-key checks) is otherwise well designed.

## Issues

### Issue 1 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/config/SecurityConfig.java:51
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/ResourceController.java:109-126
- File: /workspace/shiqian-frontend/src/api/client.ts:104-117
- Description: HTTP security marks **all** `GET /api/resource/**` as `permitAll()`, while protected reads (`/mine`, `/favorites`, `/recycle-bin`, version history, index consistency) rely on `@PreAuthorize`. With an expired/missing JWT the filter never authenticates; method security then throws `AccessDeniedException` → **HTTP 403**, not 401. The frontend only retries refresh on **401**, so stale sessions fail hard on personal/admin GETs without attempting token rotation.
- Suggestion: Narrow permitAll to truly public GETs (e.g. list, detail, search) and require `authenticated()` for `/mine`, `/favorites`, `/recycle-bin`, `/index/**`, `/{id}/versions/**`, `/{id}/favorite`. Alternatively treat unauthenticated PreAuthorize failures as 401, and/or have the frontend refresh on 403 when a refresh token exists.
- Status: fixed

### Issue 2 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/JimengIngestController.java:106-132
- File: /workspace/shiqian-gateway/src/main/java/com/shiqian/gateway/filter/JwtGlobalAuthFilter.java:106-108
- Description: Jimeng ingest is gateway-whitelisted (`/api/jimeng`) and authorizes via token + “localhost only”. When traffic arrives through the gateway, `getRemoteAddr()` is the proxy, so the code accepts **`X-Forwarded-For` first hop as 127.0.0.1/::1**. Clients can spoof that header. Anyone who obtains `X-Jimeng-Sync-Token` can bulk-write gallery content from the public gateway path.
- Suggestion: Do not trust `X-Forwarded-For` unless the immediate peer is a configured trusted proxy; prefer binding Jimeng only on loopback (no gateway route), or require mTLS / network policy, and compare remote address before any forwarded header.
- Status: fixed

### Issue 3 -- Severity: bug
- File: /workspace/shiqian-common/src/main/java/com/shiqian/common/ratelimit/DistributedRateLimitAspect.java:86-95
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/ResourceController.java:426-431
- File: /workspace/shiqian-user/src/main/java/com/shiqian/user/controller/UserController.java:68-87
- Description: Rate limiting (login/register/refresh, download/view/search) and anonymous view dedup identity both take `X-Forwarded-For` first hop without a trusted-proxy allowlist. Attackers can rotate forged IPs to **bypass login/register throttles**, inflate view counts (new IP → new dedup key), and evade IP-keyed limits.
- Suggestion: Resolve client IP only from a trusted proxy chain (Spring `ForwardedHeaderFilter` + known proxy CIDRs), or ignore `X-Forwarded-For` when not behind a verified edge. Prefer user-id keys when authenticated.
- Status: fixed

### Issue 4 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/FavoriteServiceImpl.java:78-115
- Description: `pageFavorites` pages `t_favorite` first, sets `total` from favorite rows, then drops non-published / deleted resources in memory. Result: **page size and `total` lie** (empty-looking pages while `total > 0`, broken “load more” / UI counts after takedown or soft-delete).
- Suggestion: Join/filter at SQL (`status=1 AND deleted=0`) so `Page.total` matches returned rows; optionally clean favorites when a resource goes offline/deleted.
- Status: fixed

### Issue 5 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/ResourceServiceImpl.java:121-126
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/ResourceServiceImpl.java:280-283
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/StoredObjectServiceImpl.java:289-300
- Description: `bindResourceFiles` runs only when `attachments != null`. Create/update that supplies **only legacy `fileUrl`** (still allowed on DTOs) never binds objects. Managed files stay `TEMPORARY`: public `canAccess` denies non-owners for non-BOUND objects, and the cleanup worker can delete them after `temporary-ttl` while the resource still references the URL.
- Suggestion: Always derive publicIds from both `attachments` and primary `fileUrl` and call `bindResourceFiles` on create/update; reject unmanaged external URLs if only object-storage paths are supported.
- Status: fixed

### Issue 6 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/ResourceServiceImpl.java:436-464
- Description: `resubmitResource` only flips status to pending and clears review fields. It does **not** re-run `contentReviewService.inspectOrReject`. After sensitive-word dictionary updates, previously blocked or borderline content can re-enter the audit queue without auto-moderation.
- Suggestion: Call the same `validateContent` / `inspectOrReject` path used by create/update before setting pending; optionally require a content version bump.
- Status: fixed

### Issue 7 -- Severity: bug
- File: /workspace/shiqian-frontend/src/api/client.ts:104-116
- File: /workspace/shiqian-frontend/src/api/client.ts:186-195
- File: /workspace/shiqian-frontend/src/stores/app.ts:292-295
- Description: On refresh failure, `request()` calls `clearTokens()` but never updates Pinia `logged` / `currentUser`. The UI can keep showing a logged-in shell until a later guard runs. `uploadRequest` does not clear tokens at all when `refreshAccessToken()` throws, leaving a half-dead session.
- Suggestion: Export a shared `onAuthFailure()` that clears tokens and notifies the store (event bus / store action); use it from both `request` and `uploadRequest`. Router already loads `/me` on protected routes—align API layer with that.
- Status: fixed

### Issue 8 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/ResourceController.java:182-188
- Description: `canViewResource` does `resource.getUserId().equals(userId)` without null-safe checks. A corrupt/legacy row with `user_id = null` causes **NPE** on detail/download for non-published resources (500 instead of 404).
- Suggestion: Use `Objects.equals(resource.getUserId(), userId)` and treat null owner as non-viewable unless auditor.
- Status: fixed

### Issue 9 -- Severity: bug
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/ResourceController.java:308-341
- Description: Favorite list requires `@PreAuthorize("hasAuthority('resource:favorite')")`, but add/remove/isFavorited only check `getCurrentUserId() != null`. Any authenticated principal without `resource:favorite` can still mutate favorites (or get 401-only checks while bypassing the permission model).
- Suggestion: Add `@PreAuthorize("hasAuthority('resource:favorite')")` on POST/DELETE/GET `/{id}/favorite` for consistency with list and RBAC.
- Status: fixed

### Issue 10 -- Severity: suggestion
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/controller/ResourceFileController.java:225-247
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/config/SecurityConfig.java:51
- Description: Legacy `GET /api/resource/files/**` serves any file under `resource.upload-dir` with path normalization against `..`, but **no auth and no “bound to published resource” check**. Knowing/leaking a relative path (or guessing user-scoped legacy layouts) allows unauthenticated download of unpublished or private legacy uploads. Newer `object/{publicId}` paths correctly enforce `canAccess`.
- Suggestion: Gate legacy file access through the same publication/ownership rules as managed objects, or migrate and disable the catch-all once legacy data is re-bound.
- Status: fixed

### Issue 11 -- Severity: suggestion
- File: /workspace/shiqian-user/src/main/java/com/shiqian/user/config/SecurityConfig.java:33-45
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/config/SecurityConfig.java:58
- File: /workspace/shiqian-gateway/src/main/resources/application.yml:45-49
- Description: Actuator exposes `health,info,prometheus` without authentication on gateway and services. Acceptable on localhost-bound docker ports, but risky if ports are ever published or the gateway is internet-facing—metrics and build info aid reconnaissance.
- Suggestion: Restrict actuator to management port / internal network, or require auth / IP allowlist in non-local profiles.
- Status: fixed

### Issue 12 -- Severity: suggestion
- File: /workspace/shiqian-user/src/main/java/com/shiqian/user/config/SecurityConfig.java:46-53
- File: /workspace/shiqian-user/src/main/java/com/shiqian/user/security/InternalServiceKeyValidator.java:24-31
- Description: Internal `/internal/users/**` endpoints are `permitAll` and rely solely on `X-CampusHub-Internal-Key`. Validation is constant-time and empty key fails closed—good—but user service still must not be reachable from untrusted networks. Compose binds DB/Redis to localhost; if the user service port is exposed, key brute force or leak is the only barrier to authority snapshots and profile bulk reads.
- Suggestion: Bind user service to localhost or private network only; optionally require mutual TLS or Spring Security matching for internal paths in production profiles.
- Status: fixed

### Issue 13 -- Severity: suggestion
- File: /workspace/shiqian-resource/src/main/java/com/shiqian/resource/service/impl/FavoriteServiceImpl.java:36-52
- Description: Add-favorite is check-then-insert. Unique index `uk_user_resource` prevents duplicates, but concurrent double-clicks surface as generic `DataIntegrityViolationException` → “数据已存在或存在关联约束” rather than a clear idempotent success or “已收藏”.
- Suggestion: Catch duplicate-key and treat as success (idempotent favorite), or use `INSERT IGNORE` / upsert.
- Status: fixed

### Issue 14 -- Severity: suggestion
- File: /workspace/shiqian-gateway/src/main/java/com/shiqian/gateway/filter/JwtGlobalAuthFilter.java:95-105
- Description: Gateway treats **all** `GET /api/resource/**` (plus category/tag GETs and download/view POSTs) as public and **skips JWT validation entirely** on those paths—even when a Bearer token is present. Identity still works at the resource service (Authorization is forwarded), but the gateway never enforces blacklist/version for optional-auth traffic, and the public surface is broader than needed (e.g. admin-only GET paths still reach the service unauthenticated).
- Suggestion: Mirror a stricter public path list (list/detail/search only); validate JWT when present even on public routes so optional-auth endpoints get current principal at the edge consistently.
- Status: fixed

### Issue 15 -- Severity: nit
- File: /workspace/shiqian-common/src/main/java/com/shiqian/common/security/JwtUtil.java:79-98
- Description: Expired, malformed, and signature-invalid tokens all return `null` from `parseToken`, so APIs cannot distinguish “expired—please refresh” from “tampered”. Combined with Issue 1, clients get opaque failures.
- Suggestion: Propagate typed exceptions (or a small result enum) so filters can return 401 with a stable machine-readable reason (`token_expired` vs `token_invalid`).
- Status: fixed

## Strengths

- **Session security is thoughtful**: refresh tokens stored as SHA-256 fingerprints with atomic compare-and-delete rotation; access blacklist TTL matches token expiry; monotonic `tokenVersion` in Redis is shared by gateway, user, and resource services.
- **Gateway strips client-forged `X-User-Id` / `X-Username` / `X-User-Role`** before re-injecting claims from verified JWT (`JwtGlobalAuthFilter`).
- **Resource workflow** covers publish → audit → needs-changes/reject → resubmit, owner edit of published content re-enters pending with review fields cleared, soft-delete/restore/permanent delete with outbox events, and favorites restricted to published resources on write.
- **Transactional outbox** (`Propagation.MANDATORY`) keeps MySQL writes and ES/notification messages consistent; publisher uses confirms + claim/retry/dead-letter style handling.
- **Object storage** binds ownership, rejects cross-user attachment bind, and uses after-commit storage deletes; counters use Redis aggregation with idempotent batch apply.
- **Frontend** single-flights refresh, maps reaudit on published edit, forces `/me` for admin route role checks (not trusting only localStorage role), and pairs logout/password-change with server-side token invalidation.


### Issue 16 -- Severity: bug
- File: /workspace/shiqian-gateway/src/main/java/com/shiqian/gateway/filter/JwtGlobalAuthFilter.java
- Description: 公开 GET（资源详情/搜索等）在携带 **已过期** access token 时原先按匿名放行；前端 `localStorage` 仍认为已登录，不会触发 refresh，可选鉴权接口以匿名视角返回数据。版本失效/黑名单 token 同样被静默降级为匿名。
- Suggestion: 公开路径上 EXPIRED → 401（`token_expired`）以便 SPA refresh；解析成功但 `isCurrent=false` 同样 401；仅 INVALID（篡改/垃圾）继续匿名放行。
- Status: fixed

### Issue 17 -- Severity: suggestion
- File: /workspace/shiqian-gateway/src/main/resources/application.yml
- Description: 网关仍将 `/api/jimeng/**` 路由到 resource。即梦接口虽有本机直连校验，但公网网关路由扩大了攻击面与误配风险。
- Suggestion: 从 gateway 路由中移除 jimeng，仅允许直连 resource 本机端口。
- Status: fixed

## Fix log

- 2026-08-09: Issues 1–15 addressed in commits `7718f29`, `d565b58`, and follow-up (actuator, favorites cleanup, anonymous 401).
- 2026-08-09: Issues 16–17 — public-path expired JWT → 401 + drop jimeng gateway route; expand ClientIp/rate-limit/Jimeng/gateway/frontend auth tests.
- code-review-graph v2.3.7 indexed; MCP healthy (30 tools).

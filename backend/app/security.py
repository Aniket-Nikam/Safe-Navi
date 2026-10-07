from __future__ import annotations

from collections import defaultdict, deque
import hashlib
import os
from threading import Lock
import time

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Small-process limiter; deployments can replace it with a shared Redis gateway."""
    def __init__(self, app):
        super().__init__(app); self.events: dict[str, deque[float]] = defaultdict(deque); self.lock = Lock()

    async def dispatch(self, request: Request, call_next):
        path = request.url.path; method = request.method.upper()
        if os.getenv("SAFE_NAVI_RATE_LIMIT_DISABLED") == "1": return await call_next(request)
        limit = 300
        if path in {"/api/v1/auth/login", "/api/v1/auth/register"}: limit = 30
        elif path == "/api/v1/assistant/chat": limit = 40
        elif path == "/api/v1/evidence": limit = 30
        elif method in {"POST", "PATCH", "DELETE"}: limit = 120
        ip = request.client.host if request.client else "unknown"
        authorization = request.headers.get("authorization", "")
        identity = hashlib.sha256(authorization.encode()).hexdigest()[:16] if authorization else ip
        key = f"{identity}:{path}:{method}"; now = time.monotonic(); cutoff = now - 60
        with self.lock:
            bucket = self.events[key]
            while bucket and bucket[0] < cutoff: bucket.popleft()
            if len(bucket) >= limit:
                return JSONResponse(status_code=429, content={"detail": "Too many requests. Try again shortly."}, headers={"Retry-After": "60"})
            bucket.append(now)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Cache-Control"] = "no-store" if path.startswith("/api/v1") else response.headers.get("Cache-Control", "no-cache")
        return response

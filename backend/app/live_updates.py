from __future__ import annotations

import asyncio
from fastapi import WebSocket


class LiveUpdateHub:
    """Small in-process fan-out for non-sensitive map invalidation events."""

    def __init__(self) -> None:
        self._connections: set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections.add(websocket)

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections.discard(websocket)

    async def broadcast(self, event: str) -> None:
        async with self._lock:
            recipients = tuple(self._connections)
        stale: list[WebSocket] = []
        for websocket in recipients:
            try:
                await websocket.send_json({"event": event})
            except Exception:
                stale.append(websocket)
        if stale:
            async with self._lock:
                for websocket in stale:
                    self._connections.discard(websocket)


live_updates = LiveUpdateHub()

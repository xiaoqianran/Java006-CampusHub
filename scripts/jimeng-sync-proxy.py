#!/usr/bin/env python3
"""本机 3001 端口代理：兼容油猴默认 sync URL，转发到 CampusHub 网关。"""

from __future__ import annotations

import http.server
import os
import socketserver
import sys
import urllib.error
import urllib.parse
import urllib.request

UPSTREAM = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
LISTEN_HOST = "127.0.0.1"
LISTEN_PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 3001
SYNC_TOKEN = os.environ.get("JIMENG_INGEST_TOKEN", "").strip()
ALLOWED_ORIGIN = os.environ.get(
    "JIMENG_SYNC_ALLOWED_ORIGIN",
    "https://jimeng.jianying.com",
).strip()
ALLOWED_PATHS = {
    "/api/jimeng/prompts/batch",
    "/api/jimeng/prompts/stream",
    "/api/jimeng/prompts/existing",
}


class ProxyHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_OPTIONS(self):  # noqa: N802
        if not self._request_allowed():
            self.send_error(403)
            return
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", self._response_origin())
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_POST(self):  # noqa: N802
        self._proxy("POST")

    def do_GET(self):  # noqa: N802
        self.send_error(405)

    def _proxy(self, method: str) -> None:
        if not self._request_allowed():
            self.send_error(403)
            return
        length = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(length) if length > 0 else b""
        target = UPSTREAM.rstrip("/") + self.path
        request = urllib.request.Request(target, data=body, method=method)
        content_type = self.headers.get("Content-Type")
        if content_type:
            request.add_header("Content-Type", content_type)
        request.add_header("X-Jimeng-Sync-Token", SYNC_TOKEN)
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                payload = response.read()
                self.send_response(response.status)
                for key, value in response.headers.items():
                    lower = key.lower()
                    if lower in {"transfer-encoding", "connection", "content-encoding"}:
                        continue
                    self.send_header(key, value)
                self.send_header("Access-Control-Allow-Origin", self._response_origin())
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except urllib.error.HTTPError as error:
            payload = error.read()
            self.send_response(error.code)
            self.send_header("Content-Type", error.headers.get("Content-Type", "application/json"))
            self.send_header("Access-Control-Allow-Origin", self._response_origin())
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except Exception as error:  # noqa: BLE001
            payload = ("{\"ok\":false,\"error\":%r}" % str(error)).encode("utf-8")
            self.send_response(502)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", self._response_origin())
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    def _request_allowed(self) -> bool:
        path = urllib.parse.urlsplit(self.path).path
        if path not in ALLOWED_PATHS:
            return False
        origin = (self.headers.get("Origin") or "").strip()
        return not origin or origin == ALLOWED_ORIGIN

    def _response_origin(self) -> str:
        origin = (self.headers.get("Origin") or "").strip()
        return origin if origin == ALLOWED_ORIGIN else ALLOWED_ORIGIN

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("[jimeng-proxy] " + (fmt % args) + "\n")


class ReusableTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


def main() -> int:
    if len(SYNC_TOKEN) < 32:
        print("JIMENG_INGEST_TOKEN 必须至少 32 位，拒绝启动代理", file=sys.stderr)
        return 2
    if not ALLOWED_ORIGIN.startswith("https://"):
        print("JIMENG_SYNC_ALLOWED_ORIGIN 必须是 HTTPS 地址", file=sys.stderr)
        return 2
    with ReusableTCPServer((LISTEN_HOST, LISTEN_PORT), ProxyHandler) as server:
        print(f"Jimeng sync proxy listening on http://{LISTEN_HOST}:{LISTEN_PORT} -> {UPSTREAM}", flush=True)
        server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

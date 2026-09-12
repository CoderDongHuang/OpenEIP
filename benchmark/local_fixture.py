#!/usr/bin/env python3
"""Deterministic local HTTP fixture for the v0.9 benchmark procedure."""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class FixtureHandler(BaseHTTPRequestHandler):
    response_body = b'{"status":"ok","service":"openeip-performance-fixture"}'
    delay_seconds = 0.0

    def do_GET(self) -> None:
        if self.path.split("?", 1)[0] != "/health":
            self.send_error(404)
            return
        if self.delay_seconds:
            time.sleep(self.delay_seconds)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(self.response_body)))
        self.end_headers()
        self.wfile.write(self.response_body)

    def log_message(self, *_args: object) -> None:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the deterministic OpenEIP benchmark HTTP fixture")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--delay-ms", type=float, default=0.0)
    args = parser.parse_args()
    if not 0 <= args.delay_ms <= 1_000:
        parser.error("--delay-ms must be between 0 and 1000")
    FixtureHandler.delay_seconds = args.delay_ms / 1000
    server = ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    print(json.dumps({"host": args.host, "port": args.port, "path": "/health"}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Bounded, reproducible HTTP baseline benchmark for OpenEIP services.

The tool deliberately has no third-party dependencies. It measures request
latency and transport outcomes only; response bodies are never written to the
result file.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import math
import platform
import socket
import ssl
import sys
import time
from collections import Counter
from collections.abc import Callable, Sequence
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import UTC, datetime
from http.client import HTTPConnection, HTTPSConnection
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import ParseResult, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

DEFAULT_TIMEOUT_SECONDS = 10.0
DEFAULT_MAX_RESPONSE_BYTES = 1_048_576
MAX_REQUESTS = 100_000
MAX_CONCURRENCY = 256
MAX_WARMUPS = 1_000
MAX_RESPONSE_BYTES = 16 * 1024 * 1024


class _NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, *_: Any) -> None:
        return None


_NO_REDIRECT_OPENER = build_opener(_NoRedirectHandler)


def _urlopen_no_redirect(request: Request, timeout: float) -> Any:
    return _NO_REDIRECT_OPENER.open(request, timeout=timeout)


class _PinnedHTTPConnection(HTTPConnection):
    def __init__(self, host: str, port: int, resolved_ip: str, timeout: float) -> None:
        super().__init__(host, port, timeout=timeout)
        self._resolved_ip = resolved_ip

    def connect(self) -> None:
        self.sock = socket.create_connection((self._resolved_ip, self.port), self.timeout)


class _PinnedHTTPSConnection(HTTPSConnection):
    def __init__(self, host: str, port: int, resolved_ip: str, timeout: float) -> None:
        self._ssl_context = ssl.create_default_context()
        super().__init__(host, port, timeout=timeout, context=self._ssl_context)
        self._resolved_ip = resolved_ip

    def connect(self) -> None:
        raw_socket = socket.create_connection((self._resolved_ip, self.port), self.timeout)
        self.sock = self._ssl_context.wrap_socket(raw_socket, server_hostname=self.host)


def _pinned_opener(target_url: str, resolved_ip: str) -> Callable[..., Any]:
    parsed = urlparse(target_url)
    hostname = parsed.hostname
    if hostname is None:
        raise ValueError("target URL must include a hostname")
    port = parsed.port or (443 if parsed.scheme == "https" else 80)

    def opener(request: Request, timeout: float) -> Any:
        connection_type = _PinnedHTTPSConnection if parsed.scheme == "https" else _PinnedHTTPConnection
        connection = connection_type(hostname, port, resolved_ip, timeout)
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        try:
            connection.request(request.get_method(), path, headers=dict(request.header_items()))
            return connection.getresponse()
        except Exception:
            connection.close()
            raise

    return opener


def _read_limited(response: Any, max_response_bytes: int) -> bytes:
    """Read at most ``max_response_bytes + 1`` bytes for overflow detection."""

    limit = max_response_bytes + 1
    body = bytearray()
    while len(body) < limit:
        chunk = response.read(min(65_536, limit - len(body)))
        if not chunk:
            break
        if not isinstance(chunk, (bytes, bytearray, memoryview)):
            raise TypeError("response.read() must return bytes")
        body.extend(chunk[: limit - len(body)])
    return bytes(body)


@dataclass(frozen=True)
class RequestResult:
    latency_ms: float
    outcome: str
    status_code: int | None


def validate_target_url(
    target_url: str,
    allow_private_network: bool = False,
    resolved_addresses: Sequence[str] | None = None,
) -> str:
    """Validate a target URL and reject accidental private-network traffic."""

    parsed = urlparse(target_url)
    _validate_url_syntax(parsed)
    hostname = parsed.hostname
    if hostname is None:
        raise ValueError("target URL must include a hostname")
    addresses = (
        tuple(resolved_addresses) if resolved_addresses is not None else _resolve_target_addresses(hostname)
    )
    if not addresses:
        raise ValueError(f"target hostname cannot be resolved: {hostname}")
    if not allow_private_network and any(_is_private_address(address) for address in addresses):
        raise ValueError("target resolves to a private or loopback address; pass --allow-private-network")
    return target_url


def _validate_url_syntax(parsed: ParseResult) -> None:
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("target URL must use http or https and include a hostname")
    if parsed.username or parsed.password:
        raise ValueError("target URL must not contain credentials")
    if parsed.fragment:
        raise ValueError("target URL must not contain a fragment")
    try:
        port = parsed.port
    except ValueError as exc:
        if "out of range" in str(exc):
            raise ValueError("target URL port must be between 1 and 65535") from exc
        raise ValueError("target URL contains an invalid port") from exc
    if port is not None and not 1 <= port <= 65_535:
        raise ValueError("target URL port must be between 1 and 65535")


def _resolve_target_addresses(hostname: str) -> tuple[str, ...]:
    try:
        addresses = tuple(
            dict.fromkeys(str(entry[4][0]) for entry in socket.getaddrinfo(hostname, None, type=socket.SOCK_STREAM))
        )
    except OSError as exc:
        raise ValueError(f"target hostname cannot be resolved: {hostname}") from exc
    if not addresses:
        raise ValueError(f"target hostname cannot be resolved: {hostname}")
    return addresses


def _resolves_to_private_address(hostname: str) -> bool:
    return any(_is_private_address(address) for address in _resolve_target_addresses(hostname))


def _is_private_address(address: str) -> bool:
    parsed = ipaddress.ip_address(address)
    return parsed.is_private or parsed.is_loopback or parsed.is_link_local or parsed.is_reserved or parsed.is_multicast


def _validate_limits(requests: int, concurrency: int, timeout: float, max_response_bytes: int) -> None:
    if not 1 <= requests <= MAX_REQUESTS:
        raise ValueError(f"requests must be between 1 and {MAX_REQUESTS}")
    if not 1 <= concurrency <= MAX_CONCURRENCY:
        raise ValueError(f"concurrency must be between 1 and {MAX_CONCURRENCY}")
    if not 0.001 <= timeout <= 300.0:
        raise ValueError("timeout must be between 0.001 and 300 seconds")
    if not 1 <= max_response_bytes <= MAX_RESPONSE_BYTES:
        raise ValueError(f"max response bytes must be between 1 and {MAX_RESPONSE_BYTES}")


def _validate_p99_threshold(max_p99_ms: float | None) -> None:
    if max_p99_ms is not None and not 0 < max_p99_ms <= 3_600_000:
        raise ValueError("max P99 milliseconds must be between 0 and 3600000")


def _one_request(
    target_url: str,
    timeout: float,
    max_response_bytes: int,
    opener: Callable[..., Any] = _urlopen_no_redirect,
) -> RequestResult:
    started = time.perf_counter()
    request = Request(target_url, method="GET", headers={"User-Agent": "OpenEIP-benchmark/0.9"})
    outcome = "success"
    status_code: int | None = None
    try:
        with opener(request, timeout=timeout) as response:
            status_code = getattr(response, "status", None)
            if status_code is None:
                status_code = response.getcode()
            if not isinstance(status_code, int):
                raise TypeError("response status must be an integer")
            body = _read_limited(response, max_response_bytes)
            if status_code < 200 or status_code >= 300:
                outcome = "http_error"
            else:
                declared_length: int | None = None
                try:
                    declared = getattr(response, "headers", {}).get("Content-Length")
                    declared_length = int(declared) if declared is not None else None
                except (TypeError, ValueError):
                    declared_length = None
                if len(body) > max_response_bytes or (
                    declared_length is not None and declared_length > max_response_bytes
                ):
                    outcome = "response_too_large"
    except HTTPError as exc:
        status_code = exc.code
        outcome = "http_error"
    except TimeoutError:
        outcome = "timeout"
    except URLError as exc:
        outcome = "timeout" if isinstance(exc.reason, TimeoutError) else "connection_error"
    except (OSError, TypeError, ValueError):
        outcome = "connection_error"
    except Exception:
        # The opener is a transport boundary; adapter failures become failed
        # samples instead of aborting the entire benchmark.
        outcome = "connection_error"
    latency_ms = (time.perf_counter() - started) * 1000
    return RequestResult(round(latency_ms, 3), outcome, status_code)


def percentile(values: Sequence[float], ratio: float) -> float:
    """Return the nearest-rank percentile with an explicit empty-set guard."""

    if not values:
        raise ValueError("cannot calculate a percentile without samples")
    if not 0 < ratio <= 1:
        raise ValueError("percentile ratio must be in (0, 1]")
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * ratio) - 1))
    return ordered[index]


def summarize(
    results: Sequence[RequestResult], elapsed_seconds: float, max_p99_ms: float | None = None
) -> dict[str, Any]:
    latencies = [result.latency_ms for result in results]
    statuses = Counter(str(result.status_code) for result in results if result.status_code is not None)
    outcomes = Counter(result.outcome for result in results)
    summary = {
        "schemaVersion": "1.0",
        "sampleCount": len(results),
        "elapsedSeconds": round(elapsed_seconds, 6),
        "throughputRequestsPerSecond": round(len(results) / elapsed_seconds, 3) if elapsed_seconds > 0 else 0.0,
        "latencyMs": {
            "p50": round(percentile(latencies, 0.50), 3),
            "p95": round(percentile(latencies, 0.95), 3),
            "p99": round(percentile(latencies, 0.99), 3),
            "min": round(min(latencies), 3),
            "max": round(max(latencies), 3),
        },
        "outcomes": dict(sorted(outcomes.items())),
        "statusCodes": dict(sorted(statuses.items())),
        "errorCount": sum(count for name, count in outcomes.items() if name != "success"),
        "result": (
            "PASS"
            if all(result.outcome == "success" for result in results)
            and (max_p99_ms is None or percentile(latencies, 0.99) <= max_p99_ms)
            else "FAIL"
        ),
    }
    if max_p99_ms is not None:
        summary["thresholds"] = {"maxP99Ms": max_p99_ms}
    return summary


def run_benchmark(
    target_url: str,
    requests: int = 100,
    concurrency: int = 10,
    timeout: float = DEFAULT_TIMEOUT_SECONDS,
    max_response_bytes: int = DEFAULT_MAX_RESPONSE_BYTES,
    warmups: int = 5,
    allow_private_network: bool = False,
    commit: str | None = None,
    max_p99_ms: float | None = None,
    opener: Callable[..., Any] = _urlopen_no_redirect,
) -> dict[str, Any]:
    """Run bounded GET requests and return a machine-readable summary."""

    _validate_limits(requests, concurrency, timeout, max_response_bytes)
    _validate_p99_threshold(max_p99_ms)
    if not 0 <= warmups <= MAX_WARMUPS:
        raise ValueError(f"warmups must be between 0 and {MAX_WARMUPS}")
    parsed_target = urlparse(target_url)
    _validate_url_syntax(parsed_target)
    hostname = parsed_target.hostname
    if hostname is None:
        raise ValueError("target URL must include a hostname")
    resolved_addresses = _resolve_target_addresses(hostname)
    validated_url = validate_target_url(target_url, allow_private_network, resolved_addresses)
    request_opener = opener
    if opener is _urlopen_no_redirect:
        request_opener = _pinned_opener(validated_url, resolved_addresses[0])
    for _ in range(warmups):
        _one_request(validated_url, timeout, max_response_bytes, request_opener)

    started = time.perf_counter()
    results: list[RequestResult] = []
    with ThreadPoolExecutor(max_workers=min(concurrency, requests), thread_name_prefix="openeip-bench") as pool:
        futures = [
            pool.submit(_one_request, validated_url, timeout, max_response_bytes, request_opener)
            for _ in range(requests)
        ]
        for future in as_completed(futures):
            results.append(future.result())
    elapsed = time.perf_counter() - started
    summary = summarize(results, elapsed, max_p99_ms)
    parsed = urlparse(validated_url)
    summary.update(
        {
            "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "environment": {"python": platform.python_version(), "platform": platform.platform()},
            "target": {"scheme": parsed.scheme, "host": parsed.hostname},
            "requests": requests,
            "concurrency": concurrency,
            "timeoutSeconds": timeout,
            "maxResponseBytes": max_response_bytes,
            "warmups": warmups,
        }
    )
    if commit:
        summary["commit"] = commit
    return summary


def _parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a bounded OpenEIP HTTP baseline benchmark")
    parser.add_argument("--target-url", required=True, help="Explicit HTTP(S) endpoint to measure")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--max-response-bytes", type=int, default=DEFAULT_MAX_RESPONSE_BYTES)
    parser.add_argument("--warmups", type=int, default=5)
    parser.add_argument("--allow-private-network", action="store_true")
    parser.add_argument("--commit", help="Commit SHA associated with the evidence")
    parser.add_argument("--max-p99-ms", type=float, help="Fail when measured P99 latency exceeds this value")
    parser.add_argument("--output", type=Path, help="Write JSON evidence to this path (stdout otherwise)")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        result = run_benchmark(
            args.target_url,
            args.requests,
            args.concurrency,
            args.timeout,
            args.max_response_bytes,
            args.warmups,
            args.allow_private_network,
            args.commit,
            args.max_p99_ms,
        )
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    rendered = json.dumps(result, ensure_ascii=True, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0 if result["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())

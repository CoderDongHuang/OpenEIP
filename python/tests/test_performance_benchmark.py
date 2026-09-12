import importlib.util
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError

import pytest

MODULE_PATH = Path(__file__).parents[2] / "benchmark" / "run_benchmark.py"
SPEC = importlib.util.spec_from_file_location("openeip_benchmark", MODULE_PATH)
assert SPEC and SPEC.loader
benchmark = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = benchmark
SPEC.loader.exec_module(benchmark)

FIXTURE_PATH = MODULE_PATH.with_name("local_fixture.py")
FIXTURE_SPEC = importlib.util.spec_from_file_location("openeip_benchmark_fixture", FIXTURE_PATH)
assert FIXTURE_SPEC and FIXTURE_SPEC.loader
fixture = importlib.util.module_from_spec(FIXTURE_SPEC)
FIXTURE_SPEC.loader.exec_module(fixture)


class FakeResponse:
    def __init__(self, status: int = 200, body: bytes = b"ok", content_length: str | None = None) -> None:
        self.status = status
        self._body = body
        self._offset = 0
        self.headers = {"Content-Length": content_length} if content_length else {}

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def getcode(self) -> int:
        return self.status

    def read(self, amount: int = -1) -> bytes:
        if amount < 0:
            amount = len(self._body) - self._offset
        chunk = self._body[self._offset : self._offset + amount]
        self._offset += len(chunk)
        return chunk


def fake_opener(response: FakeResponse):
    def opener(*_: object, **__: object) -> FakeResponse:
        return response

    return opener


def test_target_validation_requires_explicit_safe_http_url(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolve_target_addresses", lambda _: ("93.184.216.34",))
    assert benchmark.validate_target_url("https://example.com/health") == "https://example.com/health"
    with pytest.raises(ValueError):
        benchmark.validate_target_url("ftp://example.com")
    with pytest.raises(ValueError):
        benchmark.validate_target_url("https://user:pass@example.com")
    with pytest.raises(ValueError, match="invalid port"):
        benchmark.validate_target_url("https://example.com:not-a-port")
    with pytest.raises(ValueError, match="between 1 and 65535"):
        benchmark.validate_target_url("https://example.com:65536")


def test_private_target_requires_opt_in(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolve_target_addresses", lambda _: ("127.0.0.1",))
    with pytest.raises(ValueError, match="private"):
        benchmark.validate_target_url("http://localhost:8000/health")
    assert benchmark.validate_target_url("http://localhost:8000/health", allow_private_network=True)


def test_empty_dns_result_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark.socket, "getaddrinfo", lambda *_args, **_kwargs: [])
    with pytest.raises(ValueError, match="cannot be resolved"):
        benchmark.validate_target_url("https://example.com")
    with pytest.raises(ValueError, match="cannot be resolved"):
        benchmark.validate_target_url("https://example.com", resolved_addresses=())


def test_private_address_classifier_uses_resolved_addresses(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolve_target_addresses", lambda _: ("127.0.0.1",))
    assert benchmark._resolves_to_private_address("localhost") is True


@pytest.mark.parametrize(
    "kwargs",
    [
        {"requests": 0},
        {"requests": 100001},
        {"concurrency": 0},
        {"concurrency": 257},
        {"timeout": 0.0001},
        {"max_response_bytes": 0},
    ],
)
def test_limits_are_bounded(kwargs: dict[str, object], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolve_target_addresses", lambda _: ("93.184.216.34",))
    with pytest.raises(ValueError):
        benchmark.run_benchmark("https://example.com", allow_private_network=False, **kwargs)


def test_run_benchmark_reports_latency_status_and_json_contract(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolve_target_addresses", lambda _: ("93.184.216.34",))
    result = benchmark.run_benchmark(
        "https://example.com/health",
        requests=4,
        concurrency=2,
        warmups=1,
        opener=fake_opener(FakeResponse()),
    )
    assert result["result"] == "PASS"
    assert result["sampleCount"] == 4
    assert result["errorCount"] == 0
    assert result["statusCodes"] == {"200": 4}
    json.dumps(result)


def test_result_schema_is_versioned_and_covers_summary_contract() -> None:
    schema_path = MODULE_PATH.parents[1] / "contracts" / "performance" / "performance-benchmark-result.v1.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    assert schema["$id"].endswith("performance-benchmark-result.v1.schema.json")
    assert set(schema["required"]) >= {
        "schemaVersion",
        "sampleCount",
        "latencyMs",
        "outcomes",
        "statusCodes",
        "result",
    }


def test_loopback_fixture_exercises_pinned_http_transport() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 0), fixture.FixtureHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = benchmark.run_benchmark(
            f"http://127.0.0.1:{server.server_port}/health",
            requests=3,
            concurrency=2,
            warmups=1,
            timeout=2,
            allow_private_network=True,
        )
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    assert result["result"] == "PASS"
    assert result["statusCodes"] == {"200": 3}


def test_local_fixture_rejects_unknown_paths() -> None:
    server = ThreadingHTTPServer(("127.0.0.1", 0), fixture.FixtureHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        result = benchmark.run_benchmark(
            f"http://127.0.0.1:{server.server_port}/missing",
            requests=1,
            concurrency=1,
            warmups=0,
            allow_private_network=True,
        )
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)

    assert result["result"] == "FAIL"
    assert result["statusCodes"] == {"404": 1}


def test_local_fixture_main_validates_delay(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "argv", ["local_fixture.py", "--delay-ms", "1001"])
    with pytest.raises(SystemExit, match="2"):
        fixture.main()


def test_local_fixture_main_closes_server(monkeypatch: pytest.MonkeyPatch) -> None:
    class FakeServer:
        closed = False

        def __init__(self, address: tuple[str, int], handler: type[BaseHTTPRequestHandler]) -> None:
            assert address == ("127.0.0.1", 8123)
            assert handler is fixture.FixtureHandler

        def serve_forever(self) -> None:
            raise KeyboardInterrupt

        def server_close(self) -> None:
            self.closed = True

    server = FakeServer(("127.0.0.1", 8123), fixture.FixtureHandler)
    monkeypatch.setattr(fixture, "ThreadingHTTPServer", lambda *_args: server)
    monkeypatch.setattr(sys, "argv", ["local_fixture.py", "--port", "8123", "--delay-ms", "5"])
    assert fixture.main() == 0
    assert server.closed is True
    assert fixture.FixtureHandler.delay_seconds == 0.005


@pytest.mark.parametrize(
    "response, expected",
    [
        (FakeResponse(503), "http_error"),
        (FakeResponse(200, content_length="100"), "response_too_large"),
    ],
)
def test_non_success_responses_are_classified(response: FakeResponse, expected: str) -> None:
    result = benchmark._one_request("https://example.com", 1, 10, fake_opener(response))
    assert result.outcome == expected


def test_transport_errors_are_classified() -> None:
    def timeout_opener(*_: object, **__: object) -> None:
        raise TimeoutError()

    def connection_opener(*_: object, **__: object) -> None:
        raise URLError("offline")

    def wrapped_timeout_opener(*_: object, **__: object) -> None:
        raise URLError(TimeoutError())

    assert benchmark._one_request("https://example.com", 1, 10, timeout_opener).outcome == "timeout"
    assert benchmark._one_request("https://example.com", 1, 10, connection_opener).outcome == "connection_error"
    assert benchmark._one_request("https://example.com", 1, 10, wrapped_timeout_opener).outcome == "timeout"


def test_http_error_keeps_status_code() -> None:
    error = HTTPError("https://example.com", 429, "rate limited", {}, None)

    def opener(*_: object, **__: object) -> None:
        raise error

    result = benchmark._one_request("https://example.com", 1, 10, opener)
    assert result.status_code == 429
    assert result.outcome == "http_error"


def test_redirect_handler_does_not_follow_redirects() -> None:
    assert benchmark._NoRedirectHandler().redirect_request(None) is None


def test_read_limited_consumes_short_chunks_until_limit_or_eof() -> None:
    response = FakeResponse(body=b"0123456789")
    assert benchmark._read_limited(response, 10) == b"0123456789"
    oversized = FakeResponse(body=b"01234567890")
    assert benchmark._read_limited(oversized, 10) == b"01234567890"

    class OverreadingResponse:
        def read(self, _amount: int) -> bytes:
            return b"x" * 100

    assert len(benchmark._read_limited(OverreadingResponse(), 10)) == 11


def test_percentiles_use_nearest_rank_for_all_samples() -> None:
    results = [benchmark.RequestResult(float(value), "success", 200) for value in (1, 2, 3, 4, 5)]
    summary = benchmark.summarize(results, 1.0)
    assert summary["latencyMs"] == {"p50": 3.0, "p95": 5.0, "p99": 5.0, "min": 1.0, "max": 5.0}


def test_p99_threshold_is_a_real_pass_fail_gate() -> None:
    results = [benchmark.RequestResult(float(value), "success", 200) for value in (1, 2, 3, 4, 5)]
    assert benchmark.summarize(results, 1.0, max_p99_ms=5)["result"] == "PASS"
    assert benchmark.summarize(results, 1.0, max_p99_ms=4.9)["result"] == "FAIL"


def test_cli_parser_and_main_preserve_result_exit_status(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    payload = {"result": "PASS", "sampleCount": 1}
    monkeypatch.setattr(benchmark, "run_benchmark", lambda *_args, **_kwargs: payload)
    assert benchmark.main(["--target-url", "https://example.com"]) == 0
    assert json.loads(capsys.readouterr().out) == payload

    monkeypatch.setattr(benchmark, "run_benchmark", lambda *_args, **_kwargs: {"result": "FAIL"})
    assert benchmark.main(["--target-url", "https://example.com"]) == 1

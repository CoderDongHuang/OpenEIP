import importlib.util
import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError

import pytest

MODULE_PATH = Path(__file__).parents[2] / "benchmark" / "run_benchmark.py"
SPEC = importlib.util.spec_from_file_location("openeip_benchmark", MODULE_PATH)
assert SPEC and SPEC.loader
benchmark = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = benchmark
SPEC.loader.exec_module(benchmark)


class FakeResponse:
    def __init__(self, status: int = 200, body: bytes = b"ok", content_length: str | None = None) -> None:
        self.status = status
        self._body = body
        self.headers = {"Content-Length": content_length} if content_length else {}

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def getcode(self) -> int:
        return self.status

    def read(self, amount: int = -1) -> bytes:
        return self._body[:amount] if amount >= 0 else self._body


def fake_opener(response: FakeResponse):
    def opener(*_: object, **__: object) -> FakeResponse:
        return response

    return opener


def test_target_validation_requires_explicit_safe_http_url(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolves_to_private_address", lambda _: False)
    assert benchmark.validate_target_url("https://example.com/health") == "https://example.com/health"
    with pytest.raises(ValueError):
        benchmark.validate_target_url("ftp://example.com")
    with pytest.raises(ValueError):
        benchmark.validate_target_url("https://user:pass@example.com")


def test_private_target_requires_opt_in(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolves_to_private_address", lambda _: True)
    with pytest.raises(ValueError, match="private"):
        benchmark.validate_target_url("http://localhost:8000/health")
    assert benchmark.validate_target_url("http://localhost:8000/health", allow_private_network=True)


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
    monkeypatch.setattr(benchmark, "_resolves_to_private_address", lambda _: False)
    with pytest.raises(ValueError):
        benchmark.run_benchmark("https://example.com", allow_private_network=False, **kwargs)


def test_run_benchmark_reports_latency_status_and_json_contract(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(benchmark, "_resolves_to_private_address", lambda _: False)
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

    assert benchmark._one_request("https://example.com", 1, 10, timeout_opener).outcome == "timeout"
    assert benchmark._one_request("https://example.com", 1, 10, connection_opener).outcome == "connection_error"


def test_http_error_keeps_status_code() -> None:
    error = HTTPError("https://example.com", 429, "rate limited", {}, None)

    def opener(*_: object, **__: object) -> None:
        raise error

    result = benchmark._one_request("https://example.com", 1, 10, opener)
    assert result.status_code == 429
    assert result.outcome == "http_error"


def test_redirect_handler_does_not_follow_redirects() -> None:
    assert benchmark._NoRedirectHandler().redirect_request(None) is None


def test_percentiles_use_nearest_rank_for_all_samples() -> None:
    results = [benchmark.RequestResult(float(value), "success", 200) for value in (1, 2, 3, 4, 5)]
    summary = benchmark.summarize(results, 1.0)
    assert summary["latencyMs"] == {"p50": 3.0, "p95": 5.0, "p99": 5.0, "min": 1.0, "max": 5.0}


def test_p99_threshold_is_a_real_pass_fail_gate() -> None:
    results = [benchmark.RequestResult(float(value), "success", 200) for value in (1, 2, 3, 4, 5)]
    assert benchmark.summarize(results, 1.0, max_p99_ms=5)["result"] == "PASS"
    assert benchmark.summarize(results, 1.0, max_p99_ms=4.9)["result"] == "FAIL"

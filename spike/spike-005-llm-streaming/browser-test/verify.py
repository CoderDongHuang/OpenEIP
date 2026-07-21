"""Browser-level verification for the complete Spike-005 path."""

import json
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import httpx
from playwright.sync_api import Page, sync_playwright


def wait_for_gateway(page: Page) -> None:
    """Wait for Nginx and its upstreams to become reachable."""
    for attempt in range(60):
        try:
            page.goto("http://gateway", wait_until="domcontentloaded", timeout=2_000)
            return
        except Exception:
            if attempt == 59:
                raise
            time.sleep(1)


def submit(page: Page, message: str) -> None:
    """Submit one prompt through the rendered frontend."""
    page.locator("#prompt").fill(message)
    page.locator("#send").click()


def run() -> None:
    """Verify success, upstream error, client cancellation, and reconnect."""
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        page = browser.new_page(viewport={"width": 1280, "height": 800})
        wait_for_gateway(page)

        submit(page, "normal")
        page.locator("#status").filter(has_text="complete").wait_for(timeout=10_000)
        output = page.locator("#output").text_content()
        browser_metrics: dict[str, Any] = page.evaluate("window.__spikeMetrics")
        first_token_ms = browser_metrics["firstToken"] - browser_metrics["started"]
        total_ms = browser_metrics["completed"] - browser_metrics["started"]

        submit(page, "upstream-error")
        page.locator("#status").filter(has_text="error").wait_for(timeout=10_000)

        submit(page, "long-stream")
        page.wait_for_function("window.__spikeMetrics.firstToken !== undefined", timeout=10_000)
        page.locator("#cancel").click()
        page.locator("#status").filter(has_text="cancelled").wait_for(timeout=5_000)

        submit(page, "normal")
        page.locator("#status").filter(has_text="complete").wait_for(timeout=10_000)
        reconnect_output = page.locator("#output").text_content()
        browser.close()

    time.sleep(1)
    backend_metrics = httpx.get("http://backend:8000/metrics", timeout=5).json()
    passed = (
        output == "OpenEIP streaming works."
        and reconnect_output == "OpenEIP streaming works."
        and first_token_ms < 500
        and total_ms < 3_000
        and backend_metrics["upstream_errors"] >= 1
        and backend_metrics["client_cancellations"] >= 1
        and backend_metrics["completed"] >= 2
    )
    evidence = {
        "spike": "spike-005",
        "executed_at": datetime.now(UTC).isoformat(),
        "path": "Chromium -> Nginx Gateway -> FastAPI -> OpenAI-compatible upstream -> SSE -> Chromium",
        "first_token_ms": first_token_ms,
        "first_token_threshold_ms": 500,
        "total_ms": total_ms,
        "total_threshold_ms": 3_000,
        "normal_output": output,
        "upstream_error_visible": backend_metrics["upstream_errors"] >= 1,
        "client_cancellation_observed": backend_metrics["client_cancellations"] >= 1,
        "reconnect_output": reconnect_output,
        "backend_metrics": backend_metrics,
        "passed": passed,
    }
    results = Path("/results")
    results.mkdir(parents=True, exist_ok=True)
    (results / "result.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    if not passed:
        raise RuntimeError("Spike-005 acceptance criteria failed")


if __name__ == "__main__":
    run()

#!/usr/bin/env python3
"""Capture deterministic desktop and mobile Governance release evidence."""

from __future__ import annotations

import argparse
import re
import uuid
from pathlib import Path

from playwright.sync_api import Page, sync_playwright


def verify(page: Page, base_url: str, width: int, height: int, output: Path) -> None:
    unique = uuid.uuid4().hex[:12]
    username = f"browser_{unique}"
    password = "Release-Browser-2026!"

    print(f"INFO: starting Governance {width}x{height}", flush=True)
    page.set_default_timeout(15_000)
    page.set_viewport_size({"width": width, "height": height})
    page.goto(base_url, wait_until="domcontentloaded")
    page.get_by_role("tab", name="Create account").click()
    page.locator('input[autocomplete="username"]:visible').fill(username)
    page.locator('input[autocomplete="email"]:visible').fill(f"{username}@example.test")
    page.locator('input[autocomplete="new-password"]:visible').fill(password)
    page.get_by_role("button", name="Create account", exact=True).click()
    page.wait_for_url(re.compile(r"/overview$"))

    if width <= 900:
        page.get_by_role("button", name="Open navigation").click()
        page.get_by_text("Governance", exact=True).last.click()
    else:
        page.get_by_text("Governance", exact=True).first.click()
    page.wait_for_url(re.compile(r"/governance$"))
    page.get_by_role("heading", name="Governance control plane").wait_for()
    page.get_by_text("OpenEIP Default / governance-v1", exact=True).wait_for()
    page.get_by_text("Server-derived scope", exact=True).wait_for()

    metrics = page.evaluate(
        """() => ({
          innerWidth: window.innerWidth,
          innerHeight: window.innerHeight,
          scrollWidth: document.documentElement.scrollWidth,
          scrollHeight: document.documentElement.scrollHeight
        })"""
    )
    if metrics["innerWidth"] != width or metrics["innerHeight"] != height:
        raise AssertionError(f"Unexpected viewport: {metrics}")
    if metrics["scrollWidth"] > width:
        raise AssertionError(f"Horizontal overflow at {width}x{height}: {metrics}")

    output.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(output), full_page=True)
    print(f"PASS: Governance {width}x{height} -> {output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:3000")
    parser.add_argument("--output", type=Path, default=Path("artifacts/browser-qa/v0.7"))
    args = parser.parse_args()

    with sync_playwright() as playwright:
        print("INFO: launching system Chrome", flush=True)
        browser = playwright.chromium.launch(
            executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            headless=True,
            timeout=15_000,
        )
        try:
            for width, height, name in (
                (1440, 900, "governance-desktop.png"),
                (390, 844, "governance-mobile.png"),
            ):
                context = browser.new_context(viewport={"width": width, "height": height})
                try:
                    verify(context.new_page(), args.base_url, width, height, args.output / name)
                finally:
                    context.close()
        finally:
            browser.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

import pytest

from engine_core.main import health_check


@pytest.mark.asyncio
async def test_health_check() -> None:
    response = await health_check()

    assert response == {
        "status": "healthy",
        "version": "0.2.0-alpha",
        "service": "openeip-ai-engine",
    }

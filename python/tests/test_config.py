from engine_core.config import Settings


def test_settings_support_environment_overrides(monkeypatch) -> None:
    monkeypatch.setenv("OPENEIP_PORT", "8100")

    settings = Settings()

    assert settings.port == 8100
    assert settings.host == "0.0.0.0"

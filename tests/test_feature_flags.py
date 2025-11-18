from crypto_trader.feature_flags import FeatureFlags


def test_feature_flags_from_env(monkeypatch):
    monkeypatch.setenv("CRYPTO_FLAG_EXPERIMENTAL", "true")
    flags = FeatureFlags.from_env()
    assert flags.is_enabled("experimental") is True
    assert flags.is_enabled("missing", default=False) is False

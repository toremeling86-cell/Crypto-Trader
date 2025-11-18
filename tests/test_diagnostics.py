from crypto_trader.diagnostics import collect_diagnostics


def test_collect_diagnostics_has_core_fields():
    report = collect_diagnostics().to_dict()
    assert "generated_at" in report
    assert "python" in report
    assert "platform" in report
    assert report["python"].get("version")

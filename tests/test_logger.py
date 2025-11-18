from crypto_trader.observability.logger import ObservabilityConfig, configure_logger


def test_configure_logger_singleton_behavior():
    config = ObservabilityConfig(service_name="test-service")
    logger1 = configure_logger(config)
    logger2 = configure_logger(config)
    assert logger1 is logger2
    assert logger1.name == "test-service"

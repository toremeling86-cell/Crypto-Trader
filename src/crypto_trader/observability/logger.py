"""Structured logging and tracing utilities for the Crypto Trader service."""
from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, Optional

DEFAULT_LOG_LEVEL = os.getenv("CRYPTO_TRADER_LOG_LEVEL", "INFO")


@dataclass
class ObservabilityConfig:
    """Configuration for the observability logger."""

    service_name: str = "crypto-trader"
    environment: str = os.getenv("CRYPTO_TRADER_ENV", "development")
    log_level: str = DEFAULT_LOG_LEVEL
    include_timestamp: bool = True
    extra_fields: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """Return the configuration as a dictionary suitable for serialization."""
        return {
            "service": self.service_name,
            "environment": self.environment,
            "log_level": self.log_level,
            **self.extra_fields,
        }


def _default_formatter(config: ObservabilityConfig) -> logging.Formatter:
    """Create a formatter that renders structured JSON logs."""

    class JsonFormatter(logging.Formatter):
        def format(self, record: logging.LogRecord) -> str:  # type: ignore[override]
            payload: Dict[str, Any] = {
                "level": record.levelname,
                "message": record.getMessage(),
                **config.to_dict(),
            }
            if config.include_timestamp:
                payload["timestamp"] = datetime.utcnow().isoformat() + "Z"
            if record.exc_info:
                payload["exception"] = self.formatException(record.exc_info)
            if record.stack_info:
                payload["stack"] = self.formatStack(record.stack_info)
            return json.dumps(payload)

    return JsonFormatter()


def configure_logger(config: Optional[ObservabilityConfig] = None) -> logging.Logger:
    """Configure the root logger for structured output.

    The returned logger is configured only once. Repeated calls simply return
    the already configured logger. This makes the function safe to call from
    application entrypoints and tests without duplicating handlers.
    """

    logger = logging.getLogger(config.service_name if config else "crypto-trader")
    if logger.handlers:
        return logger

    resolved_config = config or ObservabilityConfig()
    level = getattr(logging, resolved_config.log_level.upper(), logging.INFO)
    logger.setLevel(level)

    handler = logging.StreamHandler()
    handler.setFormatter(_default_formatter(resolved_config))
    logger.addHandler(handler)
    logger.propagate = False
    logger.debug("Logger configured", extra={"config": resolved_config.to_dict()})
    return logger


def get_logger(name: Optional[str] = None, config: Optional[ObservabilityConfig] = None) -> logging.Logger:
    """Return a child logger with shared configuration."""

    root_logger = configure_logger(config)
    return root_logger.getChild(name) if name else root_logger

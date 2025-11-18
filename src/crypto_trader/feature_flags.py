"""Feature flag management for runtime configuration."""
from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Dict


@dataclass
class FeatureFlags:
    """Simple environment-backed feature flags."""

    flags: Dict[str, bool]

    @classmethod
    def from_env(cls) -> "FeatureFlags":
        """Create flags from environment variables prefixed with CRYPTO_FLAG_."""
        parsed: Dict[str, bool] = {}
        prefix = "CRYPTO_FLAG_"
        for key, value in os.environ.items():
            if key.startswith(prefix):
                flag_name = key[len(prefix) :].lower()
                parsed[flag_name] = value.lower() in {"1", "true", "yes", "on"}
        return cls(flags=parsed)

    def is_enabled(self, name: str, default: bool = False) -> bool:
        """Return whether a flag is enabled."""
        return self.flags.get(name.lower(), default)


__all__ = ["FeatureFlags"]

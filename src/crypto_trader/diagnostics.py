"""Runtime diagnostics utilities for health and debugging."""
from __future__ import annotations

import platform
import sys
from dataclasses import dataclass
from datetime import datetime
from typing import Dict


def _python_runtime() -> Dict[str, str]:
    return {
        "version": sys.version.split(" ")[0],
        "implementation": platform.python_implementation(),
    }


def _platform_info() -> Dict[str, str]:
    return {
        "system": platform.system(),
        "release": platform.release(),
        "machine": platform.machine(),
    }


@dataclass
class DiagnosticsReport:
    generated_at: str
    python: Dict[str, str]
    platform: Dict[str, str]

    def to_dict(self) -> Dict[str, Dict[str, str]]:
        return {
            "generated_at": self.generated_at,
            "python": self.python,
            "platform": self.platform,
        }


def collect_diagnostics() -> DiagnosticsReport:
    """Collect metadata that is useful for debugging deployments."""
    return DiagnosticsReport(
        generated_at=datetime.utcnow().isoformat() + "Z",
        python=_python_runtime(),
        platform=_platform_info(),
    )


__all__ = ["collect_diagnostics", "DiagnosticsReport"]

"""Parquet import integration for market data snapshots."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Optional, TYPE_CHECKING

if TYPE_CHECKING:  # pragma: no cover
    import pandas as pd


def _require_pandas():
    """Import pandas only when needed to avoid hard dependency at import time."""

    try:
        import pandas as pd
    except ImportError as exc:  # pragma: no cover - defensive guard
        raise ImportError(
            "pandas is required for ParquetDataSet operations. Install the optional "
            "dependency with `pip install crypto-trader[dev]` or `pip install pandas`."
        ) from exc

    return pd


@dataclass
class ParquetDataSet:
    """Represents a Parquet dataset and provides convenience helpers."""

    path: Path
    columns: Optional[List[str]] = None

    def load(self) -> "pd.DataFrame":
        """Load the dataset into a pandas DataFrame."""
        pd = _require_pandas()
        return pd.read_parquet(self.path, columns=self.columns)

    def iter_batches(self, batch_size: int) -> Iterable["pd.DataFrame"]:
        """Yield dataframes in batches for memory-efficient processing."""
        pd = _require_pandas()
        dataset = pd.read_parquet(self.path, columns=self.columns)
        total_rows = len(dataset)
        for start in range(0, total_rows, batch_size):
            yield dataset.iloc[start : start + batch_size]


__all__ = ["ParquetDataSet"]

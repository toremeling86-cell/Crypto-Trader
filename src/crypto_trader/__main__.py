"""CLI entrypoint showcasing core utilities."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from .data.parquet_loader import ParquetDataSet
from .diagnostics import collect_diagnostics
from .feature_flags import FeatureFlags
from .observability.logger import configure_logger


def main() -> None:
    parser = argparse.ArgumentParser(description="Crypto Trader utility CLI")
    parser.add_argument("--parquet", type=Path, help="Path to Parquet file to inspect", required=False)
    args = parser.parse_args()

    logger = configure_logger()
    flags = FeatureFlags.from_env()
    diag = collect_diagnostics().to_dict()
    logger.info("Diagnostics", extra={"diagnostics": diag, "feature_flags": flags.flags})

    if args.parquet:
        dataset = ParquetDataSet(path=args.parquet)
        try:
            frame = dataset.load()
        except FileNotFoundError:
            logger.error("Parquet file not found", extra={"path": str(args.parquet)})
            return
        logger.info("Loaded parquet file", extra={"path": str(args.parquet), "rows": len(frame)})
        print(json.dumps(frame.head().to_dict(orient="records"), indent=2))


if __name__ == "__main__":
    main()

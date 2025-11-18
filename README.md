# Crypto-Trader

A reference scaffolding for a crypto trading service with observability, data ingestion, feature flagging, diagnostics, and order tracking foundations.

## Features
- **Observability logger**: Structured JSON logging configurable via environment variables.
- **Parquet ingestion**: Convenience helpers for loading and batching Parquet market data.
- **Feature flags**: Environment-driven runtime toggles for experiments.
- **Diagnostics**: Utilities to capture runtime environment metadata.
- **Order tracking**: In-memory order registry suitable for early testing.

## Getting started
1. Install dependencies:
   ```bash
   pip install .[dev]
   ```
2. Run tests:
   ```bash
   pytest
   ```
3. Execute the CLI (optionally inspecting a Parquet file):
   ```bash
   python -m crypto_trader --parquet /path/to/data.parquet
   ```

## Development
- Python 3.10+
- CI runs lint-free unit tests via GitHub Actions.

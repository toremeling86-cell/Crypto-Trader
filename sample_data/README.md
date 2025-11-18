# Sample Data for Backtesting

## Overview

This directory contains minimal historical OHLC data for smoke testing the backtest pipeline.

## File Format

### `XXBTZUSD_1h_sample.csv`

**Format:** CSV with header
**Columns:**
1. `timestamp` - Unix timestamp in milliseconds
2. `open` - Opening price (USD)
3. `high` - Highest price (USD)
4. `low` - Lowest price (USD)
5. `close` - Closing price (USD)
6. `volume` - Trading volume (BTC)
7. `trades` - Number of trades (optional)

**Example:**
```csv
timestamp,open,high,low,close,volume,trades
1700000000000,42500.0,42650.0,42400.0,42550.0,12.5,245
1700003600000,42550.0,42700.0,42500.0,42680.0,15.2,312
```

## Data Coverage

- **Asset:** XXBTZUSD (Bitcoin/USD on Kraken)
- **Timeframe:** 1h (hourly bars)
- **Period:** 30 days (720 bars)
- **Date Range:** November 2024
- **Data Quality:** Tier 3 Standard (no gaps, validated OHLC)

## Parser

Use `ParquetFileReader` or `DataFileParser` from:
```
app/src/main/java/com/cryptotrader/data/dataimport/
```

**Kotlin usage:**
```kotlin
val parser = DataFileParser()
val bars = parser.parseCsvFile(
    file = File("sample_data/XXBTZUSD_1h_sample.csv"),
    asset = "XXBTZUSD",
    timeframe = "1h"
)
```

## Expected Backtest Results

Running RSI Diagnostics strategy on this data should produce:
- **Trades:** 10-15
- **Win Rate:** 50-60%
- **Sharpe Ratio:** 0.8-1.5
- **Max Drawdown:** 1-3%

## Generating More Sample Data

To generate sample data from Cloudflare R2:

```bash
# Download one quarter
adb shell run-as com.cryptotrader \
  'cat /data/data/com.cryptotrader/files/backtests/XXBTZUSD_1h_2024-Q4.csv' \
  | head -720 > sample_data/XXBTZUSD_1h_sample.csv
```

Or use the Python data upload script:
```python
# See scripts/upload_to_r2.py
python scripts/upload_to_r2.py --download --asset XXBTZUSD --timeframe 1h --limit 720
```

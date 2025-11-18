# Build & Run Instructions

## Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34
- Gradle 8.2+

## Environment Setup

1. Copy example config:
```bash
cp .env.example .env
cp gradle.properties.example gradle.properties
```

2. Fill in required API keys in `.env`:
- `CLAUDE_API_KEY` - Anthropic Claude API key
- `KRAKEN_API_KEY` - Kraken exchange API key (for live trading)
- `KRAKEN_PRIVATE_KEY` - Kraken exchange private key (for live trading)
- `R2_ACCOUNT_ID` - Cloudflare R2 account ID (for cloud data)
- `R2_ACCESS_KEY_ID` - Cloudflare R2 access key
- `R2_SECRET_ACCESS_KEY` - Cloudflare R2 secret key

## Build Commands

### Clean Build
```bash
./gradlew clean
./gradlew :app:assembleDebug
```

### Run Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```

### Build & Test (Full Pipeline)
```bash
./gradlew clean :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

### Install on Device
```bash
./gradlew :app:installDebug
```

## Smoke Backtest

### Expected Minimal Log Output:
```
I/BacktestEngine: ========================================
I/BacktestEngine: BACKTEST STARTED
I/BacktestEngine: Strategy: RSI Smoke Test
I/BacktestEngine: Timeframe: 1h
I/BacktestEngine: Data points: 720 (30 days)
I/BacktestEngine: ========================================
I/BacktestEngine: BACKTEST COMPLETE
I/BacktestEngine: Total Trades: 12
I/BacktestEngine: Win Rate: 58.3%
I/BacktestEngine: Total P&L: +4.2%
I/BacktestEngine: Sharpe Ratio: 1.34
I/BacktestEngine: Max Drawdown: -2.1%
I/BacktestEngine: Total Fees: $8.45
```

### Run Smoke Test
1. Launch app on device/emulator
2. Navigate to: AI Screen → Strategy Test Center
3. Select "RSI Diagnostics" strategy
4. Tap "Run Backtest"
5. Verify log output matches pattern above (±10% variance acceptable)

## Database Migrations

Current version: **v16**

Migration chain: v1 → v2 → v3 → v4 → v5 → v6 → v7 → v8 → v9 → v10 → v11 → v12 → v13 → v14 → v15 → v16

See `MIGRATIONS.md` for detailed migration history.

## Artifact Outputs

### Backtest Results Location:
```
/data/data/com.cryptotrader/files/backtests/
  └── run_<timestamp>/
      ├── result.json          # Full backtest result
      ├── events.ndjson        # Execution events (P1-7)
      ├── equity_curve.csv     # Balance over time
      └── trades.csv           # Individual trade records
```

### NDJSON Event Stream Format (P1-7):
```json
{"type":"backtest_start","timestamp":1700000000,"run_id":"bt_123","strategy":"RSI"}
{"type":"trade","timestamp":1700001000,"action":"BUY","price":42500.0,"size":0.1}
{"type":"trade","timestamp":1700002000,"action":"SELL","price":43000.0,"pnl":50.0}
{"type":"backtest_end","timestamp":1700010000,"total_trades":12,"sharpe":1.34}
```

## Verification Checklist

- [ ] Build succeeds without errors
- [ ] All unit tests pass (100% success rate)
- [ ] Smoke backtest completes with >0 trades
- [ ] Database migrates successfully to v16
- [ ] Backtest artifacts written to correct location
- [ ] NDJSON events validate against schema
- [ ] No hardcoded credentials in logs

## Troubleshooting

### Build Fails
```bash
# Clear Gradle cache
./gradlew clean --no-daemon
rm -rf .gradle/
```

### Database Migration Issues
```bash
# Clear app data and reinstall
adb shell pm clear com.cryptotrader
./gradlew :app:installDebug
```

### Missing API Keys
- Ensure `.env` file exists in project root
- Verify all required keys are set
- Restart Android Studio after changing `.env`

## CI/CD Integration

See `.github/workflows/build-test.yml` for automated build & test pipeline.

Smoke test will fail if:
- 0 trades executed
- Sharpe ratio < 0
- Backtest crashes or times out

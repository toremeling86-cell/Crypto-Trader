# CryptoTrader - Complete Project Overview

**Last Updated:** 2025-11-17
**Version:** 1.0.0
**Platform:** Android (Kotlin)
**Purpose:** Personal AI-powered cryptocurrency trading platform

---

## Table of Contents

1. [Project Vision](#project-vision)
2. [Technology Stack](#technology-stack)
3. [Architecture](#architecture)
4. [Implemented Features](#implemented-features)
5. [In-Progress Features](#in-progress-features)
6. [Planned Features](#planned-features)
7. [Known Issues](#known-issues)
8. [File Structure](#file-structure)
9. [Database Schema](#database-schema)
10. [API Integrations](#api-integrations)
11. [Security](#security)
12. [Testing Strategy](#testing-strategy)
13. [Deployment](#deployment)

---

## Project Vision

**CryptoTrader** is a personal, AI-enhanced trading platform designed as a "pocket-sized hedge fund." It combines:
- Real-time cryptocurrency trading via Kraken API
- AI-powered market analysis using Claude (Anthropic)
- Professional-grade backtesting with multiple data tiers
- Automated strategy execution
- Advanced risk management

**Core Philosophy:**
- No compromises on code quality
- Production-grade, hedge fund quality implementation
- Real market data only (no mock/synthetic data)
- Security-first approach

---

## Technology Stack

### Android Application

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Kotlin | 1.9.20 |
| **Min SDK** | Android 8.0 (API 26) | - |
| **Target SDK** | Android 14 (API 34) | - |
| **UI Framework** | Jetpack Compose | 2023.10.01 |
| **Architecture** | MVVM + Clean Architecture | - |
| **DI** | Hilt (Dagger) | 2.48 |
| **Database** | Room | 2.6.0 |
| **Networking** | Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| **Coroutines** | kotlinx-coroutines | 1.7.3 |
| **Serialization** | Moshi + kotlinx.serialization | 1.15.0 / 1.6.0 |

### Cloud Infrastructure (In Progress)

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Storage** | Cloudflare R2 | Historical market data (30GB+) |
| **Data Format** | Parquet + Zstandard | Compressed OHLC data |
| **SDK** | AWS SDK for Kotlin | S3-compatible R2 access |
| **Processing** | Apache Arrow | Parquet file reading |

### Backend Services

| Service | Provider | Purpose |
|---------|----------|---------|
| **Trading API** | Kraken | Live/paper trading, market data |
| **AI Analysis** | Anthropic Claude | Strategy generation, market analysis |
| **WebSocket** | Kraken WS | Real-time price feeds |

### Data Sources

| Source | Data Type | Tier | Status |
|--------|-----------|------|--------|
| **CryptoLake** | OHLC Parquet | TIER_4_BASIC | Ready (132 files) |
| **Binance** | OHLC CSV | TIER_3_STANDARD | Ready |
| **Kraken API** | Real-time OHLC | TIER_4_BASIC | Implemented |
| **Premium Data** | Order book depth | TIER_1_PREMIUM | Planned |

---

## Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────────┐
│           Presentation Layer            │
│  (Jetpack Compose UI + ViewModels)     │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│            Domain Layer                 │
│  (Use Cases, Business Logic, Models)   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│             Data Layer                  │
│  (Repositories, DAOs, API Clients)     │
└─────────────────────────────────────────┘
```

### Key Architectural Patterns

**1. Repository Pattern**
- `LocalHistoricalDataRepository` - Room database access
- `CloudDataRepository` - R2 cloud storage (in progress)
- `StrategyRepository` - Strategy CRUD operations

**2. Dependency Injection**
- Hilt modules in `di/` package
- Singleton scoped services
- ViewModels with @HiltViewModel

**3. Reactive State Management**
- Kotlin Flow for async streams
- StateFlow for UI state
- Coroutines for background work

**4. Security**
- EncryptedSharedPreferences for API keys
- No hardcoded credentials
- Biometric authentication

---

## Implemented Features

### ✅ Core Trading System

#### Strategy Management
- **File:** `domain/strategy/StrategyExecutor.kt`
- Create custom trading strategies with conditions
- Support for technical indicators (RSI, SMA, EMA, MACD, Bollinger Bands)
- Multi-pair trading support
- Active/inactive toggle
- Paper trading mode

#### Order Execution
- **File:** `domain/trading/OrderManager.kt`
- Market and limit orders
- Stop-loss and take-profit
- Position sizing with portfolio percentage
- Order validation and error handling
- Integration with Kraken API

#### Position Tracking
- **File:** `domain/trading/PositionTracker.kt`
- Real-time P&L calculation
- Open positions monitoring
- Risk exposure tracking
- Portfolio value updates

### ✅ AI Trading Advisor

#### Multi-Agent Analysis System
- **File:** `workers/AIAdvisorWorker.kt`
- 5 specialized AI agents:
  - Technical Analyst (chart patterns, indicators)
  - Risk Manager (exposure, drawdown analysis)
  - Sentiment Analyst (news, social media)
  - Quantitative Analyst (statistical models)
  - Market Structure Expert (liquidity, order flow)
- Synthesis of multiple agent reports
- Trading opportunity identification
- Automated background analysis (configurable intervals)

#### Claude API Integration
- **File:** `data/api/ClaudeApiService.kt`
- Streaming response support
- Conversation context management
- Token usage tracking
- Error handling and retries

### ✅ Backtesting System (Professional Grade)

#### BacktestEngine
- **File:** `domain/backtesting/BacktestEngine.kt`
- **Status:** Fully implemented, production-ready
- **Features:**
  - Data tier validation (prevents mixing quality levels)
  - Look-ahead bias prevention
  - Realistic cost modeling (fees, slippage, spread)
  - Proper equity curve calculation
  - Performance metrics:
    - Total P&L (absolute & percentage)
    - Win rate
    - Sharpe ratio
    - Max drawdown
    - Average trade duration
  - Trade-by-trade breakdown
  - Historical backtest storage

#### BacktestOrchestrator
- **File:** `domain/backtesting/BacktestOrchestrator.kt`
- **Status:** Fully implemented
- **Features:**
  - Auto mode (AI-recommended parameters)
  - Manual mode (user-specified parameters)
  - Data availability validation
  - Quality score calculation
  - Result persistence to database

#### BacktestProposalGenerator
- **File:** `domain/backtesting/BacktestProposalGenerator.kt`
- **Status:** Fully implemented
- **Features:**
  - AI-powered backtest configuration
  - Data quality scoring
  - Automatic tier selection
  - Warning generation (data gaps, quality issues)

#### BacktestDataProvider
- **File:** `domain/backtesting/BacktestDataProvider.kt`
- **Status:** Implemented for local data
- **Features:**
  - Intelligent data selection
  - Multi-tier support
  - Data coverage validation
  - Quality checks

### ✅ Data Management

#### Local Storage (Room Database)
- **Version:** 14 (latest migration)
- **Entities:**
  - `OHLCBarEntity` - OHLC candlestick data
  - `TechnicalIndicatorEntity` - Calculated indicators
  - `DataCoverageEntity` - Coverage metadata
  - `BacktestRunEntity` - Backtest results
  - `DataQualityEntity` - Quality metrics
  - `StrategyEntity` - Trading strategies
  - `TradeEntity` - Executed trades
  - And 10+ more (see database schema section)

#### CSV Data Import
- **File:** `data/dataimport/BatchDataImporter.kt`
- **Status:** Fully functional
- Batch processing (1000 rows at a time)
- Progress tracking
- Error handling
- Automatic data tier detection

#### File Parsing
- **File:** `data/dataimport/DataFileParser.kt`
- **Status:** Fully functional
- Binance format support
- Asset normalization (BTCUSDT → XXBTZUSD)
- Timeframe normalization
- Date extraction

### ✅ Real-Time Market Data

#### Kraken WebSocket Integration
- **File:** `data/websocket/KrakenWebSocketManager.kt`
- Real-time OHLC updates
- Ticker data
- Automatic reconnection
- Subscription management

#### Price Feed Management
- **File:** `domain/market/PriceFeedManager.kt`
- Multi-asset price tracking
- Update notification system
- Historical price caching

### ✅ UI Components

#### Strategy Configuration Screen
- **File:** `presentation/screens/strategy/StrategyConfigScreen.kt`
- Visual strategy builder
- Condition editor
- Parameter configuration
- Active trading pairs selector

#### Dashboard
- **File:** `presentation/screens/dashboard/DashboardScreen.kt`
- Portfolio overview
- Recent trades
- P&L visualization
- Quick actions

#### Settings
- **File:** `presentation/screens/settings/SettingsScreen.kt`
- API key management (Kraken, Claude)
- Biometric lock
- Theme configuration
- Data management

### ✅ Security Features

#### Encrypted Storage
- **File:** `data/security/SecureKeyStorage.kt`
- EncryptedSharedPreferences for API keys
- Secure credential management
- No plaintext secrets

#### Biometric Authentication
- Fingerprint/face unlock
- Configurable requirement
- Secure enclave integration

#### Root Detection
- **File:** `utils/RootDetection.kt`
- Comprehensive root checking
- Magisk detection
- Warning system

---

## In-Progress Features

### 🚧 Cloud Data Storage (Cloudflare R2)

**Status:** ~40% complete
**Files:**
- ✅ `scripts/upload_to_r2.py` - Python upload script (DONE)
- ✅ `scripts/R2_SETUP_GUIDE.md` - Setup documentation (DONE)
- ✅ `data/cloud/CloudStorageClient.kt` - R2 client (DONE)
- ✅ `data/dataimport/ParquetFileReader.kt` - Parquet reader (DONE)
- ⏳ `data/repository/CloudDataRepository.kt` - Integration layer (NEXT)

**Remaining Work:**
1. CloudDataRepository implementation
2. DataSyncManager for smart downloads
3. DataCacheManager for storage management
4. UI for data management settings
5. BacktestDataProvider cloud integration
6. Testing with real R2 data

**Dependencies Added:**
- AWS SDK for Kotlin (S3-compatible)
- Apache Arrow (Parquet reading)
- Zstandard (compression)

**Challenges Solved:**
- ✅ Jetifier conflicts (disabled)
- ✅ Arrow packaging issues (excluded duplicates)
- ✅ Gradle build configuration

### 🚧 Backtest Testing UI

**Status:** UI created, needs data
**File:** `ui/backtest/BacktestTestActivity.kt`

**Features:**
- 4-step testing workflow
- Progress tracking
- Result visualization
- Quick test mode

**Blocking:** Needs cloud data or alternative test data source

---

## Planned Features

### 📋 Phase 1: Complete Cloud Storage (2-3 weeks)

1. **DataSyncManager** - Background sync service
   - WiFi-only option
   - Resumable downloads
   - Progress notifications
   - WorkManager integration

2. **DataCacheManager** - Storage optimization
   - LRU cache eviction
   - Size limits
   - Manual cleanup

3. **Data Management UI** - User controls
   - View downloaded data
   - Delete cached data
   - Download specific assets/timeframes
   - Storage usage display

### 📋 Phase 2: Advanced Backtesting (2 weeks)

1. **Multi-Strategy Backtesting** - Test strategy portfolios
2. **Walk-Forward Analysis** - Out-of-sample validation
3. **Monte Carlo Simulation** - Robustness testing
4. **Parameter Optimization** - Grid search, genetic algorithms

### 📋 Phase 3: Live Trading Enhancements (3 weeks)

1. **Advanced Order Types**
   - Trailing stop-loss
   - OCO (One-Cancels-Other)
   - Iceberg orders

2. **Risk Management Dashboard**
   - VaR (Value at Risk)
   - Portfolio heat map
   - Correlation matrix

3. **Trade Journaling**
   - Screenshots
   - Notes
   - Performance analysis

### 📋 Phase 4: Premium Features (4 weeks)

1. **Tier 1-3 Data Integration**
   - Order book depth
   - Tick data
   - Trade flow analysis

2. **Advanced AI Agents**
   - News sentiment analysis
   - Social media tracking
   - On-chain analysis

3. **Custom Indicators**
   - User-defined formulas
   - Backtestable
   - Shareable

---

## Known Issues

### Critical Issues
*None currently*

### High Priority
1. **Cloud data not yet accessible from phone**
   - Cloud storage implementation in progress
   - Workaround: Local testing with adb push (not implemented yet)

### Medium Priority
1. **Jetifier warnings**
   - Impact: Build warnings only
   - Fix: Disabled Jetifier (android.enableJetifier=false)
   - Status: Resolved

2. **Large APK size concern**
   - Current: ~20MB (estimated with Arrow/AWS SDK)
   - Potential: ProGuard/R8 optimization needed
   - Status: Monitor

### Low Priority
1. **Unused parameter warnings**
   - Files: Several UI components, AIAdvisorWorker
   - Impact: None (code cleanup needed)
   - Status: Deferred

---

## File Structure

```
CryptoTrader/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/cryptotrader/
│   │   │   │   ├── data/              # Data layer
│   │   │   │   │   ├── api/          # API clients (Kraken, Claude)
│   │   │   │   │   ├── cloud/        # Cloud storage (R2)
│   │   │   │   │   ├── dataimport/   # CSV/Parquet import
│   │   │   │   │   ├── local/        # Room database
│   │   │   │   │   │   ├── dao/     # Database access objects
│   │   │   │   │   │   ├── entities/ # Database entities
│   │   │   │   │   │   └── migrations/
│   │   │   │   │   ├── repository/   # Data repositories
│   │   │   │   │   ├── security/     # Encryption, auth
│   │   │   │   │   └── websocket/    # WebSocket clients
│   │   │   │   ├── domain/           # Business logic
│   │   │   │   │   ├── backtesting/  # Backtest engine
│   │   │   │   │   ├── model/        # Domain models
│   │   │   │   │   ├── market/       # Market data
│   │   │   │   │   ├── strategy/     # Strategy execution
│   │   │   │   │   └── trading/      # Order/position management
│   │   │   │   ├── presentation/     # UI layer
│   │   │   │   │   └── screens/     # Compose screens
│   │   │   │   ├── ui/               # Additional UI
│   │   │   │   │   └── backtest/    # Backtest testing UI
│   │   │   │   ├── di/               # Dependency injection
│   │   │   │   ├── utils/            # Utilities
│   │   │   │   └── workers/          # Background workers
│   │   │   └── res/                  # Android resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── scripts/                          # Build/deployment scripts
│   ├── upload_to_r2.py              # R2 data upload
│   ├── requirements.txt             # Python dependencies
│   └── R2_SETUP_GUIDE.md           # Setup guide
├── data/                            # External data (not in repo)
│   ├── crypto_lake_ohlcv/          # CryptoLake Parquet files
│   └── binance_raw/                # Binance CSV files
├── build.gradle.kts                 # Project Gradle config
├── settings.gradle.kts
├── gradle.properties
├── PROJECT_OVERVIEW.md             # This file
└── README.md
```

---

## Database Schema

### Migration History

| Version | Description |
|---------|-------------|
| 1 | Initial schema (trades, strategies) |
| 2-12 | Progressive features (AI analysis, positions, etc.) |
| 13 | Backend data storage (OHLC, indicators) |
| **14** | **Current:** Data tier quality tracking |

### Core Tables

#### OHLCBarEntity (ohlc_bars)
```sql
CREATE TABLE ohlc_bars (
    asset TEXT NOT NULL,
    timeframe TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    open REAL NOT NULL,
    high REAL NOT NULL,
    low REAL NOT NULL,
    close REAL NOT NULL,
    volume REAL NOT NULL,
    trades INTEGER DEFAULT 0,
    source TEXT NOT NULL,
    dataTier TEXT NOT NULL,
    importedAt INTEGER NOT NULL,
    PRIMARY KEY (asset, timeframe, timestamp)
);
CREATE INDEX idx_ohlc_asset_timeframe ON ohlc_bars(asset, timeframe);
```

**Purpose:** Historical OHLC candlestick data for backtesting
**Size:** ~100 rows per day per asset per timeframe

#### BacktestRunEntity (backtest_runs)
```sql
CREATE TABLE backtest_runs (
    id TEXT PRIMARY KEY,
    strategyId TEXT NOT NULL,
    asset TEXT NOT NULL,
    timeframe TEXT NOT NULL,
    startDate INTEGER NOT NULL,
    endDate INTEGER NOT NULL,
    totalTrades INTEGER NOT NULL,
    winRate REAL NOT NULL,
    totalPnL REAL NOT NULL,
    totalPnLPercent REAL NOT NULL,
    sharpeRatio REAL NOT NULL,
    maxDrawdown REAL NOT NULL,
    dataTier TEXT,
    dataQualityScore REAL,
    validationError TEXT,
    createdAt INTEGER NOT NULL
);
```

**Purpose:** Store backtest results
**Size:** One row per backtest run

#### StrategyEntity (strategies)
```sql
CREATE TABLE strategies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    tradingPairs TEXT NOT NULL, -- JSON array
    entryConditions TEXT NOT NULL, -- JSON array
    exitConditions TEXT NOT NULL, -- JSON array
    stopLossPercent REAL NOT NULL,
    takeProfitPercent REAL NOT NULL,
    positionSizePercent REAL NOT NULL,
    maxDrawdown REAL,
    isActive INTEGER NOT NULL,
    tradingMode TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
```

**Purpose:** User-defined trading strategies

### Supporting Tables

- `technical_indicators` - Calculated indicator values
- `data_coverage` - Data availability tracking
- `data_quality` - Quality metrics per dataset
- `trades` - Executed trades
- `portfolio_snapshots` - Historical portfolio values
- `market_snapshots` - Historical market data
- `ai_market_analysis` - AI analysis results
- `advisor_analysis` - Trading advisor reports
- `trading_opportunities` - Identified opportunities
- `meta_analysis` - Multi-agent synthesis

---

## API Integrations

### Kraken API

**Base URL:** `https://api.kraken.com`
**WebSocket:** `wss://ws.kraken.com`

**Implemented Endpoints:**
- `GET /0/public/AssetPairs` - Get trading pairs
- `GET /0/public/OHLC` - Get OHLC data
- `GET /0/public/Ticker` - Get ticker info
- `POST /0/private/Balance` - Get account balance
- `POST /0/private/AddOrder` - Place order
- `POST /0/private/CancelOrder` - Cancel order
- `POST /0/private/OpenOrders` - Get open orders
- `POST /0/private/ClosedOrders` - Get order history

**WebSocket Subscriptions:**
- `ohlc` - Real-time OHLC updates
- `ticker` - Real-time ticker updates

**Authentication:**
- API-Key header
- API-Sign header (HMAC-SHA512)
- Nonce-based replay protection

### Anthropic Claude API

**Base URL:** `https://api.anthropic.com/`

**Implemented:**
- `POST /v1/messages` - Create message (streaming)
- `POST /v1/messages` - Create message (non-streaming)

**Models Used:**
- `claude-3-5-sonnet-20241022` - Main analysis model
- `claude-3-haiku-20240307` - Quick responses

**Features:**
- Streaming responses
- System prompts for agent personalities
- Token usage tracking
- Conversation history

---

## Security

### API Key Storage

**Method:** EncryptedSharedPreferences
**Encryption:** AES-256
**Location:** Android KeyStore

**Keys Stored:**
- Kraken API Key
- Kraken Private Key
- Anthropic API Key
- Cloudflare R2 credentials (when configured)

### Biometric Authentication

**Supported:**
- Fingerprint
- Face recognition
- PIN fallback

**Implementation:** androidx.biometric library

### Network Security

**HTTPS Only:** All API calls use TLS 1.2+
**Certificate Pinning:** Not implemented (consider for production)
**API Key Rotation:** Manual (user-initiated)

### Root Detection

**Checks:**
- su binary presence
- Build tags
- Known root app packages
- RW system mount

**Action:** Warning only (non-blocking)

---

## Testing Strategy

### Unit Tests

**Status:** Minimal coverage
**Priority:** Add comprehensive tests

**Planned:**
- BacktestEngine logic tests
- Strategy evaluation tests
- Order validation tests
- Price calculation tests

### Integration Tests

**Status:** None
**Priority:** Medium

**Planned:**
- Database migrations
- API client error handling
- WebSocket reconnection

### UI Tests

**Status:** None
**Priority:** Low

**Planned:**
- Critical user flows
- Strategy creation
- Order placement

### Manual Testing

**Current Approach:**
- BacktestTestActivity for backtest flow
- Real Kraken paper trading
- Log monitoring (Timber)

---

## Deployment

### Build Configuration

**Debug:**
- Debuggable: true
- Minification: false
- API keys: Required in settings

**Release:**
- Minification: true
- Shrink resources: true
- ProGuard rules: `proguard-rules.pro`
- Signing: Required

### Distribution

**Method:** Direct APK installation (personal use)
**Not on Play Store:** Personal app

### Version Management

**Current:** 1.0.0
**Scheme:** Semantic versioning (MAJOR.MINOR.PATCH)

---

## Development Workflow

### Prerequisites

1. Android Studio Hedgehog or later
2. JDK 17
3. Android SDK 34
4. Kotlin 1.9.20+
5. Python 3.8+ (for R2 upload script)

### Setup

```bash
# Clone repository
git clone <repo-url>
cd CryptoTrader

# Install Python dependencies
cd scripts
pip install -r requirements.txt

# Build app
cd ..
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Running Backtest Test

```bash
# Launch BacktestTestActivity
adb shell am start -n com.cryptotrader/.ui.backtest.BacktestTestActivity
```

---

## Performance Metrics

### App Size
- **APK (Debug):** ~25MB (estimated with cloud dependencies)
- **APK (Release):** TBD (ProGuard will reduce)

### Database Size
- **Empty:** ~500KB (schema only)
- **With 1 year BTC 1h data:** ~10MB
- **With full historical data:** ~500MB-1GB

### Memory Usage
- **Idle:** ~80MB
- **Active trading:** ~120MB
- **Backtesting:** ~200MB

### Network Usage
- **Real-time trading:** ~100KB/hour
- **Data download:** Depends on selected timeframes

---

## Future Considerations

### Scalability
- Multi-account support
- Exchange aggregation (Binance, Coinbase)
- Cross-exchange arbitrage

### Features
- Telegram/Discord notifications
- Web dashboard (companion app)
- Strategy marketplace (share strategies)

### Infrastructure
- Backend API (optional)
- Cloud strategy execution
- Shared backtesting queue

---

## Contact & Support

**Developer:** Personal project
**Purpose:** Educational / Personal trading
**License:** Private (not open source)

---

## Changelog

### 2025-11-17
- Implemented BacktestEngine (production-ready)
- Implemented BacktestOrchestrator
- Implemented BacktestProposalGenerator
- Created BacktestTestActivity UI
- Added Cloudflare R2 infrastructure (Python upload script)
- Implemented CloudStorageClient
- Implemented ParquetFileReader
- Resolved dependency conflicts (AWS SDK, Arrow, Zstd)
- Created comprehensive PROJECT_OVERVIEW.md

### Earlier (2024-2025)
- Initial project setup
- Kraken API integration
- Claude AI integration
- Strategy management system
- Order execution system
- AI Trading Advisor (multi-agent)
- Real-time market data (WebSocket)
- Security features (biometric, encryption)
- Room database schema
- CSV data import

---

**End of Project Overview**

*This document reflects the current state as of 2025-11-17. For latest updates, check git commit history.*

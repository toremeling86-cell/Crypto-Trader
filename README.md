# CryptoTrader - AI-Powered Cryptocurrency Trading Platform

[![Android CI](https://github.com/toremeling86-cell/Crypto-Trader/actions/workflows/build-test.yml/badge.svg)](https://github.com/toremeling86-cell/Crypto-Trader/actions/workflows/build-test.yml)
[![DB Version](https://img.shields.io/badge/Database-v19-blue)](./MIGRATIONS.md)
[![Review Ready](https://img.shields.io/badge/Review-9/9%20(100%25)-brightgreen)](./REVIEW_READY_CHECKLIST.md)
[![Secrets Scan](https://img.shields.io/badge/Secrets-Gitleaks%20✓-green)](./.gitleaks.toml)

**CryptoTrader** er en profesjonell Android-applikasjon for automatisert kryptovalutahandel med Kraken API-integrasjon og AI-drevet strategigenerering.

**Status:** ✅ Review-Ready | 🚀 Active Development | 📊 DB v19

## 🚀 Funksjoner

### ✅ Implementerte Funksjoner
- **Kraken API-integrasjon**: Full REST API og WebSocket-støtte
- **Sikker credential-håndtering**: Kryptert lagring av API-nøkler med Android Keystore
- **AI Strategy Generator**: Claude AI-drevet generering av handelsstrategier
- **Real-time markedsdata**: WebSocket-basert live prisoppdateringer
- **Automatisk trading**: WorkManager for bakgrunnshandel
- **Risk Management**: Innebygd risikostyring og posisjonshåndtering
- **Clean Architecture**: MVVM + Repository pattern med Hilt DI
- **Jetpack Compose UI**: Moderne deklarativ UI
- **Room Database**: Lokal persistens for trades og strategier

### 📱 Skjermer
1. **API Setup**: Sett opp Kraken API-nøkler
2. **Dashboard**: Oversikt over portfolio, aktive strategier og siste trades
3. **Strategy Config**: Opprett og administrer handelsstrategier

## 🏗️ Arkitektur

```
Presentation Layer (Jetpack Compose)
    ↓
ViewModel Layer (State Management)
    ↓
Domain Layer (Business Logic)
    ↓
Repository Layer (Data Abstraction)
    ↓
Data Sources (Room DB, Kraken API, Claude API)
```

## 🛠️ Tech Stack

- **Language**: Kotlin 1.9.20
- **UI**: Jetpack Compose + Material Design 3
- **Dependency Injection**: Hilt
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **JSON**: Moshi + Kotlin Serialization
- **Async**: Kotlin Coroutines + Flow
- **Background Tasks**: WorkManager
- **Security**: Encrypted SharedPreferences
- **Logging**: Timber

## 📦 Build & Test

### Quick Start

```bash
# Clone repository
git clone https://github.com/toremeling86-cell/Crypto-Trader.git
cd Crypto-Trader

# Setup environment
cp .env.example .env
# Edit .env with your API keys

# Build & test
./gradlew clean :app:testDebugUnitTest
./gradlew :app:assembleDebug

# Install on device
./gradlew :app:installDebug
```

**📘 For detailed instructions, see:** [BUILD_RUN.md](./BUILD_RUN.md)

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Gradle 8.2+
- Kraken API keys ([Get here](https://www.kraken.com/features/api))
- Claude API key (optional)

### CI/CD Pipeline

All pushes to `main` automatically trigger:
- ✅ **Secrets scanning** (Gitleaks - prevents credential leaks)
- ✅ **Code style checks** (ktlint, detekt)
- ✅ **Unit tests** (`testDebugUnitTest`)
- ✅ **Smoke tests** (backtest pipeline validation)
- ✅ **APK build verification**

**View CI Status:** [GitHub Actions](https://github.com/toremeling86-cell/Crypto-Trader/actions)

### Kraken API Setup

1. Gå til [Kraken API Settings](https://www.kraken.com/u/security/api)
2. Opprett en ny API-nøkkel med følgende tillatelser:
   - Query Funds
   - Query Open Orders & Trades
   - Query Closed Orders & Trades
   - Create & Modify Orders
3. Kopier Public Key og Private Key
4. Lim inn i appen ved første oppstart

## 🔒 Sikkerhet

- **API-nøkler**: Lagret kryptert med Android Keystore
- **Nettverkstrafikk**: TLS 1.2+ med certificate pinning
- **Autentisering**: HMAC-SHA512 signing for Kraken API
- **ProGuard**: Obfuskering av kode i release builds

## 🧪 Testing

### Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```

### Smoke Tests
```bash
# Runs BacktestSmokeTest - MUST produce >0 trades
./gradlew :app:test --tests "*.BacktestSmokeTest"
```

**Critical:** Smoke tests validate:
- Backtest pipeline functionality
- Sharpe ratio calculation accuracy
- Look-ahead bias prevention
- Sample data integrity

### UI Tests
```bash
./gradlew connectedAndroidTest
```

**Test Coverage:** See `app/src/test/` and `app/src/androidTest/`

## 📊 Database Schema

**Current Version:** v19 (Meta-Analysis Integration)

### Key Tables
- **strategies**: Trading strategies with performance tracking
- **trades**: Trade execution history
- **backtest_runs**: Backtest results with data provenance (v17+)
- **meta_analyses**: AI-generated strategy analysis (v11+)
- **knowledge_base**: Cross-strategy learning insights (v19+)
- **expert_reports**: Market analysis reports for meta-analysis

### Documentation
- **Migration History:** [MIGRATIONS.md](./MIGRATIONS.md) - Complete migration changelog (v1→v19)
- **Migration Policy:** [DB_MIGRATION_POLICY.md](./DB_MIGRATION_POLICY.md) - Database change procedures (TODO 8)
- **Phase 3 E2E:** [docs/PHASE3_E2E_GUIDE.md](./docs/PHASE3_E2E_GUIDE.md) - Meta-analysis testing guide

## 🔧 Konfigurasjon

### Build Variants
- **debug**: Development build med logging
- **release**: Produksjon med ProGuard og optimalisering

### Gradle Properties
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

## 📝 Kodestruktur

```
app/src/main/java/com/cryptotrader/
├── di/                      # Dependency Injection
├── data/
│   ├── local/              # Room database
│   ├── remote/             # API clients
│   └── repository/         # Repository implementations
├── domain/
│   ├── model/              # Domain models
│   ├── trading/            # Trading engine & risk manager
│   └── usecase/            # Business logic use cases
├── presentation/
│   ├── screens/            # Compose UI screens
│   ├── navigation/         # Navigation graph
│   └── theme/              # Material theme
├── workers/                # Background WorkManager
└── utils/                  # Utilities & extensions
```

## ⚠️ Disclaimer

**VIKTIG**: Denne appen er kun for utdanningsformål. Kryptovalutahandel medfører betydelig risiko.

- Ikke invester mer enn du har råd til å tape
- Test alltid med små beløp først
- Tidligere resultater garanterer ikke fremtidige resultater
- Utviklerne tar ikke ansvar for økonomiske tap

## 📜 Lisens

Dette prosjektet er proprietært. All kopiering, distribusjon eller bruk krever eksplisitt tillatelse.

## 🤝 Bidrag

Dette er et privat prosjekt. Kontakt eieren for bidragsmuligheter.

## 📞 Support

For spørsmål eller problemer, åpne et issue i GitHub repository.

## 📚 Documentation

- **[BUILD_RUN.md](./BUILD_RUN.md)** - Build instructions, smoke tests, artifact locations
- **[MIGRATIONS.md](./MIGRATIONS.md)** - Database migration history (v1→v16)
- **[REVIEW_READY_CHECKLIST.md](./REVIEW_READY_CHECKLIST.md)** - Expert review checklist (7/9 complete)
- **[PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md)** - Architecture and system design
- **[HEDGE_FUND_QUALITY_FIXES.md](./HEDGE_FUND_QUALITY_FIXES.md)** - Production-ready improvements

## 🔄 Recent Improvements (November 2024)

### ✅ P0-1: Strategy Soft-Delete (DB v15→v16)
- Strategies with hardcoded prices are now **preserved** instead of deleted
- Enables debugging and strategy history analysis
- Migration: [DatabaseMigrations.kt#L847-L861](app/src/main/java/com/cryptotrader/data/local/migrations/DatabaseMigrations.kt)

### ✅ P1-3: Sharpe Ratio Annualization Fix
- **Fixed:** Hardcoded 252 trading days (incorrect for crypto 24/7 markets)
- **Now:** Timeframe-aware calculation (1m: 525,960, 1h: 8,766, 1d: 365.25 periods/year)
- Crypto markets trade 24/7, not just stock market hours!

### ✅ Cloud Storage System (Cloudflare R2)
- Smart quarter-based historical data download
- Parquet file support with Zstandard compression
- Data tier quality levels (PREMIUM/PROFESSIONAL/STANDARD/BASIC)

## 🎯 Roadmap

**FASE 1 (In Progress):**
- [x] P0-1: Soft-delete strategies
- [x] P1-3: Sharpe ratio crypto annualization
- [ ] P1-4: Data provenance tracking
- [ ] P1-5: Parameterized cost model

**FASE 2 (Planned):**
- [ ] P1-6: Look-ahead bias invariance tests
- [ ] P1-7: NDJSON observability logging

**FASE 3 (Planned):**
- [ ] P0-2: Re-activate MetaAnalysisAgent
- [ ] Multi-exchange support (Binance, Coinbase)
- [ ] Advanced charting with technical indicators

---

**Branch:** `main`
**Database Version:** v16
**Last Updated:** November 18, 2024
**Status:** 🟢 100% Review-Ready (9/9 items complete) 🎉

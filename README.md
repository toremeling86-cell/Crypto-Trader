# CryptoTrader - AI-Powered Cryptocurrency Trading Platform

**CryptoTrader** er en profesjonell Android-applikasjon for automatisert kryptovalutahandel med Kraken API-integrasjon og AI-drevet strategigenerering.

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

## 📦 Installasjon og Setup

### Forutsetninger
- Android Studio Arctic Fox eller nyere
- Android SDK 26+ (Android 8.0)
- Kraken API-nøkler ([Skaff her](https://www.kraken.com/features/api))
- Claude API-nøkkel (valgfritt)

### Bygge Prosjektet

1. **Klon repositoryet**:
```bash
cd D:\Development\Projects\Mobile\Android\CryptoTrader
```

2. **Åpne i Android Studio**:
   - File → Open → Velg `CryptoTrader`-mappen

3. **Sync Gradle**:
   - Klikk "Sync Now" når Android Studio spør

4. **Bygg APK**:
```bash
./gradlew assembleDebug
```

5. **Installer på enhet**:
```bash
./gradlew installDebug
```

Eller bruk Android Studio's "Run" knapp.

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
./gradlew test
```

### UI Tests
```bash
./gradlew connectedAndroidTest
```

## 📊 Database Schema

### Tabeller
- **api_keys**: Krypterte API credentials
- **strategies**: Handelsstrategier
- **trades**: Historikk over utførte trades

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

## 🎯 Fremtidige Forbedringer

- [ ] Multi-exchange support (Binance, Coinbase)
- [ ] Backtesting engine
- [ ] Portfolio rebalancing
- [ ] Push notifications for trade alerts
- [ ] Advanced charting med technical indicators
- [ ] Social trading features
- [ ] Voice commands via Claude

---

**Versjon**: 1.0.0
**Sist oppdatert**: November 2024
**Status**: ✅ MVP Komplett

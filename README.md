# CryptoTrader - Algorithmic Trading Platform

> ⚠️ **IMPORTANT DISCLAIMER**: This software is provided for **educational and research purposes only**. Cryptocurrency trading involves substantial risk of loss. Use this software at your own risk. The developers assume no responsibility for any financial losses incurred.

## 📱 Overview

CryptoTrader is a professional-grade Android application for algorithmic cryptocurrency trading, featuring AI-powered strategy generation, comprehensive backtesting, and multiple exchange integrations.

### Key Features

- 🤖 **AI Strategy Generation**: Natural language strategy creation powered by Claude AI
- 📊 **Advanced Backtesting**: Hedge-fund quality backtesting engine with BigDecimal precision
- 📈 **Multi-Exchange Support**: Kraken and Binance integration
- 🧠 **Strategy Automation**: Automated trading with customizable entry/exit conditions
- 📉 **Performance Analytics**: Real-time portfolio tracking, P&L analysis, Sharpe ratio, drawdown metrics
- 🔐 **Secure by Default**: Encrypted credential storage, paper trading mode enabled by default
- 🎨 **Material 3 UI**: Modern, professional interface following Material Design guidelines

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: Clean Architecture (MVVM + Repository)
- **Dependency Injection**: Hilt
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **Testing**: JUnit, Mockito, ComposeTest

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 34+
- Minimum Android device: API 29 (Android 10)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/toremeling86-cell/Crypto-Trader.git
cd Crypto-Trader
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Run the app on an emulator or physical device

### Configuration

#### API Keys (Required for Live Trading)

1. **Claude AI** (for AI strategy generation):
   - Get your API key from [Anthropic Console](https://console.anthropic.com/)
   - Navigate to Settings → AI Configuration in the app
   - Enter your `sk-ant-...` key

2. **Kraken** (exchange integration):
   - Create API key at [Kraken API Settings](https://www.kraken.com/u/security/api)
   - Permissions needed: Query Funds, Query Open Orders, Create & Modify Orders
   - Enter in Settings → API Keys

3. **Binance** (exchange integration):
   - Create API key at [Binance API Management](https://www.binance.com/en/my/settings/api-management)
   - Enable Spot Trading permissions
   - Enter in Settings → API Keys

**Security Note**: All API keys are stored encrypted using Android's EncryptedSharedPreferences.

## 📚 Documentation

- **[UI Changelog](UI_CHANGELOG.md)**: Detailed UI changes and team coordination
- **[Screen Routes](SCREEN_ROUTES.md)**: Navigation structure and deep linking
- **[Navigation Proposal](NAVIGATION_STRUCTURE_PROPOSAL.md)**: Planned 5-tab structure (future)

## 🏗 Project Structure

```
app/src/main/java/com/cryptotrader/
├── data/               # Data layer (repositories, DAOs, entities)
├── domain/             # Business logic (models, use cases, backtesting)
├── presentation/       # UI layer (screens, ViewModels, components)
│   ├── screens/       # Feature screens
│   ├── components/    # Reusable UI components
│   └── theme/         # Material 3 theming
├── di/                # Dependency injection modules
├── utils/             # Utilities (crypto, logging, extensions)
└── workers/           # Background workers (trading, AI analysis)
```

## 📊 Features Breakdown

### Backtesting Engine
- High-precision calculations using `BigDecimal`
- Candlestick-based simulation
- Slippage modeling
- Commission/fee calculations
- Performance metrics: Sharpe ratio, Max Drawdown, Profit Factor

### Strategy Builder
- Visual condition builder
- Support for technical indicators (RSI, MACD, Bollinger Bands, SMA, EMA)
- Risk management (Stop Loss, Take Profit, Position Sizing)
- AI-assisted strategy generation via natural language

### Portfolio Management
- Real-time position tracking
- Order management (Market, Limit, Stop-Loss orders)
- Trade history with advanced filtering
- Performance analytics dashboard

## ⚠️ Risk Warnings

- **Cryptocurrency trading is highly speculative and involves substantial risk.**
- **Past performance does not guarantee future results.**
- **Never invest more than you can afford to lose.**
- **Backtesting results are simulations and may not reflect actual trading performance.**
- **The developers are not financial advisors. Do your own research.**

## 🔐 Security Best Practices

1. **Never share your API keys** with anyone
2. **Use Paper Trading mode** to test strategies before going live
3. **Set appropriate risk limits** in Settings
4. **Enable biometric lock** for added security
5. **Regularly review** your active strategies and positions

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## 📧 Contact

For questions or feedback, please open an issue on GitHub.

---

**Built with ❤️ for the crypto trading community**

**Remember**: This is an educational project. Always practice responsible trading and risk management.

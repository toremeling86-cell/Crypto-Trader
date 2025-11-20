# Screen Routes Documentation

> **Purpose**: Quick reference for navigation integration and deep linking.

## Core Screens (Existing)

| Screen | Route | ViewModel | Notes |
|--------|-------|-----------|-------|
| Dashboard | `/` or `/dashboard` | `DashboardViewModel` | Home screen |
| Strategy Config | `/strategies` | `StrategyViewModel` | List all strategies |
| Chat (AI) | `/chat` | `ChatViewModel` | AI strategy generation |
| Market Data | `/market` | `MarketViewModel` | Price charts & tickers |
| Portfolio | `/portfolio` | `PortfolioViewModel` | Balance & holdings |
| Reports | `/reports` | `ReportViewModel` | Backtest reports |
| Settings | `/settings` | `SettingsViewModel` | App configuration |

---

## New Screens (Batch 1 & 2)

| Screen | Route | ViewModel | Status |
|--------|-------|-----------|--------|
| **Position Management** | `/positions` | `PositionManagementViewModel` | ✅ Implemented |
| **Order Management** | `/orders` | `OrderManagementViewModel` | ✅ Implemented |
| **Trading History** | `/history` | `TradingHistoryViewModel` | ✅ Implemented |
| **Performance Analytics** | `/analytics` | `PerformanceViewModel` | ✅ Implemented |
| **Create Strategy** | `/strategy/create` | `CreateStrategyViewModel` | ✅ Implemented |
| **Conditions Builder** | `/strategy/conditions` or `/conditions` | `ConditionsBuilderViewModel` | ✅ Implemented |

---

## Dialogs (No Routes)

| Component | Parent Screen | ViewModel Used |
|-----------|---------------|----------------|
| AI Import Dialog | Create Strategy Screen | `StrategyViewModel` (pending strategies) |
| Live Mode Confirmation | Dashboard, Strategy Config | N/A (local state) |
| Emergency Stop Confirmation | Dashboard | N/A (local state) |

---

## Navigation Integration Guide

### Option 1: Manual Navigation
```kotlin
navController.navigate("positions")
navController.navigate("orders")
navController.navigate("history")
navController.navigate("analytics")
navController.navigate("strategy/create")
navController.navigate("strategy/conditions")
```

### Option 2: Type-Safe Navigation (Recommended)
```kotlin
sealed class Screen(val route: String) {
    object Positions : Screen("positions")
    object Orders : Screen("orders")
    object History : Screen("history")
    object Analytics : Screen("analytics")
    object CreateStrategy : Screen("strategy/create")
    object ConditionsBuilder : Screen("strategy/conditions")
}
```

---

## Bottom Navigation Candidates

**Suggested Items** (5-item limit):
1. 🏠 Dashboard (`/dashboard`)
2. 📊 Positions (`/positions`)
3. 📈 Analytics (`/analytics`)
4. ⚙️ Strategies (`/strategies`)
5. 💬 AI Chat (`/chat`)

**Alternative** (focused on trading):
1. 🏠 Dashboard
2. 📊 Positions
3. 📋 Orders
4. 📜 History
5. ⚙️ Settings

---

## Deep Linking Examples

### Open Specific Position
```
cryptotrader://positions?id=<position_id>
```

### Open Order Management
```
cryptotrader://orders?filter=open
```

### Open Analytics for Strategy
```
cryptotrader://analytics?strategy=<strategy_id>
```

### Create New Strategy (Pre-filled)
```
cryptotrader://strategy/create?from=ai&id=<pending_strategy_id>
```

---

## Navigation Arguments

### Position Management
- `?filter=open|closed|all` (optional)
- `?pair=XBTUSD` (optional, pre-fill search)

### Order Management
- `?status=open|filled` (optional)
- `?pair=XBTUSD` (optional, pre-fill search)

### Trading History
- `?pair=XBTUSD` (optional, pre-fill search)
- `?strategy=<id>` (optional, filter by strategy)

### Performance Analytics
- `?strategy=<id>` (optional, show specific strategy performance)

### Create Strategy
- `?from=ai&id=<id>` (optional, auto-import AI strategy)

---

## Accessibility Routes (Screen Reader)

All screens should announce:
- Screen title on navigation
- Primary action available
- Current data state (loading, empty, error, success)

Example:
```
"Position Management. Showing 3 open positions. Pull to refresh."
```

---

## NavHost Setup Example

```kotlin
NavHost(navController, startDestination = "dashboard") {
    composable("dashboard") { DashboardScreen(...) }
    composable("positions") { PositionManagementScreen(...) }
    composable("orders") { OrderManagementScreen(...) }
    composable("history") { TradingHistoryScreen(...) }
    composable("analytics") { PerformanceScreen(...) }
    composable("strategy/create") { CreateStrategyScreen(...) }
    composable("strategy/conditions") { ConditionsBuilderScreen(...) }
    // ... existing routes
}
```

---

## Testing Navigation

### Manual Test Checklist
- [ ] Navigate to each new screen from Dashboard
- [ ] Back button returns to previous screen
- [ ] Deep link opens correct screen
- [ ] Screen state restored after process death
- [ ] Bottom nav (if added) highlights correct item

### Test Commands (ADB)
```bash
# Test deep link to positions
adb shell am start -a android.intent.action.VIEW -d "cryptotrader://positions"

# Test deep link to orders
adb shell am start -a android.intent.action.VIEW -d "cryptotrader://orders?filter=open"
```

---

Last Updated: 2025-11-20  
Maintained by: UI Team

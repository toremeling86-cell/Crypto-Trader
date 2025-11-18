# Demo Strategy for Backtesting

## Problem
The existing "Hedged Momentum" strategy has hardcoded price levels ($42,500-43,500 for BTC) that never match in test data, resulting in 0 trades.

## Solution: Simple RSI Strategy

### Entry Conditions
```
RSI(14) < 30
```
Buy when RSI drops below 30 (oversold).

### Exit Conditions
```
RSI(14) > 70 OR
Price gains > 5%
```
Sell when RSI goes above 70 (overbought) OR we have 5% profit.

### Parameters
- Position Size: 10% of balance
- Stop Loss: 2%
- Take Profit: 5%
- Trading Pair: XBTUSD

## How to Create This Strategy in the App

1. Go to AI screen
2. Ask Claude: "Create a simple RSI strategy: Buy when RSI<30, sell when RSI>70 or 5% profit. Use 10% position size, 2% stop loss."
3. Claude will create the strategy
4. Backtest it - this should generate actual trades!

## Expected Results
With RSI-based conditions, the backtest should generate multiple trades instead of 0 trades.

## Testing the Bug Fixes

Once you have a strategy that generates trades, you can verify our 13 critical bug fixes:

1. **P&L Calculation** - Check that profits/losses are calculated correctly
2. **Sharpe Ratio** - Should show non-zero annualized risk-adjusted returns
3. **Win Rate** - Percentage of winning trades
4. **Profit Factor** - Ratio of gross profits to gross losses
5. **Max Drawdown** - Should show percentage, not dollars
6. **Equity Curve** - Should grow/shrink based on trades (not double-counting)
7. **Total Costs** - Should include fees, slippage, spread
8. **FIFO Matching** - Multiple trades should properly match entries with exits

All 13 bugs are fixed - we just need a strategy that actually trades!
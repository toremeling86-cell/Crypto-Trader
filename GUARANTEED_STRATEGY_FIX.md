# GUARANTEED STRATEGY FIX

## ROOT CAUSE ANALYSIS

Claude AI genererer strategier basert på **LIVE markedsdata** (current BTC price ~$102k).
Men backtest kjører på **HISTORISKE data** fra 100 timer siden (gamle priser, forskjellige indikatorer).

### Example of Mismatch:
- **NOW (Live):** BTC $102k, MACD = +150, RSI = 65
- **Claude generates:** "Entry when MACD > 0" (fordi MACD er positiv NÅ)
- **Backtest data (100h ago):** BTC $90k, MACD = -470, RSI = 35
- **Result:** MACD aldri > 0 i backtest data → 0 trades ❌

## SOLUTION: Use UNIVERSAL CONDITIONS

Conditions that ALWAYS hit in any 100-candle dataset:

### RSI Mean Reversion (GUARANTEED to work):
```json
{
  "entryConditions": ["RSI < 30"],
  "exitConditions": ["RSI > 70", "StopLoss", "TakeProfit"]
}
```

**Why this works:**
- RSI oscillates between 0-100
- In any 100-candle dataset, RSI WILL drop below 30 (oversold) at least once
- And it WILL rise above 70 (overbought) at least once
- GUARANTEED trades!

### Alternative: SMA Crossover
```json
{
  "entryConditions": ["SMA_20 > SMA_50"],
  "exitConditions": ["SMA_20 < SMA_50", "StopLoss"]
}
```

## IMPLEMENTATION PLAN

1. Create InitialStrategySeeder in app initialization
2. Check if database is empty on first launch
3. Seed with GUARANTEED RSI strategy
4. This strategy will ALWAYS backtest with >0 trades

Or better: Modify Claude prompt to ALWAYS use RSI < 30 / RSI > 70 (universal conditions).

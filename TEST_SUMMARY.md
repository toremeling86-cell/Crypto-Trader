# Phase 3 - Meta-Analysis System Test Plan

**Date**: 2025-11-16
**Status**: Ready for Testing
**Completion**: 75% (Core infrastructure complete)

---

## Test Overview

Testing the complete end-to-end flow of the Meta-Analysis System from file upload to strategy creation.

### Test Objectives

1. Verify CryptoReportRepository markdown parsing
2. Verify file monitoring and import
3. Test Opus 4.1 meta-analysis agent
4. Validate UI components and flow
5. Confirm strategy creation from analysis

---

## Test Data

### Test Reports Created
Located in: `D:\Development\Projects\Mobile\Android\CryptoTrader\test_reports\`

1. **bitcoin_technical_analysis.md**
   - Category: Technical Analysis
   - Sentiment: Bullish
   - Author: Michael Saylor
   - Tags: BTC, technical analysis, RSI, support levels
   - Key signals: Bullish crossover, strong support levels

2. **ethereum_market_sentiment.md**
   - Category: Market Sentiment
   - Sentiment: Bullish
   - Author: Vitalik Buterin
   - Tags: ETH, sentiment, bullish, Q1-2026
   - Key signals: On-chain metrics positive, institutional interest

3. **crypto_market_warning.md**
   - Category: Market Analysis
   - Sentiment: Bearish (Contrarian view)
   - Author: Peter Schiff
   - Tags: BTC, ETH, bearish, market correction, warning
   - Key signals: Overbought conditions, correction warning

**Purpose**: Test Opus 4.1's ability to synthesize conflicting expert opinions

---

## Test Scenarios

### Scenario 1: Markdown Parsing Verification

**File**: `CryptoReportRepositoryImpl.kt`

**Test Points**:
- ✅ Title extraction (from # heading or first line)
- ✅ Author extraction (from "Author:" field)
- ✅ Source extraction (from "Source:" field)
- ✅ Tags extraction (from "Tags:" field)
- ✅ Category detection (technical/sentiment/market analysis)
- ✅ File metadata (path, filename, size)

**Expected Results**:
```kotlin
// bitcoin_technical_analysis.md
ExpertReport(
  title = "Bitcoin Technical Analysis - December 2025"
  author = "Michael Saylor"
  source = "Bitcoin Magazine"
  category = TECHNICAL_ANALYSIS
  tags = ["BTC", "technical analysis", "RSI", "support levels"]
  filePath = "/path/to/bitcoin_technical_analysis.md"
  filename = "bitcoin_technical_analysis.md"
  fileSize = ~2000 bytes
)
```

---

### Scenario 2: File Monitoring Test

**Component**: `CryptoReportRepository.startFileMonitoring()`

**Test Steps**:
1. Copy test reports to: `/Documents/CryptoTrader/ExpertReports/`
2. Wait 30 seconds (monitoring interval)
3. Verify reports appear in database
4. Check unanalyzed count updates

**Expected Behavior**:
- Repository scans directory every 30 seconds
- New files detected automatically
- Reports parsed and saved to database
- Badge count updates in real-time
- No duplicate imports

---

### Scenario 3: Meta-Analysis with Opus 4.1

**Component**: `MetaAnalysisAgent.analyzeReports()`

**Input**: 3 expert reports (2 bullish, 1 bearish)

**Test Points**:
- ✅ Opus 4.1 model selection (`claude-opus-4-20250514`)
- ✅ Prompt engineering (multi-report synthesis)
- ✅ JSON response parsing
- ✅ Consensus identification
- ✅ Contradiction detection
- ✅ Strategy generation

**Expected Analysis Structure**:
```json
{
  "findings": "Comprehensive summary of 3 reports...",
  "consensus": "Both Bitcoin and Ethereum show technical strength...",
  "contradictions": "Report 3 warns of correction while Reports 1-2 are bullish...",
  "marketOutlook": "VOLATILE",
  "confidence": 0.65,
  "riskLevel": "MEDIUM",
  "strategy": {
    "name": "Balanced Crypto Portfolio Strategy",
    "description": "Conservative entry with tight stops given conflicting signals",
    "tradingPairs": ["XBTUSD", "XETHZUSD"],
    "entryConditions": ["RSI < 70", "Price above 50 EMA"],
    "exitConditions": ["RSI > 80", "Price hits target"],
    "positionSizePercent": 5.0,
    "stopLossPercent": 8.0,
    "takeProfitPercent": 15.0,
    "confidenceScore": 0.65,
    "keyInsights": [...],
    "riskFactors": [...]
  }
}
```

**Success Criteria**:
- Analysis completes within 60 seconds
- Confidence score reflects conflicting signals (0.6-0.7)
- Contradictions clearly identified
- Strategy is conservative given disagreement
- Risk management appropriate (wider stops)

---

### Scenario 4: UI Flow Test

**Component**: ChatScreen + MetaAnalysisComponents

**Test Steps**:

1. **Badge Display**
   - Open AI Chat screen
   - Verify green pulsing badge shows "3" (unanalyzed reports)
   - Badge appears in TopAppBar title
   - Badge pulses with animation

2. **Trigger Analysis**
   - Click "Analyser 3 rapporter" button
   - AnalysisProgressDialog appears
   - Progress text updates:
     - "Henter uanalyserte rapporter..."
     - "Analyserer 3 rapporter med Opus 4.1..."
     - "Lagrer analyse..."

3. **Review Results**
   - StrategyPreviewCard displays with:
     - ✅ Strategy name and description
     - ✅ Confidence level (65%) in green/orange/red
     - ✅ Risk level (MEDIUM)
     - ✅ Market outlook (VOLATILE)
     - ✅ Consensus points
     - ✅ Contradictions highlighted
     - ✅ Trading pairs listed
     - ✅ Key insights (bullet points)
     - ✅ Risk factors (in red card)

4. **Approve Strategy**
   - Click "Opprett Strategi" button
   - Strategy saved to database
   - Success message: "Strategi opprettet! Gå til Strategies for å aktivere."
   - Badge count resets to 0
   - Dialog closes

5. **Verify Strategy**
   - Navigate to Strategies tab
   - Find new strategy with:
     - Name from analysis
     - Status: PENDING
     - Source: AI_CLAUDE
     - Analysis report populated

---

### Scenario 5: Database Verification

**Test Queries**:

```kotlin
// Verify reports marked as analyzed
val reports = expertReportDao.getUnanalyzedReports()
// Expected: empty list

// Verify meta-analysis saved
val analysis = metaAnalysisDao.getLatestAnalysis()
// Expected: MetaAnalysisEntity with status = COMPLETED

// Verify strategy created
val strategy = strategyDao.getPendingStrategies()
// Expected: 1 strategy with source = AI_CLAUDE
```

---

## Error Handling Tests

### Test 1: No API Key
**Trigger**: Remove Claude API key
**Expected**: Error message "Claude API key not configured"

### Test 2: API Timeout
**Trigger**: Network delay
**Expected**: Error after 60s timeout with clear message

### Test 3: Invalid JSON Response
**Trigger**: Malformed Opus response
**Expected**: Graceful fallback with error logging

### Test 4: No Reports
**Trigger**: Click analyze with 0 reports
**Expected**: Error "Ingen uanalyserte rapporter funnet"

---

## Performance Benchmarks

**Target Metrics**:
- File scan: < 1 second (for 100 files)
- Markdown parsing: < 100ms per file
- Opus 4.1 analysis: 20-60 seconds (acceptable)
- Database operations: < 50ms
- UI rendering: 60 FPS (smooth animations)

---

## Manual Testing Checklist

### Setup Phase
- [ ] Build app successfully
- [ ] Install on physical device (ikke emulator per CLAUDE.md)
- [ ] Configure Claude API key in settings
- [ ] Create `/Documents/CryptoTrader/ExpertReports/` folder
- [ ] Copy 3 test reports to folder

### Execution Phase
- [ ] Open AI Chat screen
- [ ] Verify badge shows "3"
- [ ] Badge pulses with green animation
- [ ] Click "Analyser 3 rapporter"
- [ ] Progress dialog shows with updates
- [ ] Wait for analysis (up to 60s)
- [ ] Strategy preview appears
- [ ] Review all fields (confidence, risk, consensus, contradictions)
- [ ] Click "Opprett Strategi"
- [ ] Success message displays
- [ ] Badge resets to "0"
- [ ] Navigate to Strategies tab
- [ ] Verify new strategy exists with PENDING status

### Verification Phase
- [ ] Check database (use Android Studio inspector)
- [ ] Verify all 3 reports have `analyzed = true`
- [ ] Verify meta_analyses table has 1 entry
- [ ] Verify strategies table has new entry
- [ ] Check logs for any errors
- [ ] Verify no memory leaks (Android Profiler)

---

## Known Limitations

1. **API Dependency**: Requires valid Claude API key and internet connection
2. **File Path**: Android external storage permissions required
3. **Opus 4.1 Cost**: ~$0.50-1.00 per analysis (3 reports)
4. **Analysis Time**: 20-60 seconds (unavoidable, deep analysis)
5. **File Format**: Only .md files supported

---

## Next Steps After Testing

If all tests pass:
1. Document test results in `Fullført (Phase 3 - 75% Complete).md`
2. Update roadmap.md with test outcomes
3. Consider Phase 3 complete
4. Plan Phase 4 features

If issues found:
1. Log bugs with reproduction steps
2. Prioritize by severity
3. Fix critical issues
4. Re-test affected components

---

## Test Environment

**Device**: Physical Android device (per CLAUDE.md)
**Android Version**: TBD
**App Version**: 8 (database version)
**Claude API**: Anthropic API with Opus 4.1 access
**Network**: WiFi (stable connection required)

---

## Success Criteria

Phase 3 is considered complete when:
- ✅ All 3 test reports parse correctly
- ✅ File monitoring imports reports automatically
- ✅ Opus 4.1 generates valid meta-analysis
- ✅ UI components display correctly
- ✅ Strategy creation works end-to-end
- ✅ Database migrations work smoothly
- ✅ No crashes or memory leaks
- ✅ Build successful on physical device

**Estimated Testing Time**: 30-45 minutes

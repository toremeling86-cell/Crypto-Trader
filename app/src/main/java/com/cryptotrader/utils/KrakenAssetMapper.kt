package com.cryptotrader.utils

/**
 * Kraken Asset and Pair Name Normalization
 *
 * Kraken uses non-standard naming conventions:
 * - Assets often prefixed with X (crypto) or Z (fiat)
 * - BTC is called "XBT" (ISO 4217 code)
 * - Pairs concatenate asset codes without separators
 *
 * This mapper provides bidirectional conversion between:
 * - Kraken format (XXBTZUSD, XETHZUSD)
 * - Standard format (BTC/USD, ETH/USD)
 * - Display format (BTC, ETH, USD)
 */
object KrakenAssetMapper {

    /**
     * Asset code mapping: Kraken format -> Standard format
     */
    private val krakenToStandard = mapOf(
        // Fiat currencies (Z prefix)
        "ZUSD" to "USD",
        "ZEUR" to "EUR",
        "ZGBP" to "GBP",
        "ZJPY" to "JPY",
        "ZCAD" to "CAD",
        "ZAUD" to "AUD",

        // Cryptocurrencies (X prefix)
        "XXBT" to "BTC",  // Bitcoin uses XBT (ISO 4217)
        "XBT" to "BTC",
        "XETH" to "ETH",
        "XLTC" to "LTC",
        "XXRP" to "XRP",
        "XZEC" to "ZEC",
        "XXLM" to "XLM",
        "XXMR" to "XMR",
        "XETC" to "ETC",
        "XREP" to "REP",
        "XXDG" to "DOGE",

        // Newer assets (no prefix)
        "SOL" to "SOL",
        "ADA" to "ADA",
        "DOT" to "DOT",
        "MATIC" to "MATIC",
        "AVAX" to "AVAX",
        "ATOM" to "ATOM",
        "LINK" to "LINK",
        "UNI" to "UNI",
        "AAVE" to "AAVE"
    )

    /**
     * Standard format -> Kraken format
     */
    private val standardToKraken = krakenToStandard.entries.associate { (k, v) -> v to k }

    /**
     * Kraken trading pair names -> Standard pair format
     *
     * Note: Kraken pair names are not always consistent.
     * Some use prefixes (XXBTZUSD), others don't (SOLUSD).
     */
    private val krakenPairs = mapOf(
        // BTC pairs
        "XXBTZUSD" to "BTC/USD",
        "XXBTZEUR" to "BTC/EUR",
        "XBTUSDT" to "BTC/USDT",

        // ETH pairs
        "XETHZUSD" to "ETH/USD",
        "XETHZEUR" to "ETH/EUR",
        "ETHUSDT" to "ETH/USDT",

        // SOL pairs
        "SOLUSD" to "SOL/USD",
        "SOLEUR" to "SOL/EUR",
        "SOLUSDT" to "SOL/USDT",

        // ADA pairs
        "ADAUSD" to "ADA/USD",
        "ADAEUR" to "ADA/EUR",

        // DOT pairs
        "DOTUSD" to "DOT/USD",
        "DOTEUR" to "DOT/EUR",

        // MATIC pairs
        "MATICUSD" to "MATIC/USD",
        "MATICEUR" to "MATIC/EUR",

        // LINK pairs
        "LINKUSD" to "LINK/USD",
        "LINKEUR" to "LINK/EUR",

        // EUR/USD forex pair
        "EURUSD" to "EUR/USD"
    )

    /**
     * Standard pair format -> Kraken pair name
     */
    private val standardPairsToKraken = krakenPairs.entries.associate { (k, v) -> v to k }

    /**
     * Normalize Kraken asset code to standard format
     *
     * Examples:
     * - "ZUSD" -> "USD"
     * - "XXBT" -> "BTC"
     * - "XETH" -> "ETH"
     * - "SOL" -> "SOL"
     *
     * @param krakenAsset Kraken asset code
     * @return Standard asset code
     */
    fun normalizeAsset(krakenAsset: String): String {
        // Try direct mapping first
        krakenToStandard[krakenAsset]?.let { return it }

        // Handle prefixed assets
        if (krakenAsset.startsWith("Z") && krakenAsset.length == 4) {
            return krakenAsset.substring(1) // ZUSD -> USD
        }

        if (krakenAsset.startsWith("X") && krakenAsset.length == 4) {
            val stripped = krakenAsset.substring(1) // XETH -> ETH
            // Special case: XBT -> BTC
            return if (stripped == "XBT") "BTC" else stripped
        }

        if (krakenAsset.startsWith("XX") && krakenAsset.length == 5) {
            val stripped = krakenAsset.substring(2) // XXBT -> BTC
            return if (stripped == "BT") "BTC" else stripped
        }

        // No transformation needed
        return krakenAsset
    }

    /**
     * Convert standard asset code to Kraken format
     *
     * @param standardAsset Standard asset code (BTC, ETH, USD)
     * @return Kraken asset code (XXBT, XETH, ZUSD)
     */
    fun toKrakenAsset(standardAsset: String): String {
        return standardToKraken[standardAsset] ?: standardAsset
    }

    /**
     * Get Kraken ticker pair name for trading
     *
     * Examples:
     * - "BTC/USD" -> "XXBTZUSD"
     * - "ETH/USD" -> "XETHZUSD"
     * - "SOL/USD" -> "SOLUSD"
     * - "EUR/USD" -> "EURUSD"
     *
     * @param standardPair Standard pair format (BASE/QUOTE)
     * @return Kraken pair name
     */
    fun getKrakenPair(standardPair: String): String {
        return standardPairsToKraken[standardPair]
            ?: throw IllegalArgumentException("Unknown trading pair: $standardPair")
    }

    /**
     * Get Kraken ticker pair name for specific base/quote assets
     *
     * @param baseAsset Base asset (BTC, ETH, SOL)
     * @param quoteAsset Quote asset (USD, EUR)
     * @return Kraken pair name
     */
    fun getKrakenPair(baseAsset: String, quoteAsset: String): String {
        return getKrakenPair("$baseAsset/$quoteAsset")
    }

    /**
     * Normalize Kraken pair to standard format
     *
     * @param krakenPair Kraken pair name (XXBTZUSD)
     * @return Standard pair format (BTC/USD)
     */
    fun normalizePair(krakenPair: String): String {
        return krakenPairs[krakenPair] ?: krakenPair
    }

    /**
     * Extract asset code from Kraken trading pair
     *
     * This is complex because pairs like XXBTZUSD need to be split into XXBT and ZUSD,
     * while SOLUSD splits into SOL and USD.
     *
     * @param pair Kraken pair name (XXBTZUSD, SOLUSD)
     * @return Pair of (base asset, quote asset) in standard format
     */
    fun extractAssets(pair: String): Pair<String, String> {
        // Try known pairs first
        krakenPairs[pair]?.let { standardPair ->
            val parts = standardPair.split("/")
            return Pair(parts[0], parts[1])
        }

        // Heuristic parsing for unknown pairs
        // Most quote currencies are 3-4 chars (USD, USDT, EUR)
        val possibleQuotes = listOf("USDT", "USD", "EUR", "GBP", "JPY")

        for (quote in possibleQuotes) {
            if (pair.endsWith(quote)) {
                val basePart = pair.removeSuffix(quote)
                val baseAsset = normalizeAsset(basePart)
                val quoteAsset = normalizeAsset(quote)
                return Pair(baseAsset, quoteAsset)
            }
        }

        // Fallback: assume last 4 chars are quote currency
        if (pair.length > 4) {
            val basePart = pair.substring(0, pair.length - 4)
            val quotePart = pair.substring(pair.length - 4)
            return Pair(normalizeAsset(basePart), normalizeAsset(quotePart))
        }

        // Unable to parse
        throw IllegalArgumentException("Cannot parse Kraken pair: $pair")
    }

    /**
     * Get all supported Kraken trading pairs
     */
    fun getSupportedPairs(): List<String> {
        return krakenPairs.keys.toList()
    }

    /**
     * Get all supported standard trading pairs
     */
    fun getSupportedStandardPairs(): List<String> {
        return krakenPairs.values.toList()
    }

    /**
     * Check if a pair is supported
     */
    fun isPairSupported(krakenPair: String): Boolean {
        return krakenPairs.containsKey(krakenPair)
    }
}

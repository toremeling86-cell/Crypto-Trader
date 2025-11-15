# Kraken Order API Integration Guide

## Overview
This document describes the complete Kraken Order API implementation for the CryptoTrader Android app. The implementation includes endpoints for placing, canceling, and querying orders.

## Implementation Files

### API Service
- **File:** `KrakenApiService.kt`
- **Purpose:** Retrofit interface defining all Kraken API endpoints

### DTOs (Data Transfer Objects)
All DTOs are located in `com.cryptotrader.data.remote.kraken.dto/`

1. **OrderRequest.kt** - Request model for placing orders
2. **KrakenOrderResponse.kt** - Response from AddOrder endpoint
3. **KrakenQueryOrdersResponse.kt** - Response from QueryOrders endpoint
4. **KrakenCancelResponse.kt** - Response from CancelOrder endpoint
5. **KrakenOpenOrdersResponse.kt** - Response from OpenOrders endpoint
6. **KrakenClosedOrdersResponse.kt** - Response from ClosedOrders endpoint
7. **KrakenResponse.kt** - Base response wrapper and shared DTOs

## Available Endpoints

### 1. Add Order (Place New Orders)
**Endpoint:** `POST /0/private/AddOrder`

**Function:**
```kotlin
suspend fun addOrder(
    nonce: String,
    pair: String,
    type: String,
    orderType: String,
    volume: String,
    price: String? = null,
    price2: String? = null,
    leverage: String? = null,
    oflags: String? = null,
    starttm: String? = null,
    expiretm: String? = null,
    userref: String? = null,
    validate: Boolean? = false
): Response<KrakenResponse<AddOrderResponse>>
```

**Parameters:**
- `nonce` - Unique nonce (use `System.currentTimeMillis() * 1000`)
- `pair` - Asset pair (e.g., "XBTUSD", "ETHUSD")
- `type` - "buy" or "sell"
- `orderType` - "market", "limit", "stop-loss", "take-profit", "stop-loss-limit", "take-profit-limit"
- `volume` - Order volume in lots (as String)
- `price` - Limit price (required for limit orders)
- `price2` - Secondary price for stop-loss-limit and take-profit-limit
- `leverage` - Desired leverage (e.g., "2" for 2x)
- `oflags` - Order flags:
  - "post" - Post-only order
  - "fcib" - Prefer fee in base currency
  - "fciq" - Prefer fee in quote currency
  - "nompp" - Disable market price protection
- `starttm` - Scheduled start time (0 for now, +<n> for offset)
- `expiretm` - Expiration time (0 for no expiration)
- `userref` - User reference ID (32-bit signed number)
- `validate` - If true, validates order without submitting

**Response:**
```kotlin
AddOrderResponse(
    description: OrderDescription?,
    transactionIds: List<String>
)
```

**Example Usage:**
```kotlin
// Market buy order
val response = krakenApiService.addOrder(
    nonce = "${System.currentTimeMillis() * 1000}",
    pair = "XBTUSD",
    type = "buy",
    orderType = "market",
    volume = "0.01"
)

// Limit sell order
val response = krakenApiService.addOrder(
    nonce = "${System.currentTimeMillis() * 1000}",
    pair = "XBTUSD",
    type = "sell",
    orderType = "limit",
    volume = "0.01",
    price = "50000.00"
)

// Validate order without submitting
val response = krakenApiService.addOrder(
    nonce = "${System.currentTimeMillis() * 1000}",
    pair = "XBTUSD",
    type = "buy",
    orderType = "market",
    volume = "0.01",
    validate = true
)
```

### 2. Cancel Order
**Endpoint:** `POST /0/private/CancelOrder`

**Function:**
```kotlin
suspend fun cancelOrder(
    nonce: String,
    txid: String
): Response<KrakenResponse<CancelOrderResponse>>
```

**Parameters:**
- `nonce` - Unique nonce
- `txid` - Transaction ID of order to cancel

**Response:**
```kotlin
CancelOrderResponse(
    count: Int,
    pending: Boolean?
)
```

**Example Usage:**
```kotlin
val response = krakenApiService.cancelOrder(
    nonce = "${System.currentTimeMillis() * 1000}",
    txid = "ORDER-TRANSACTION-ID"
)
```

### 3. Query Orders
**Endpoint:** `POST /0/private/QueryOrders`

**Function:**
```kotlin
suspend fun queryOrders(
    nonce: String,
    txid: String,
    trades: Boolean? = false,
    userref: String? = null
): Response<KrakenResponse<QueryOrdersData>>
```

**Parameters:**
- `nonce` - Unique nonce
- `txid` - Comma-delimited list of transaction IDs
- `trades` - Whether to include trades in output
- `userref` - Filter by user reference ID

**Response:**
Map of transaction ID to OrderInfo

**Example Usage:**
```kotlin
val response = krakenApiService.queryOrders(
    nonce = "${System.currentTimeMillis() * 1000}",
    txid = "ORDER-TX-ID-1,ORDER-TX-ID-2",
    trades = true
)

if (response.isSuccessful) {
    response.body()?.result?.forEach { (txid, orderInfo) ->
        println("Order $txid: ${orderInfo.status}")
    }
}
```

### 4. Open Orders
**Endpoint:** `POST /0/private/OpenOrders`

**Function:**
```kotlin
suspend fun openOrders(
    nonce: String,
    trades: Boolean? = false,
    userref: String? = null
): Response<KrakenResponse<OpenOrdersData>>
```

**Parameters:**
- `nonce` - Unique nonce
- `trades` - Whether to include trades
- `userref` - Filter by user reference ID

**Response:**
```kotlin
OpenOrdersData(
    open: Map<String, OrderInfo>
)
```

**Example Usage:**
```kotlin
val response = krakenApiService.openOrders(
    nonce = "${System.currentTimeMillis() * 1000}",
    trades = false
)

if (response.isSuccessful) {
    val openOrders = response.body()?.result?.open ?: emptyMap()
    openOrders.forEach { (txid, order) ->
        println("Open order: $txid - ${order.description?.pair} ${order.volume}")
    }
}
```

### 5. Closed Orders
**Endpoint:** `POST /0/private/ClosedOrders`

**Function:**
```kotlin
suspend fun closedOrders(
    nonce: String,
    trades: Boolean? = false,
    userref: String? = null,
    start: String? = null,
    end: String? = null,
    ofs: Int? = null,
    closetime: String? = null
): Response<KrakenResponse<ClosedOrdersData>>
```

**Parameters:**
- `nonce` - Unique nonce
- `trades` - Whether to include trades
- `userref` - Filter by user reference ID
- `start` - Starting timestamp or order tx id
- `end` - Ending timestamp or order tx id
- `ofs` - Result offset for pagination
- `closetime` - "open", "close", or "both" (default)

**Response:**
```kotlin
ClosedOrdersData(
    closed: Map<String, OrderInfo>,
    count: Int
)
```

**Example Usage:**
```kotlin
val response = krakenApiService.closedOrders(
    nonce = "${System.currentTimeMillis() * 1000}",
    trades = false,
    ofs = 0 // For pagination
)

if (response.isSuccessful) {
    val closedOrders = response.body()?.result?.closed ?: emptyMap()
    println("Total closed orders: ${response.body()?.result?.count}")
}
```

## Order Status Values
- `pending` - Order pending book entry
- `open` - Order open
- `closed` - Order closed
- `canceled` - Order canceled
- `expired` - Order expired

## Order Types
- `market` - Market order
- `limit` - Limit order
- `stop-loss` - Stop loss order
- `take-profit` - Take profit order
- `stop-loss-limit` - Stop loss limit order
- `take-profit-limit` - Take profit limit order

## Error Handling

All Kraken API responses include an `error` field:

```kotlin
if (response.isSuccessful) {
    val krakenResponse = response.body()
    if (krakenResponse?.error.isNullOrEmpty()) {
        // Success - use krakenResponse.result
        val result = krakenResponse?.result
    } else {
        // Kraken returned an error
        val errors = krakenResponse?.error ?: emptyList()
        errors.forEach { error ->
            Log.e("Kraken", "API Error: $error")
        }
    }
} else {
    // HTTP error
    Log.e("Kraken", "HTTP Error: ${response.code()}")
}
```

## Common Kraken Error Codes
- `EAPI:Invalid key` - Invalid API key
- `EAPI:Invalid signature` - Invalid API signature
- `EAPI:Invalid nonce` - Invalid or reused nonce
- `EGeneral:Invalid arguments` - Invalid parameters
- `EOrder:Insufficient funds` - Not enough balance
- `EOrder:Unknown order` - Order not found
- `EOrder:Rate limit exceeded` - Too many requests

## Rate Limiting

Kraken enforces rate limits:
- **Private API:** 15-20 calls per minute (varies by verification tier)
- **Counter decay:** Reduces by 1 every 3 seconds

**Best Practices:**
- Cache order data when possible
- Use batch queries (QueryOrders with multiple txids)
- Implement exponential backoff for retries
- Monitor rate limit headers in responses

## Authentication

All private endpoints require:
1. **API-Key header** - Your Kraken API key
2. **API-Sign header** - HMAC-SHA512 signature

The signature should be calculated using the existing authentication mechanism in the app. Make sure your Retrofit client includes the appropriate interceptor for adding these headers.

## Nonce Generation

Kraken requires a strictly increasing nonce for each request:

```kotlin
val nonce = "${System.currentTimeMillis() * 1000}"
```

**Important:** Never reuse a nonce, even for retry attempts.

## Testing

### Validation Mode
Use `validate = true` to test order parameters without actually placing the order:

```kotlin
val response = krakenApiService.addOrder(
    nonce = "${System.currentTimeMillis() * 1000}",
    pair = "XBTUSD",
    type = "buy",
    orderType = "limit",
    volume = "0.01",
    price = "50000.00",
    validate = true  // Won't actually place the order
)
```

## Next Steps

To use these endpoints in your app:

1. **Create a Repository** - Wrap API calls with proper error handling
2. **Create Use Cases** - Implement business logic for placing/canceling orders
3. **Update UI** - Create screens for order management
4. **Implement Real-time Updates** - Consider WebSocket integration for live order updates
5. **Add Local Caching** - Store order history locally for offline access

## References

- [Kraken REST API Documentation](https://docs.kraken.com/rest/)
- [Kraken API Support](https://support.kraken.com/hc/en-us/categories/360000080686-API)

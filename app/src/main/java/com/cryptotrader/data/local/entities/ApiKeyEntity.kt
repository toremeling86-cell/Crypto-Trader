package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val id: Int = 1,
    val publicKey: String,
    val privateKeyEncrypted: String, // Encrypted via Android Keystore
    val lastUsed: Long = System.currentTimeMillis(),
    val apiTier: String = "starter", // starter, intermediate, pro
    val createdAt: Long = System.currentTimeMillis()
)

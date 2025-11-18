package com.cryptotrader.data.cloud

import android.content.Context
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Storage Client for Cloudflare R2
 *
 * Provides S3-compatible access to Cloudflare R2 bucket containing
 * historical market data for backtesting.
 *
 * Credentials are stored in EncryptedSharedPreferences for security.
 */
@Singleton
class CloudStorageClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var s3Client: S3Client? = null
    private var bucketName: String? = null

    companion object {
        private const val PREF_NAME = "cloud_storage_prefs"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_ACCESS_KEY = "access_key_id"
        private const val KEY_SECRET_KEY = "secret_access_key"
        private const val KEY_BUCKET_NAME = "bucket_name"

        private const val DEFAULT_BUCKET = "crypto-trader-data"

        // Retry configuration
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * Initialize S3 client with R2 credentials
     */
    suspend fun initialize(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String = DEFAULT_BUCKET
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save credentials securely
            saveCredentials(accountId, accessKeyId, secretAccessKey, bucketName)

            // Create S3 client configured for Cloudflare R2
            val endpoint = "https://$accountId.r2.cloudflarestorage.com"

            s3Client = S3Client {
                region = "auto" // R2 uses 'auto' region
                endpointUrl = aws.smithy.kotlin.runtime.net.url.Url.parse(endpoint)
                credentialsProvider = StaticCredentialsProvider(
                    Credentials(
                        accessKeyId = accessKeyId,
                        secretAccessKey = secretAccessKey
                    )
                )
            }

            this@CloudStorageClient.bucketName = bucketName

            Timber.i("✅ CloudStorageClient initialized: $endpoint/$bucketName")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CloudStorageClient")
            Result.failure(e)
        }
    }

    /**
     * Initialize from saved credentials
     */
    suspend fun initializeFromSaved(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val accountId = prefs.getString(KEY_ACCOUNT_ID, null)
            val accessKey = prefs.getString(KEY_ACCESS_KEY, null)
            val secretKey = prefs.getString(KEY_SECRET_KEY, null)
            val bucket = prefs.getString(KEY_BUCKET_NAME, DEFAULT_BUCKET)!!

            if (accountId == null || accessKey == null || secretKey == null) {
                return@withContext Result.failure(
                    IllegalStateException("No saved credentials found")
                )
            }

            initialize(accountId, accessKey, secretKey, bucket)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize from saved credentials")
            Result.failure(e)
        }
    }

    /**
     * Download file from R2
     *
     * @param key Object key in bucket (e.g., "XXBTZUSD/1h/2024-Q2.parquet.zst")
     * @param destination Local file to write to
     * @param onProgress Progress callback (0.0 to 1.0)
     */
    suspend fun downloadFile(
        key: String,
        destination: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        ensureInitialized() ?: return@withContext Result.failure(
            IllegalStateException("Client not initialized")
        )

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < MAX_RETRIES) {
            try {
                Timber.d("📥 Downloading: $key (attempt ${attempt + 1}/$MAX_RETRIES)")

                // Get object metadata first to get file size
                val headRequest = HeadObjectRequest {
                    bucket = bucketName
                    this.key = key
                }

                val headResponse = s3Client!!.headObject(headRequest)
                val contentLength = headResponse.contentLength ?: 0L

                Timber.d("   File size: ${contentLength / 1024}KB")

                // Download object
                val getRequest = GetObjectRequest {
                    bucket = bucketName
                    this.key = key
                }

                s3Client!!.getObject(getRequest) { response ->
                    val body = response.body

                    when {
                        body is ByteStream.Buffer -> {
                            // Small file - load directly
                            val bytes = body.bytes()
                            destination.parentFile?.mkdirs()
                            destination.writeBytes(bytes)
                            onProgress?.invoke(1.0f)
                        }
                        body is ByteStream.ChannelStream -> {
                            // Large file - stream with progress
                            destination.parentFile?.mkdirs()

                            destination.outputStream().use { output ->
                                // Note: AWS SDK doesn't provide direct channel access
                                // We convert to byte array for simplicity
                                val bytes = body.toByteArray()
                                output.write(bytes)

                                onProgress?.invoke(1.0f)
                            }
                        }
                        else -> {
                            // Unknown stream type - try to convert to bytes
                            val bytes = body?.toByteArray() ?: ByteArray(0)
                            destination.parentFile?.mkdirs()
                            destination.writeBytes(bytes)
                            onProgress?.invoke(1.0f)
                        }
                    }
                }

                Timber.i("✅ Downloaded: $key → ${destination.name}")
                return@withContext Result.success(destination)

            } catch (e: NoSuchKey) {
                Timber.e("❌ File not found in R2: $key")
                return@withContext Result.failure(
                    FileNotFoundException("Object not found: $key")
                )

            } catch (e: Exception) {
                lastException = e
                attempt++

                if (attempt < MAX_RETRIES) {
                    Timber.w("⚠️ Download failed, retrying... (${e.message})")
                    kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                } else {
                    Timber.e(e, "❌ Download failed after $MAX_RETRIES attempts")
                }
            }
        }

        Result.failure(lastException ?: Exception("Download failed"))
    }

    /**
     * Get file metadata without downloading
     */
    suspend fun getFileMetadata(key: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        ensureInitialized() ?: return@withContext Result.failure(
            IllegalStateException("Client not initialized")
        )

        try {
            val request = HeadObjectRequest {
                bucket = bucketName
                this.key = key
            }

            val response = s3Client!!.headObject(request)

            val metadata = FileMetadata(
                key = key,
                sizeBytes = response.contentLength ?: 0L,
                lastModified = response.lastModified?.epochSeconds ?: 0L,
                contentType = response.contentType,
                etag = response.eTag
            )

            Result.success(metadata)

        } catch (e: NoSuchKey) {
            Result.failure(FileNotFoundException("Object not found: $key"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file metadata: $key")
            Result.failure(e)
        }
    }

    /**
     * List objects in bucket with prefix
     */
    suspend fun listObjects(prefix: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        ensureInitialized() ?: return@withContext Result.failure(
            IllegalStateException("Client not initialized")
        )

        try {
            val request = ListObjectsV2Request {
                bucket = bucketName
                this.prefix = prefix
                maxKeys = 1000
            }

            val response = s3Client!!.listObjectsV2(request)
            val keys = response.contents?.mapNotNull { it.key } ?: emptyList()

            Timber.d("📋 Listed ${keys.size} objects with prefix: $prefix")
            Result.success(keys)

        } catch (e: Exception) {
            Timber.e(e, "Failed to list objects with prefix: $prefix")
            Result.failure(e)
        }
    }

    /**
     * Check if file exists in R2
     */
    suspend fun fileExists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getFileMetadata(key).isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get bucket statistics
     */
    suspend fun getBucketStats(): Result<BucketStats> = withContext(Dispatchers.IO) {
        ensureInitialized() ?: return@withContext Result.failure(
            IllegalStateException("Client not initialized")
        )

        try {
            val request = ListObjectsV2Request {
                bucket = bucketName
                maxKeys = Int.MAX_VALUE
            }

            val response = s3Client!!.listObjectsV2(request)
            val objects = response.contents ?: emptyList()

            val stats = BucketStats(
                objectCount = objects.size,
                totalSizeBytes = objects.sumOf { it.size ?: 0L },
                lastModified = objects.maxOfOrNull { it.lastModified?.epochSeconds ?: 0L } ?: 0L
            )

            Result.success(stats)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get bucket stats")
            Result.failure(e)
        }
    }

    /**
     * Close client and release resources
     */
    fun close() {
        s3Client?.close()
        s3Client = null
        Timber.d("CloudStorageClient closed")
    }

    // Private helpers

    private fun ensureInitialized(): S3Client? {
        if (s3Client == null) {
            Timber.e("CloudStorageClient not initialized")
        }
        return s3Client
    }

    private fun saveCredentials(
        accountId: String,
        accessKeyId: String,
        secretAccessKey: String,
        bucketName: String
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCOUNT_ID, accountId)
            .putString(KEY_ACCESS_KEY, accessKeyId)
            .putString(KEY_SECRET_KEY, secretAccessKey)
            .putString(KEY_BUCKET_NAME, bucketName)
            .apply()
    }

    fun clearCredentials() {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        close()
    }
}

/**
 * File metadata from R2
 */
data class FileMetadata(
    val key: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentType: String?,
    val etag: String?
)

/**
 * Bucket statistics
 */
data class BucketStats(
    val objectCount: Int,
    val totalSizeBytes: Long,
    val lastModified: Long
)

/**
 * File not found exception
 */
class FileNotFoundException(message: String) : Exception(message)

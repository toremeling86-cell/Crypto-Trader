package com.cryptotrader.di

import android.content.Context
import com.cryptotrader.BuildConfig
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.KrakenAuthInterceptor
import com.cryptotrader.data.remote.kraken.KrakenWebSocketClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KrakenRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // SECURITY: Use HEADERS instead of BODY to avoid logging sensitive request/response data
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideSecureLoggingInterceptor(): com.cryptotrader.utils.SecureLoggingInterceptor {
        return com.cryptotrader.utils.SecureLoggingInterceptor(
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        )
    }

    @Provides
    @Singleton
    fun provideKrakenAuthInterceptor(
        @ApplicationContext context: Context
    ): KrakenAuthInterceptor {
        return KrakenAuthInterceptor(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        secureLoggingInterceptor: com.cryptotrader.utils.SecureLoggingInterceptor,
        krakenAuthInterceptor: KrakenAuthInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(secureLoggingInterceptor) // SECURITY: Use secure logging that redacts sensitive data
            .addInterceptor(krakenAuthInterceptor)
            // Optimized timeouts for production use
            .connectTimeout(15, TimeUnit.SECONDS) // Time to establish connection
            .readTimeout(30, TimeUnit.SECONDS)    // Time to read response
            .writeTimeout(15, TimeUnit.SECONDS)   // Time to write request
            .callTimeout(45, TimeUnit.SECONDS)    // Total time for complete call

        // SECURITY: Enable certificate pinning in production
        if (com.cryptotrader.utils.CertificatePinnerConfig.isEnabled()) {
            builder.certificatePinner(com.cryptotrader.utils.CertificatePinnerConfig.build())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @KrakenRetrofit
    fun provideKrakenRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.KRAKEN_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @ClaudeRetrofit
    fun provideClaudeRetrofit(
        secureLoggingInterceptor: com.cryptotrader.utils.SecureLoggingInterceptor,
        moshi: Moshi
    ): Retrofit {
        val claudeClientBuilder = OkHttpClient.Builder()
            .addInterceptor(secureLoggingInterceptor) // SECURITY: Use secure logging
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        // SECURITY: Enable certificate pinning in production
        if (com.cryptotrader.utils.CertificatePinnerConfig.isEnabled()) {
            claudeClientBuilder.certificatePinner(com.cryptotrader.utils.CertificatePinnerConfig.build())
        }

        return Retrofit.Builder()
            .baseUrl(BuildConfig.CLAUDE_API_BASE_URL)
            .client(claudeClientBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideKrakenApiService(
        @KrakenRetrofit retrofit: Retrofit
    ): KrakenApiService {
        return retrofit.create(KrakenApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideClaudeApiService(
        @ClaudeRetrofit retrofit: Retrofit
    ): ClaudeApiService {
        return retrofit.create(ClaudeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideKrakenWebSocketClient(
        okHttpClient: OkHttpClient
    ): KrakenWebSocketClient {
        return KrakenWebSocketClient(
            okHttpClient = okHttpClient,
            wsUrl = BuildConfig.KRAKEN_WS_URL
        )
    }
}

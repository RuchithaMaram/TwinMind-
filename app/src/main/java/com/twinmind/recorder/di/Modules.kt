package com.twinmind.recorder.di

import android.content.Context
import androidx.room.Room
import com.twinmind.recorder.data.local.AppDatabase
import com.twinmind.recorder.data.local.dao.AudioChunkDao
import com.twinmind.recorder.data.local.dao.MeetingDao
import com.twinmind.recorder.data.local.dao.TranscriptDao
import com.twinmind.recorder.data.remote.GeminiApiService
import com.twinmind.recorder.data.remote.WhisperApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "twinmind.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMeetingDao(db: AppDatabase): MeetingDao = db.meetingDao()
    @Provides fun provideChunkDao(db: AppDatabase): AudioChunkDao = db.audioChunkDao()
    @Provides fun provideTranscriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun loadApiKey(keyName: String): String {
        return try {
            val properties = Properties()
            val localPropertiesFile = File("local.properties")
            if (localPropertiesFile.exists()) {
                properties.load(localPropertiesFile.inputStream())
                (properties.getProperty(keyName) ?: "").trim().removeSurrounding("\"")
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @Provides
    @Singleton
    @Named("whisper")
    fun provideWhisperOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val openaiKey = loadApiKey("OPEN_API_KEY")
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $openaiKey")
                    .build()
            )
        })
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideWhisperApi(@Named("whisper") client: OkHttpClient): WhisperApiService =
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhisperApiService::class.java)

    @Provides
    @Singleton
    fun provideGeminiOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideGeminiApi(client: OkHttpClient): GeminiApiService =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
}

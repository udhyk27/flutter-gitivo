package com.ydh.salvio.data.api

import com.ydh.salvio.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "https://api.github.com/"

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /**
     * 앱 전역에서 공유하는 OkHttpClient.
     * 커넥션 풀·스레드 풀·디스패처를 재사용하므로, 호출마다 새 클라이언트를 만들던
     * 기존 방식의 자원 낭비를 없앤다. 토큰별 클라이언트는 `newBuilder()`로 파생시켜
     * 이 공유 자원을 그대로 물려받는다.
     */
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** 토큰별 GitHubApi 캐시 — 동일 토큰이면 Retrofit/Api 인스턴스를 재사용한다. */
    private val apiCache = HashMap<String, GitHubApi>()

    /** 로그아웃 시 토큰별 API 캐시를 비운다 (이전 사용자 클라이언트 잔존 방지). */
    fun clearCache() = synchronized(apiCache) { apiCache.clear() }

    fun create(token: String): GitHubApi = synchronized(apiCache) {
        apiCache.getOrPut(token) {
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                chain.proceed(request)
            }

            val client = sharedClient.newBuilder()
                .addInterceptor(authInterceptor)
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GitHubApi::class.java)
        }
    }

    /** OAuth Device Flow 용 클라이언트 (base URL 이 github.com, 인증 헤더 없음) */
    fun createOAuth(): GitHubOAuthApi {
        val client = sharedClient.newBuilder().build()

        return Retrofit.Builder()
            .baseUrl("https://github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubOAuthApi::class.java)
    }
}

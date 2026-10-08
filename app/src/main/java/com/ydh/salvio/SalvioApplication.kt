package com.ydh.salvio

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ydh.salvio.data.api.RetrofitClient
import com.ydh.salvio.data.local.AppDatabase
import com.ydh.salvio.data.local.TokenDataStore
import com.ydh.salvio.data.repository.GitHubRepository
import com.ydh.salvio.data.worker.PrCheckWorker
import com.ydh.salvio.data.worker.RepoSyncWorker
import java.util.concurrent.TimeUnit

class SalvioApplication : Application() {
    lateinit var tokenDataStore: TokenDataStore
    lateinit var database: AppDatabase

    private var cachedRepository: Pair<String, GitHubRepository>? = null

    /**
     * 토큰에 대응하는 GitHubRepository를 반환한다.
     * 동일 토큰이면 캐시된 인스턴스를 재사용하므로, 각 ViewModel이 호출마다
     * Repository를 새로 생성하던 중복을 없앤다.
     */
    @Synchronized
    fun githubRepository(token: String): GitHubRepository {
        cachedRepository?.let { (t, repo) -> if (t == token) return repo }
        return GitHubRepository(RetrofitClient.create(token), database.cacheDao())
            .also { cachedRepository = token to it }
    }

    /**
     * 로그아웃 시 메모리에 남은 인증 클라이언트/리포지토리 캐시를 비운다.
     * (Room 캐시 정리는 호출 측에서 database.clearAllTables() 로 백그라운드에서 수행)
     */
    @Synchronized
    fun clearSession() {
        cachedRepository = null
        RetrofitClient.clearCache()
    }

    override fun onCreate() {
        super.onCreate()
        tokenDataStore = TokenDataStore(this)
        database = AppDatabase.getInstance(this)
        PrCheckWorker.ensureChannel(this)
        scheduleRepoSync()
    }

    /**
     * 주기적인 저장소 동기화를 스케줄한다.
     * 사용자 설정으로 비활성화 가능하게 만들 수 있다.
     */
    private fun scheduleRepoSync() {
        val syncRequest = PeriodicWorkRequestBuilder<RepoSyncWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RepoSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

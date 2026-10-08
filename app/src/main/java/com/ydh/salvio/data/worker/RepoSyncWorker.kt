package com.ydh.salvio.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.data.local.TokenDataStore
import com.ydh.salvio.util.Logger
import kotlinx.coroutines.flow.first

/**
 * 주기적으로 저장소 목록을 백그라운드에서 동기화하는 Worker.
 * WorkManager를 통해 스케줄되며, 네트워크가 없거나 토큰이 없으면 재시도한다.
 */
class RepoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as SalvioApplication
            val dataStore = app.tokenDataStore
            val token = dataStore.token.first()

            if (token.isNullOrBlank()) {
                Logger.d("RepoSyncWorker: No token found, skipping sync")
                return Result.retry()
            }

            val repo = app.githubRepository(token)
            repo.getUserRepos(forceRefresh = true).fold(
                onSuccess = {
                    Logger.i("RepoSyncWorker: Synced ${it.size} repos")
                    Result.success()
                },
                onFailure = { e ->
                    Logger.e("RepoSyncWorker failed", e)
                    // 네트워크 오류면 재시도, 인증 오류면 성공 처리
                    if (e.message?.contains("Network") == true) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            Logger.e("RepoSyncWorker exception", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "repo_sync"
    }
}

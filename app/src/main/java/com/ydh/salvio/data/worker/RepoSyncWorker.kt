package com.ydh.salvio.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.util.Logger
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * 주기적으로 저장소 목록을 백그라운드에서 갱신한다.
 * 토큰이 없으면 할 일이 없으므로 성공으로 끝내고, 네트워크 오류만 재시도한다.
 */
class RepoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SalvioApplication
        val token = app.tokenDataStore.token.first()
        if (token.isNullOrBlank()) return Result.success()

        return app.githubRepository(token).getUserRepos(forceRefresh = true).fold(
            onSuccess = {
                Logger.i("RepoSyncWorker: synced ${it.size} repos")
                Result.success()
            },
            onFailure = { e ->
                Logger.e("RepoSyncWorker failed", e)
                if (e is IOException) Result.retry() else Result.failure()
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "repo_sync"
    }
}

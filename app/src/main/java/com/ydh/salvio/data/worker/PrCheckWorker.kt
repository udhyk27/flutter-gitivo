package com.ydh.salvio.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.data.local.TokenDataStore
import com.ydh.salvio.util.Logger
import com.ydh.salvio.util.httpCode
import com.ydh.salvio.util.isAuthExpired
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class PrCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dataStore = TokenDataStore(context)
        val token = dataStore.token.first() ?: return Result.success()
        val watchedRepos = dataStore.watchedRepos.first()
        if (watchedRepos.isEmpty()) return Result.success()

        val repo = (context.applicationContext as SalvioApplication).githubRepository(token)

        var anyFailed = false
        watchedRepos.forEach { fullName ->
            val parts = fullName.split("/")
            if (parts.size != 2) return@forEach
            val (owner, repoName) = parts

            // 백그라운드 감시는 항상 최신 데이터를 가져와야 한다 (캐시 사용 시 새 PR 누락).
            val prs = repo.getPullRequests(owner, repoName, "open", forceRefresh = true).getOrElse { e ->
                when {
                    // 토큰이 폐기됐으면 재시도해도 소용없으므로 중단한다.
                    e.isAuthExpired() -> {
                        Logger.authFailed("PR 감시 중단: 토큰 만료")
                        return Result.failure()
                    }
                    // 저장소가 삭제되었거나 이름이 바뀐 경우 이 저장소만 건너뛴다.
                    e.httpCode() == 404 -> {
                        Logger.w("PR 감시 건너뜀: $fullName 을 찾을 수 없습니다.")
                        return@forEach
                    }
                    else -> {
                        Logger.e("PR 감시 실패: $fullName", e)
                        anyFailed = true
                        return@forEach
                    }
                }
            }

            val currentNumbers = prs.map { it.number }.toSet()
            val lastNumbers = dataStore.getLastPrNumbers(fullName)
            // 총 개수가 같아도(열림 1 + 닫힘 1) 집합 차이로 새 PR을 감지한다.
            val newNumbers = currentNumbers - lastNumbers

            if (newNumbers.isNotEmpty()) {
                sendNotification(
                    id = fullName.hashCode(),
                    title = "새 PR - $repoName",
                    body = "${newNumbers.size}개의 새 Pull Request가 열렸습니다."
                )
            }
            dataStore.saveLastPrNumbers(fullName, currentNumbers)
        }

        // 일시적 네트워크 오류로 일부 조회에 실패했으면 재시도를 요청한다.
        return if (anyFailed) Result.retry() else Result.success()
    }

    private fun sendNotification(id: Int, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // repo 별 안정적 ID → 같은 저장소 알림은 쌓이지 않고 갱신된다.
        manager.notify(id, notification)
    }

    companion object {
        const val WORK_NAME = "pr_check_work"
        const val CHANNEL_ID = "salvio_pr_channel"

        /** PR 알림 채널 생성 (앱 시작 시 한 번 호출). */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "PR 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "새로운 Pull Request 알림"
            }
            manager.createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

package com.ydh.salvio.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ydh.salvio.data.api.GitHubApi
import com.ydh.salvio.data.local.*
import com.ydh.salvio.data.model.*
import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException

class GitHubRepository(
    private val api: GitHubApi,
    private val dao: CacheDao? = null
) {
    private val cacheMaxAgeMs = 5 * 60 * 1000L // 기본: 5분
    private val shortCacheMs = 3 * 60 * 1000L // PR/이슈(변경 빈번): 3분
    private val longCacheMs = 30 * 60 * 1000L // 통계/기여자(변경 드문): 30분
    private val statsTrafficCacheMs = 60 * 60 * 1000L // 트래픽(변경 매우 드문): 1시간

    private fun isExpired(cachedAt: Long, maxAgeMs: Long = cacheMaxAgeMs) =
        System.currentTimeMillis() - cachedAt > maxAgeMs

    /** Return stale cached data when a refresh fails, without refreshing its timestamp. */
    private suspend fun <T : Any> fetchOrStale(
        staleValue: T?,
        fetch: suspend () -> T,
        save: suspend (T) -> Unit
    ): T {
        val fresh = try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            return staleValue ?: throw error
        }
        save(fresh)
        return fresh
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    // 마지막 페이지 판정은 응답 개수 기준(필터링 전). 과도한 요청을 막기 위해 최대 페이지 수를 둔다.
    private suspend fun <T> fetchAllPages(fetch: suspend (page: Int) -> List<T>): List<T> {
        val all = mutableListOf<T>()
        for (page in 1..MAX_PAGES) {
            val batch = fetch(page)
            all += batch
            if (batch.size < PAGE_SIZE) break
        }
        return all
    }

    suspend fun getUser(): Result<GitHubUser> = resultOf { api.getAuthenticatedUser() }

    suspend fun getUserRepos(forceRefresh: Boolean = false): Result<List<GitHubRepo>> = resultOf {
        val cached = dao?.getAllRepos()
        val cachedRepos = cached?.takeIf { it.isNotEmpty() }?.map {
            gson.fromJson(it.json, GitHubRepo::class.java)
        }
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            return@resultOf cachedRepos.orEmpty()
        }
        fetchOrStale(
            cachedRepos,
            fetch = { fetchAllPages { page -> api.getUserRepos(perPage = PAGE_SIZE, page = page) } },
            save = { repos -> dao?.insertRepos(repos.map { CachedRepo(it.fullName, gson.toJson(it)) }) }
        )
    }

    suspend fun getCommits(
        owner: String,
        repo: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<List<GitHubCommit>> = resultOf {
        val repoFullName = "$owner/$repo"
        if (page == 1) {
            val cached = dao?.getCommits(repoFullName)
            val cachedCommits = cached?.takeIf { it.isNotEmpty() }?.map {
                gson.fromJson<GitHubCommit>(it.json, object : TypeToken<GitHubCommit>() {}.type)
            }
            if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
                return@resultOf cachedCommits.orEmpty()
            }
            return@resultOf fetchOrStale(
                cachedCommits,
                fetch = { api.getCommits(owner, repo, perPage = 100, page = page) },
                save = { commits ->
                    dao?.deleteCommits(repoFullName)
                    dao?.insertCommits(commits.map { CachedCommit("$repoFullName:${it.sha}", repoFullName, gson.toJson(it)) })
                }
            )
        }
        api.getCommits(owner, repo, perPage = 100, page = page)
    }

    suspend fun getPullRequests(
        owner: String,
        repo: String,
        state: String = "open",
        forceRefresh: Boolean = false
    ): Result<List<GitHubPullRequest>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrs(repoFullName, state)
        val type = object : TypeToken<GitHubPullRequest>() {}.type
        val cachedPrs = cached?.takeIf { it.isNotEmpty() }?.map { gson.fromJson<GitHubPullRequest>(it.json, type) }
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt, shortCacheMs)) {
            return@resultOf cachedPrs.orEmpty()
        }
        fetchOrStale(
            cachedPrs,
            fetch = { fetchAllPages { page -> api.getPullRequests(owner, repo, state = state, perPage = PAGE_SIZE, page = page) } },
            save = { prs ->
                dao?.deletePrs(repoFullName, state)
                dao?.insertPrs(prs.map { CachedPr("$repoFullName:${it.number}:$state", repoFullName, state, gson.toJson(it)) })
            }
        )
    }

    suspend fun getBranches(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<GitHubBranch>> =
        resultOf {
            val repoFullName = "$owner/$repo"
            val cached = dao?.getBranches(repoFullName)
            val type = object : TypeToken<GitHubBranch>() {}.type
            val cachedBranches = cached?.takeIf { it.isNotEmpty() }?.map { gson.fromJson<GitHubBranch>(it.json, type) }
            if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
                return@resultOf cachedBranches.orEmpty()
            }
            fetchOrStale(
                cachedBranches,
                fetch = { fetchAllPages { page -> api.getBranches(owner, repo, perPage = PAGE_SIZE, page = page) } },
                save = { branches ->
                    dao?.deleteBranches(repoFullName)
                    dao?.insertBranches(branches.map { CachedBranch("$repoFullName:${it.name}", repoFullName, gson.toJson(it)) })
                }
            )
        }

    suspend fun getContributors(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<GitHubContributor>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getContributors(repoFullName)
        val type = object : TypeToken<List<GitHubContributor>>() {}.type
        val cachedContributors = cached?.let { gson.fromJson<List<GitHubContributor>>(it.json, type) }
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, longCacheMs)) {
            return@resultOf cachedContributors.orEmpty()
        }
        fetchOrStale(
            cachedContributors,
            fetch = { fetchAllPages { page -> api.getContributors(owner, repo, perPage = PAGE_SIZE, page = page) } },
            save = { contributors -> dao?.insertContributors(CachedContributors(repoFullName, gson.toJson(contributors))) }
        )
    }

    suspend fun getRepo(owner: String, repo: String, forceRefresh: Boolean = false): Result<GitHubRepo> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getRepo(repoFullName)
        val cachedRepo = cached?.let { gson.fromJson(it.json, GitHubRepo::class.java) }
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt) && cachedRepo != null) {
            return@resultOf cachedRepo
        }
        fetchOrStale(
            cachedRepo,
            fetch = { api.getRepo(owner, repo) },
            save = { result -> dao?.insertRepo(CachedRepo(result.fullName, gson.toJson(result))) }
        )
    }

    suspend fun getBranchLatestCommit(owner: String, repo: String, ref: String): Result<GitHubCommit> =
        resultOf { api.getBranchLatestCommit(owner, repo, ref) }

    suspend fun getCommitDetail(owner: String, repo: String, sha: String): Result<GitHubCommitDetail> =
        resultOf { api.getCommitDetail(owner, repo, sha) }

    suspend fun getPrReviews(
        owner: String,
        repo: String,
        prNumber: Int,
        forceRefresh: Boolean = false
    ): Result<List<GitHubPrReview>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrReviews(repoFullName, prNumber)
        val type = object : TypeToken<GitHubPrReview>() {}.type
        val cachedReviews = cached?.takeIf { it.isNotEmpty() }?.map { gson.fromJson<GitHubPrReview>(it.json, type) }
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            return@resultOf cachedReviews.orEmpty()
        }
        fetchOrStale(
            cachedReviews,
            fetch = { fetchAllPages { page -> api.getPrReviews(owner, repo, prNumber, perPage = PAGE_SIZE, page = page) } },
            save = { reviews ->
                dao?.deletePrReviews(repoFullName, prNumber)
                dao?.insertPrReviews(reviews.map {
                    CachedPrReview("$repoFullName:$prNumber:${it.id}", repoFullName, prNumber, gson.toJson(it))
                })
            }
        )
    }

    suspend fun getIssues(
        owner: String,
        repo: String,
        state: String = "open",
        forceRefresh: Boolean = false
    ): Result<List<GitHubIssue>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getIssues(repoFullName, state)
        val type = object : TypeToken<GitHubIssue>() {}.type
        val cachedIssues = cached?.takeIf { it.isNotEmpty() }?.map { gson.fromJson<GitHubIssue>(it.json, type) }
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt, shortCacheMs)) {
            return@resultOf cachedIssues.orEmpty()
        }
        fetchOrStale(
            cachedIssues,
            fetch = { fetchAllPages { page -> api.getIssues(owner, repo, state, perPage = PAGE_SIZE, page = page) }.filter { it.pullRequest == null } },
            save = { issues ->
                dao?.deleteIssues(repoFullName, state)
                dao?.insertIssues(issues.map { CachedIssue("$repoFullName:${it.number}:$state", repoFullName, state, gson.toJson(it)) })
            }
        )
    }

    suspend fun getReleases(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<GitHubRelease>> =
        resultOf {
            val repoFullName = "$owner/$repo"
            val cached = dao?.getReleases(repoFullName)
            val type = object : TypeToken<GitHubRelease>() {}.type
            val cachedReleases = cached?.takeIf { it.isNotEmpty() }?.map { gson.fromJson<GitHubRelease>(it.json, type) }
            if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
                return@resultOf cachedReleases.orEmpty()
            }
            fetchOrStale(
                cachedReleases,
                fetch = { fetchAllPages { page -> api.getReleases(owner, repo, perPage = PAGE_SIZE, page = page) } },
                save = { releases ->
                    dao?.deleteReleases(repoFullName)
                    dao?.insertReleases(releases.map { CachedRelease("$repoFullName:${it.id}", repoFullName, gson.toJson(it)) })
                }
            )
        }

    suspend fun getPrFiles(owner: String, repo: String, prNumber: Int): Result<List<PullRequestFile>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrFiles(repoFullName, prNumber)
        val type = object : TypeToken<List<PullRequestFile>>() {}.type
        val cachedFiles = cached?.let { gson.fromJson<List<PullRequestFile>>(it.json, type) }
        if (cached != null && !isExpired(cached.cachedAt)) {
            return@resultOf cachedFiles.orEmpty()
        }
        fetchOrStale(
            cachedFiles,
            fetch = { fetchAllPages { page -> api.getPrFiles(owner, repo, prNumber, perPage = PAGE_SIZE, page = page) } },
            save = { files -> dao?.insertPrFiles(CachedPrFiles("$repoFullName:$prNumber", repoFullName, prNumber, gson.toJson(files))) }
        )
    }

    suspend fun getTrafficViews(owner: String, repo: String, forceRefresh: Boolean = false): Result<TrafficViews> =
        resultOf {
            val repoFullName = "$owner/$repo"
            val cached = dao?.getTrafficViews(repoFullName)
            val cachedValue = cached?.let { gson.fromJson(it.json, TrafficViews::class.java) }
            if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
                return@resultOf cachedValue ?: throw IllegalStateException("Cached traffic views are invalid")
            }
            fetchOrStale(
                cachedValue,
                fetch = { api.getTrafficViews(owner, repo) },
                save = { dao?.insertTrafficViews(CachedTrafficViews(repoFullName, gson.toJson(it))) }
            )
        }

    suspend fun getTrafficClones(owner: String, repo: String, forceRefresh: Boolean = false): Result<TrafficClones> =
        resultOf {
            val repoFullName = "$owner/$repo"
            val cached = dao?.getTrafficClones(repoFullName)
            val cachedValue = cached?.let { gson.fromJson(it.json, TrafficClones::class.java) }
            if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
                return@resultOf cachedValue ?: throw IllegalStateException("Cached traffic clones are invalid")
            }
            fetchOrStale(
                cachedValue,
                fetch = { api.getTrafficClones(owner, repo) },
                save = { dao?.insertTrafficClones(CachedTrafficClones(repoFullName, gson.toJson(it))) }
            )
        }

    suspend fun getContributorStats(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<ContributorWeeklyStats>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getContributorStats(repoFullName)
        val type = object : TypeToken<List<ContributorWeeklyStats>>() {}.type
        val cachedValue = cached?.let { gson.fromJson<List<ContributorWeeklyStats>>(it.json, type) }
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
            return@resultOf cachedValue.orEmpty()
        }
        fetchOrStale(
            cachedValue,
            fetch = { api.getContributorStats(owner, repo) },
            save = { if (it.isNotEmpty()) dao?.insertContributorStats(CachedContributorStats(repoFullName, gson.toJson(it))) }
        )
    }

    suspend fun getNotifications(): Result<List<GitHubNotification>> = resultOf {
        api.getNotifications()
    }

    suspend fun searchCode(owner: String, repo: String, query: String): Result<CodeSearchResponse> = resultOf {
        api.searchCode("$query+repo:$owner/$repo")
    }

    suspend fun markNotificationRead(threadId: String): Result<Unit> = resultOf {
        val response = api.markNotificationRead(threadId)
        if (!response.isSuccessful) throw HttpException(response)
        Unit
    }

    suspend fun getCheckRuns(
        owner: String,
        repo: String,
        ref: String,
        forceRefresh: Boolean = false
    ): Result<CheckRunsResponse> = resultOf {
        val repoFullName = "$owner/$repo"
        val cacheId = "$repoFullName:$ref"
        val cached = dao?.getCheckRuns(cacheId)
        val cachedValue = cached?.let { gson.fromJson(it.json, CheckRunsResponse::class.java) }
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt)) {
            return@resultOf cachedValue ?: throw IllegalStateException("Cached check runs are invalid")
        }
        fetchOrStale(
            cachedValue,
            fetch = { api.getCheckRuns(owner, repo, ref) },
            save = { dao?.insertCheckRuns(CachedCheckRuns(cacheId, repoFullName, ref, gson.toJson(it))) }
        )
    }

    companion object {
        private val gson = Gson()
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
    }

    suspend fun getCommitActivity(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<List<CommitWeekActivity>> = resultOf {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getCommitActivity(repoFullName)
        val type = object : TypeToken<List<CommitWeekActivity>>() {}.type
        val cachedValue = cached?.let { gson.fromJson<List<CommitWeekActivity>>(it.json, type) }
        // 커밋 활동은 1시간 캐싱
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.cachedAt < 60 * 60 * 1000L) {
            return@resultOf cachedValue.orEmpty()
        }
        fetchOrStale(
            cachedValue,
            fetch = { api.getCommitActivity(owner, repo) },
            save = { dao?.insertCommitActivity(CachedCommitActivity(repoFullName, gson.toJson(it))) }
        )
    }
}

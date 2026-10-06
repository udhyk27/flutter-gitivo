package com.ydh.salvio.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ydh.salvio.data.api.GitHubApi
import com.ydh.salvio.data.local.*
import com.ydh.salvio.data.model.*
import com.ydh.salvio.util.Logger

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

    suspend fun getUser(): Result<GitHubUser> = runCatching { api.getAuthenticatedUser() }

    suspend fun getUserRepos(forceRefresh: Boolean = false): Result<List<GitHubRepo>> = runCatching {
        val cached = dao?.getAllRepos()
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            return@runCatching cached.map { gson.fromJson(it.json, GitHubRepo::class.java) }
        }
        val repos = api.getUserRepos(perPage = 100)
        dao?.insertRepos(repos.map { CachedRepo(it.fullName, gson.toJson(it)) })
        repos
    }

    suspend fun getCommits(owner: String, repo: String, page: Int = 1, forceRefresh: Boolean = false): Result<List<GitHubCommit>> = runCatching {
        val repoFullName = "$owner/$repo"
        if (page == 1) {
            val cached = dao?.getCommits(repoFullName)
            if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
                val type = object : TypeToken<GitHubCommit>() {}.type
                return@runCatching cached.map { gson.fromJson(it.json, type) }
            }
        }
        val commits = api.getCommits(owner, repo, perPage = 100, page = page)
        if (page == 1) {
            dao?.deleteCommits(repoFullName)
            dao?.insertCommits(commits.map { CachedCommit("$repoFullName:${it.sha}", repoFullName, gson.toJson(it)) })
        }
        commits
    }

    suspend fun getPullRequests(owner: String, repo: String, state: String = "open", forceRefresh: Boolean = false): Result<List<GitHubPullRequest>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrs(repoFullName, state)
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt, shortCacheMs)) {
            val type = object : TypeToken<GitHubPullRequest>() {}.type
            return@runCatching cached.map { gson.fromJson(it.json, type) }
        }
        val prs = api.getPullRequests(owner, repo, state = state, perPage = 100)
        dao?.deletePrs(repoFullName, state)
        dao?.insertPrs(prs.map { CachedPr("$repoFullName:${it.number}:$state", repoFullName, state, gson.toJson(it)) })
        prs
    }

    suspend fun getBranches(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<GitHubBranch>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getBranches(repoFullName)
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            val type = object : TypeToken<GitHubBranch>() {}.type
            return@runCatching cached.map { gson.fromJson(it.json, type) }
        }
        val branches = api.getBranches(owner, repo)
        dao?.deleteBranches(repoFullName)
        dao?.insertBranches(branches.map { CachedBranch("$repoFullName:${it.name}", repoFullName, gson.toJson(it)) })
        branches
    }

    suspend fun getContributors(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<GitHubContributor>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getContributors(repoFullName)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, longCacheMs)) {
            val type = object : TypeToken<List<GitHubContributor>>() {}.type
            return@runCatching gson.fromJson(cached.json, type)
        }
        val contributors = api.getContributors(owner, repo)
        dao?.insertContributors(CachedContributors(repoFullName, gson.toJson(contributors)))
        contributors
    }

    suspend fun getRepo(owner: String, repo: String, forceRefresh: Boolean = false): Result<GitHubRepo> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getRepo(repoFullName)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt)) {
            return@runCatching gson.fromJson(cached.json, GitHubRepo::class.java)
        }
        val result = api.getRepo(owner, repo)
        dao?.insertRepo(CachedRepo(result.fullName, gson.toJson(result)))
        result
    }

    suspend fun getBranchLatestCommit(owner: String, repo: String, ref: String): Result<GitHubCommit> =
        runCatching { api.getBranchLatestCommit(owner, repo, ref) }

    suspend fun getCommitDetail(owner: String, repo: String, sha: String): Result<GitHubCommitDetail> =
        runCatching { api.getCommitDetail(owner, repo, sha) }

    suspend fun getPrReviews(owner: String, repo: String, prNumber: Int, forceRefresh: Boolean = false): Result<List<GitHubPrReview>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrReviews(repoFullName, prNumber)
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            val type = object : TypeToken<GitHubPrReview>() {}.type
            return@runCatching cached.map { gson.fromJson(it.json, type) }
        }
        val reviews = api.getPrReviews(owner, repo, prNumber)
        dao?.deletePrReviews(repoFullName, prNumber)
        dao?.insertPrReviews(reviews.map { CachedPrReview("$repoFullName:$prNumber:${it.id}", repoFullName, prNumber, gson.toJson(it)) })
        reviews
    }

    suspend fun getIssues(owner: String, repo: String, state: String = "open", forceRefresh: Boolean = false): Result<List<GitHubIssue>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getIssues(repoFullName, state)
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt, shortCacheMs)) {
            val type = object : TypeToken<GitHubIssue>() {}.type
            return@runCatching cached.map { gson.fromJson(it.json, type) }
        }
        val issues = api.getIssues(owner, repo, state).filter { it.pullRequest == null }
        dao?.deleteIssues(repoFullName, state)
        dao?.insertIssues(issues.map { CachedIssue("$repoFullName:${it.number}:$state", repoFullName, state, gson.toJson(it)) })
        issues
    }

    suspend fun getReleases(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<GitHubRelease>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getReleases(repoFullName)
        if (!forceRefresh && !cached.isNullOrEmpty() && !isExpired(cached.first().cachedAt)) {
            val type = object : TypeToken<GitHubRelease>() {}.type
            return@runCatching cached.map { gson.fromJson(it.json, type) }
        }
        val releases = api.getReleases(owner, repo)
        dao?.deleteReleases(repoFullName)
        dao?.insertReleases(releases.map { CachedRelease("$repoFullName:${it.id}", repoFullName, gson.toJson(it)) })
        releases
    }

    suspend fun getPrFiles(owner: String, repo: String, prNumber: Int): Result<List<PullRequestFile>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getPrFiles(repoFullName, prNumber)
        if (cached != null && !isExpired(cached.cachedAt)) {
            val type = object : TypeToken<List<PullRequestFile>>() {}.type
            return@runCatching gson.fromJson(cached.json, type)
        }
        val files = api.getPrFiles(owner, repo, prNumber)
        dao?.insertPrFiles(CachedPrFiles("$repoFullName:$prNumber", repoFullName, prNumber, gson.toJson(files)))
        files
    }

    suspend fun getTrafficViews(owner: String, repo: String, forceRefresh: Boolean = false): Result<TrafficViews> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getTrafficViews(repoFullName)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
            return@runCatching gson.fromJson(cached.json, TrafficViews::class.java)
        }
        val result = api.getTrafficViews(owner, repo)
        dao?.insertTrafficViews(CachedTrafficViews(repoFullName, gson.toJson(result)))
        result
    }

    suspend fun getTrafficClones(owner: String, repo: String, forceRefresh: Boolean = false): Result<TrafficClones> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getTrafficClones(repoFullName)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
            return@runCatching gson.fromJson(cached.json, TrafficClones::class.java)
        }
        val result = api.getTrafficClones(owner, repo)
        dao?.insertTrafficClones(CachedTrafficClones(repoFullName, gson.toJson(result)))
        result
    }

    suspend fun getContributorStats(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<ContributorWeeklyStats>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getContributorStats(repoFullName)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt, statsTrafficCacheMs)) {
            val type = object : TypeToken<List<ContributorWeeklyStats>>() {}.type
            return@runCatching gson.fromJson(cached.json, type)
        }
        val result = api.getContributorStats(owner, repo)
        if (result.isNotEmpty()) {
            dao?.insertContributorStats(CachedContributorStats(repoFullName, gson.toJson(result)))
        }
        result
    }

    suspend fun getNotifications(): Result<List<GitHubNotification>> = runCatching {
        api.getNotifications()
    }

    suspend fun searchCode(owner: String, repo: String, query: String): Result<CodeSearchResponse> = runCatching {
        api.searchCode("$query+repo:$owner/$repo")
    }

    suspend fun markNotificationRead(threadId: String): Result<Unit> = runCatching {
        api.markNotificationRead(threadId)
        Unit
    }

    suspend fun getCheckRuns(owner: String, repo: String, ref: String, forceRefresh: Boolean = false): Result<CheckRunsResponse> = runCatching {
        val repoFullName = "$owner/$repo"
        val cacheId = "$repoFullName:$ref"
        val cached = dao?.getCheckRuns(cacheId)
        if (!forceRefresh && cached != null && !isExpired(cached.cachedAt)) {
            return@runCatching gson.fromJson(cached.json, CheckRunsResponse::class.java)
        }
        val result = api.getCheckRuns(owner, repo, ref)
        dao?.insertCheckRuns(CachedCheckRuns(cacheId, repoFullName, ref, gson.toJson(result)))
        result
    }

    companion object {
        private val gson = Gson()
    }

    suspend fun getCommitActivity(owner: String, repo: String, forceRefresh: Boolean = false): Result<List<CommitWeekActivity>> = runCatching {
        val repoFullName = "$owner/$repo"
        val cached = dao?.getCommitActivity(repoFullName)
        // 커밋 활동은 1시간 캐싱
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.cachedAt < 60 * 60 * 1000L) {
            val type = object : TypeToken<List<CommitWeekActivity>>() {}.type
            return@runCatching gson.fromJson<List<CommitWeekActivity>>(cached.json, type)
        }
        val activity = api.getCommitActivity(owner, repo)
        dao?.insertCommitActivity(CachedCommitActivity(repoFullName, gson.toJson(activity)))
        activity
    }
}

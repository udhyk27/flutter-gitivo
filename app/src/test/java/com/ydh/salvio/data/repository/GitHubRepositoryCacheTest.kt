package com.ydh.salvio.data.repository

import com.google.gson.Gson
import com.ydh.salvio.data.api.GitHubApi
import com.ydh.salvio.data.local.CacheDao
import com.ydh.salvio.data.local.CachedIssue
import com.ydh.salvio.data.local.CachedPr
import com.ydh.salvio.data.model.GitHubIssue
import com.ydh.salvio.data.model.GitHubPullRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubRepositoryCacheTest {

    private val gson = Gson()
    private lateinit var api: GitHubApi
    private lateinit var dao: CacheDao
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk(relaxed = true)
        repository = GitHubRepository(api, dao)
    }

    private fun pr(number: Int) = gson.fromJson("{\"number\":$number}", GitHubPullRequest::class.java)

    private fun issue(number: Int, isPullRequest: Boolean): GitHubIssue {
        val json = if (isPullRequest) {
            "{\"number\":$number,\"pull_request\":{}}"
        } else {
            "{\"number\":$number}"
        }
        return gson.fromJson(json, GitHubIssue::class.java)
    }

    @Test
    fun getPullRequests_freshCacheSkipsApi() = runBlocking {
        val cached = CachedPr("user/repo:7:open", "user/repo", "open", gson.toJson(pr(7)))
        coEvery { dao.getPrs("user/repo", "open") } returns listOf(cached)

        val result = repository.getPullRequests("user", "repo", "open").getOrThrow()

        assertEquals(listOf(7), result.map { it.number })
        coVerify(exactly = 0) { api.getPullRequests(any(), any(), any(), any(), any()) }
    }

    @Test
    fun getPullRequests_expiredCacheRefetchesAndReplacesCache() = runBlocking {
        val stale = CachedPr(
            "user/repo:7:open", "user/repo", "open", gson.toJson(pr(7)),
            cachedAt = System.currentTimeMillis() - 10 * 60 * 1000L
        )
        coEvery { dao.getPrs("user/repo", "open") } returns listOf(stale)
        coEvery { api.getPullRequests("user", "repo", state = "open", perPage = 100, page = 1) } returns listOf(pr(8))

        val result = repository.getPullRequests("user", "repo", "open").getOrThrow()

        assertEquals(listOf(8), result.map { it.number })
        coVerify(exactly = 1) { dao.deletePrs("user/repo", "open") }
        coVerify(exactly = 1) { dao.insertPrs(match { it.size == 1 && it[0].id == "user/repo:8:open" }) }
    }

    @Test
    fun getIssues_excludesPullRequestsFromApiResult() = runBlocking {
        coEvery { dao.getIssues("user/repo", "open") } returns emptyList()
        coEvery { api.getIssues("user", "repo", "open", perPage = 100, page = 1) } returns listOf(
            issue(1, isPullRequest = false),
            issue(2, isPullRequest = true),
            issue(3, isPullRequest = false)
        )

        val result = repository.getIssues("user", "repo", "open").getOrThrow()

        assertEquals(listOf(1, 3), result.map { it.number })
        coVerify(exactly = 1) {
            dao.insertIssues(match { list: List<CachedIssue> -> list.map { it.id } == listOf("user/repo:1:open", "user/repo:3:open") })
        }
        assertTrue(result.none { it.pullRequest != null })
    }
}

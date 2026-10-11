package com.ydh.salvio.data.repository

import com.ydh.salvio.data.api.GitHubApi
import com.ydh.salvio.data.model.GitHubRepo
import com.ydh.salvio.data.model.GitHubUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GitHubRepositoryTest {

    private lateinit var api: GitHubApi
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        api = mockk()
        repository = GitHubRepository(api, dao = null)
    }

    private fun repo(index: Int) = GitHubRepo(
        id = index.toLong(),
        name = "repo-$index",
        fullName = "user/repo-$index",
        description = null,
        private = false,
        owner = GitHubUser(1, "user", null, "https://example.com/a.png", 0, 0, 0),
        stars = 0,
        forks = 0,
        openIssues = 0,
        defaultBranch = "main",
        updatedAt = "2026-01-01T00:00:00Z",
        pushedAt = null,
        language = null
    )

    @Test
    fun getUserRepos_collectsPagesUntilShortPage() = runBlocking {
        coEvery { api.getUserRepos(perPage = 100, page = 1) } returns (0 until 100).map { repo(it) }
        coEvery { api.getUserRepos(perPage = 100, page = 2) } returns (100 until 130).map { repo(it) }

        val result = repository.getUserRepos(forceRefresh = true).getOrThrow()

        assertEquals(130, result.size)
        coVerify(exactly = 1) { api.getUserRepos(perPage = 100, page = 1) }
        coVerify(exactly = 1) { api.getUserRepos(perPage = 100, page = 2) }
        coVerify(exactly = 0) { api.getUserRepos(perPage = 100, page = 3) }
    }

    @Test
    fun getUserRepos_singlePageDoesNotRequestNext() = runBlocking {
        coEvery { api.getUserRepos(perPage = 100, page = 1) } returns (0 until 5).map { repo(it) }

        val result = repository.getUserRepos(forceRefresh = true).getOrThrow()

        assertEquals(5, result.size)
        coVerify(exactly = 1) { api.getUserRepos(perPage = 100, page = 1) }
    }

    @Test
    fun getUserRepos_returnsFailureWhenApiThrows() = runBlocking {
        coEvery { api.getUserRepos(perPage = 100, page = 1) } throws IOException("offline")

        val result = repository.getUserRepos(forceRefresh = true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}

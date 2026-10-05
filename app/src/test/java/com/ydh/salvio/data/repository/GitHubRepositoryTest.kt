package com.ydh.salvio.data.repository

import com.ydh.salvio.data.api.GitHubApi
import com.ydh.salvio.data.model.GitHubRepo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class GitHubRepositoryTest {

    private lateinit var api: GitHubApi
    private lateinit var repository: GitHubRepository

    @Before
    fun setup() {
        api = mockk()
        repository = GitHubRepository(api, null)
    }

    @Test
    fun `getUserRepos returns success when API call succeeds`() = runBlocking {
        val mockRepos = listOf(
            GitHubRepo(
                id = 1,
                name = "test-repo",
                fullName = "user/test-repo",
                description = "Test repository",
                stars = 10,
                language = "Kotlin",
                openIssues = 5,
                private = false,
                owner = mockk(),
                defaultBranch = "main"
            )
        )
        coEvery { api.getUserRepos(any(), any(), any()) } returns mockRepos

        val result = repository.getUserRepos(forceRefresh = false)

        assert(result.isSuccess)
        assert(result.getOrNull()?.size == 1)
        assert(result.getOrNull()?.first()?.name == "test-repo")
    }

    @Test
    fun `getUserRepos returns failure when API call fails`() = runBlocking {
        val exception = Exception("Network error")
        coEvery { api.getUserRepos(any(), any(), any()) } throws exception

        val result = repository.getUserRepos(forceRefresh = false)

        assert(result.isFailure)
        assert(result.exceptionOrNull() is Exception)
    }
}

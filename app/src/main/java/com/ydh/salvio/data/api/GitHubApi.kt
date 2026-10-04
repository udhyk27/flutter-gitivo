package com.ydh.salvio.data.api

import com.ydh.salvio.data.model.*
import retrofit2.http.*

interface GitHubApi {

    @GET("user")
    suspend fun getAuthenticatedUser(): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("sort") sort: String = "pushed",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubCommit>

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GitHubPullRequest>

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): List<GitHubBranch>

    @GET("repos/{owner}/{repo}/contributors")
    suspend fun getContributors(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): List<GitHubContributor>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/commits/{ref}")
    suspend fun getBranchLatestCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): GitHubCommit

    @GET("repos/{owner}/{repo}/commits/{sha}")
    suspend fun getCommitDetail(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String
    ): GitHubCommitDetail

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun getPrReviews(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): List<GitHubPrReview>

    @GET("repos/{owner}/{repo}/stats/commit_activity")
    suspend fun getCommitActivity(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<CommitWeekActivity>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubIssue>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/commits/{ref}/check-runs")
    suspend fun getCheckRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): CheckRunsResponse

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun getPrFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Query("per_page") perPage: Int = 100
    ): List<PullRequestFile>

    @GET("repos/{owner}/{repo}/traffic/views")
    suspend fun getTrafficViews(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): TrafficViews

    @GET("repos/{owner}/{repo}/traffic/clones")
    suspend fun getTrafficClones(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): TrafficClones

    @GET("repos/{owner}/{repo}/stats/contributors")
    suspend fun getContributorStats(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<ContributorWeeklyStats>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("all") all: Boolean = false,
        @Query("per_page") perPage: Int = 50
    ): List<GitHubNotification>

    @PATCH("notifications/threads/{thread_id}")
    suspend fun markNotificationRead(
        @Path("thread_id") threadId: String
    ): retrofit2.Response<Unit>

    @GET("search/code")
    suspend fun searchCode(
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 100
    ): CodeSearchResponse
}

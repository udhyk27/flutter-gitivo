package com.ydh.salvio.data.model

import com.google.gson.annotations.SerializedName

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("public_repos") val publicRepos: Int,
    val followers: Int,
    val following: Int
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    val private: Boolean,
    val owner: GitHubUser,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("forks_count") val forks: Int,
    @SerializedName("open_issues_count") val openIssues: Int,
    @SerializedName("default_branch") val defaultBranch: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("pushed_at") val pushedAt: String?,
    val language: String?
)

data class GitHubCommit(
    val sha: String,
    val commit: CommitDetail,
    val author: GitHubUser?,
    @SerializedName("html_url") val htmlUrl: String
)

data class CommitDetail(
    val message: String,
    val author: CommitAuthor,
    val committer: CommitAuthor
)

data class CommitAuthor(
    val name: String,
    val email: String,
    val date: String
)

data class GitHubPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val user: GitHubUser,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("merged_at") val mergedAt: String?,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val head: PrBranch,
    val base: PrBranch,
    @SerializedName("review_comments") val reviewComments: Int,
    val comments: Int,
    val additions: Int?,
    val deletions: Int?,
    @SerializedName("changed_files") val changedFiles: Int?
)

data class PrBranch(
    val label: String,
    val ref: String,
    val sha: String
)

data class GitHubBranch(
    val name: String,
    val commit: BranchCommit,
    val protected: Boolean
)

data class BranchCommit(
    val sha: String,
    val url: String
)

data class GitHubContributor(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    val contributions: Int,
    val id: Long
)

data class GitHubCommitDetail(
    val sha: String,
    val commit: CommitDetail,
    val author: GitHubUser?,
    @SerializedName("html_url") val htmlUrl: String,
    val files: List<CommitFile>,
    val stats: CommitStats?
)

data class CommitFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

data class CommitStats(
    val total: Int,
    val additions: Int,
    val deletions: Int
)

data class GitHubPrReview(
    val id: Long,
    val user: GitHubUser,
    val state: String,
    @SerializedName("submitted_at") val submittedAt: String?,
    val body: String?
)

data class CommitWeekActivity(
    val week: Long,
    val total: Int,
    val days: List<Int>
)

data class GitHubLabel(
    val id: Long,
    val name: String,
    val color: String,
    val description: String?
)

data class GitHubMilestone(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    @SerializedName("open_issues") val openIssues: Int,
    @SerializedName("closed_issues") val closedIssues: Int
)

data class PrRef(val url: String? = null)

data class PullRequestFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

data class TrafficPoint(
    val timestamp: String,
    val count: Int,
    val uniques: Int
)

data class TrafficViews(
    val count: Int,
    val uniques: Int,
    val views: List<TrafficPoint>
)

data class TrafficClones(
    val count: Int,
    val uniques: Int,
    val clones: List<TrafficPoint>
)

data class ContributorWeekData(
    val w: Long,
    val a: Int,
    val d: Int,
    val c: Int
)

data class ContributorWeeklyStats(
    val author: GitHubUser,
    val total: Int,
    val weeks: List<ContributorWeekData>
)

data class NotificationRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String
)

data class NotificationSubject(
    val title: String,
    val url: String?,
    val type: String
)

data class GitHubNotification(
    val id: String,
    val repository: NotificationRepo,
    val subject: NotificationSubject,
    val reason: String,
    val unread: Boolean,
    @SerializedName("updated_at") val updatedAt: String
)

data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val user: GitHubUser,
    val labels: List<GitHubLabel>,
    val milestone: GitHubMilestone?,
    val comments: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("closed_at") val closedAt: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("pull_request") val pullRequest: PrRef? = null
)

data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String
)

data class GitHubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val author: GitHubUser,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    val assets: List<GitHubAsset>,
    @SerializedName("html_url") val htmlUrl: String
)

data class CheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    @SerializedName("html_url") val htmlUrl: String
)

data class CheckRunsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("check_runs") val checkRuns: List<CheckRun>
)

data class CodeSearchItem(
    val name: String,
    val path: String,
    val sha: String,
    @SerializedName("html_url") val htmlUrl: String,
    val repository: NotificationRepo
)

data class CodeSearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("incomplete_results") val incompleteResults: Boolean,
    val items: List<CodeSearchItem>
)

data class GitHubRepoStats(
    val repoFullName: String,
    val openPrCount: Int,
    val mergedPrCount: Int,
    val closedPrCount: Int,
    val branchCount: Int,
    val contributorCount: Int
)

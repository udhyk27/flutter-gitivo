package com.ydh.salvio.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.data.model.*
import kotlinx.coroutines.flow.update
import com.ydh.salvio.data.repository.GitHubRepository
import com.ydh.salvio.util.httpCode
import com.ydh.salvio.util.toUserMessage
import com.ydh.salvio.util.NetworkState
import com.ydh.salvio.util.isAuthExpired
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val repo: GitHubRepo? = null,
    val stats: GitHubRepoStats? = null,
    val recentCommits: List<GitHubCommit> = emptyList(),
    val openPrs: List<GitHubPullRequest> = emptyList(),
    val branches: List<GitHubBranch> = emptyList(),
    val contributors: List<GitHubContributor> = emptyList(),
    val error: String? = null
)

data class PrUiState(
    val isLoading: Boolean = false,
    val openPrs: List<GitHubPullRequest> = emptyList(),
    val closedPrs: List<GitHubPullRequest> = emptyList(),
    val mergedPrs: List<GitHubPullRequest> = emptyList(),
    val prReviews: Map<Int, List<GitHubPrReview>> = emptyMap(),
    val prCheckRuns: Map<Int, CheckRunsResponse> = emptyMap(),
    val prFiles: Map<Int, List<PullRequestFile>> = emptyMap(),
    val error: String? = null
)

data class SearchUiState(
    val isLoading: Boolean = false,
    val results: com.ydh.salvio.data.model.CodeSearchResponse? = null,
    val query: String = "",
    val error: String? = null
)

data class IssueUiState(
    val isLoading: Boolean = false,
    val openIssues: List<GitHubIssue> = emptyList(),
    val closedIssues: List<GitHubIssue> = emptyList(),
    val error: String? = null
)

data class ReleaseUiState(
    val isLoading: Boolean = false,
    val releases: List<GitHubRelease> = emptyList(),
    val error: String? = null
)

data class BranchUiState(
    val isLoading: Boolean = false,
    val branches: List<GitHubBranch> = emptyList(),
    val branchCommits: Map<String, GitHubCommit> = emptyMap(),
    val error: String? = null
)

data class StatsUiState(
    val isLoading: Boolean = false,
    val contributors: List<GitHubContributor> = emptyList(),
    val contributorWeeklyStats: List<ContributorWeeklyStats> = emptyList(),
    val commits: List<GitHubCommit> = emptyList(),
    val commitActivity: List<CommitWeekActivity> = emptyList(),
    val trafficViews: TrafficViews? = null,
    val trafficClones: TrafficClones? = null,
    val trafficAccessDenied: Boolean = false,
    val error: String? = null
)

data class CommitDetailUiState(
    val isLoading: Boolean = false,
    val detail: GitHubCommitDetail? = null,
    val error: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalvioApplication
    private val dataStore = app.tokenDataStore

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _prState = MutableStateFlow(PrUiState())
    val prState: StateFlow<PrUiState> = _prState.asStateFlow()

    private val _branchState = MutableStateFlow(BranchUiState())
    val branchState: StateFlow<BranchUiState> = _branchState.asStateFlow()

    private val _statsState = MutableStateFlow(StatsUiState())
    val statsState: StateFlow<StatsUiState> = _statsState.asStateFlow()

    private val _commitDetailState = MutableStateFlow(CommitDetailUiState())
    val commitDetailState: StateFlow<CommitDetailUiState> = _commitDetailState.asStateFlow()

    private val _issueState = MutableStateFlow(IssueUiState())
    val issueState: StateFlow<IssueUiState> = _issueState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _releaseState = MutableStateFlow(ReleaseUiState())
    val releaseState: StateFlow<ReleaseUiState> = _releaseState.asStateFlow()

    private var prDetailsJob: Job? = null
    private var searchJob: Job? = null

    private suspend fun getRepo(): GitHubRepository? {
        val token = dataStore.token.first() ?: return null
        return app.githubRepository(token)
    }

    fun loadDashboard(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _dashboardState.value = _dashboardState.value.copy(isLoading = true, error = null)

            if (!NetworkState.isNetworkAvailable(app)) {
                _dashboardState.value = _dashboardState.value.copy(isLoading = false, error = "인터넷에 연결되어 있지 않습니다.")
                return@launch
            }

            val repo = getRepo() ?: run {
                _dashboardState.value = _dashboardState.value.copy(isLoading = false, error = NO_TOKEN_MSG)
                return@launch
            }

            val repoInfo = async { repo.getRepo(owner, repoName, forceRefresh) }
            val commits = async { repo.getCommits(owner, repoName, forceRefresh = forceRefresh) }
            val openPrs = async { repo.getPullRequests(owner, repoName, "open", forceRefresh) }
            val branches = async { repo.getBranches(owner, repoName, forceRefresh) }
            val contributors = async { repo.getContributors(owner, repoName, forceRefresh) }
            val closedPrs = async { repo.getPullRequests(owner, repoName, "closed", forceRefresh) }

            val repoInfoResult = repoInfo.await()
            val commitsResult = commits.await()
            val openPrsResult = openPrs.await()
            val branchesResult = branches.await()
            val contributorsResult = contributors.await()
            val closedResult = closedPrs.await()
            val closedList = closedResult.getOrElse { emptyList() }

            // 여러 호출 중 하나라도 실패하면 그 메시지를 노출한다.
            // (부분 실패를 "정상 0" 으로 숨기지 않기 위함)
            val firstError = listOf(
                repoInfoResult, commitsResult, openPrsResult,
                branchesResult, contributorsResult, closedResult
            ).firstNotNullOfOrNull { it.exceptionOrNull() }

            val errorMessage = firstError?.let {
                if (it.isAuthExpired()) "인증이 만료되었습니다. 다시 로그인하세요."
                else it.toUserMessage("대시보드를 불러오지 못했습니다.")
            }

            _dashboardState.value = DashboardUiState(
                isLoading = false,
                repo = repoInfoResult.getOrNull(),
                // 핵심 저장소 정보 조회 자체가 실패하면 통계는 신뢰할 수 없으므로 null
                // → 화면이 전체 에러 상태를 보여주도록 한다.
                stats = if (repoInfoResult.isSuccess) GitHubRepoStats(
                    repoFullName = "$owner/$repoName",
                    commitCount = commitsResult.getOrElse { emptyList() }.size,
                    openPrCount = openPrsResult.getOrElse { emptyList() }.size,
                    mergedPrCount = closedList.count { it.mergedAt != null },
                    closedPrCount = closedList.count { it.mergedAt == null },
                    branchCount = branchesResult.getOrElse { emptyList() }.size,
                    contributorCount = contributorsResult.getOrElse { emptyList() }.size
                ) else null,
                recentCommits = commitsResult.getOrElse { emptyList() }.take(5),
                openPrs = openPrsResult.getOrElse { emptyList() }.take(3),
                branches = branchesResult.getOrElse { emptyList() },
                contributors = contributorsResult.getOrElse { emptyList() },
                error = errorMessage
            )
        }
    }

    fun loadPullRequests(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _prState.value = PrUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _prState.value = PrUiState(error = NO_TOKEN_MSG)
                return@launch
            }

            val open = async { repo.getPullRequests(owner, repoName, "open", forceRefresh) }
            val closed = async { repo.getPullRequests(owner, repoName, "closed", forceRefresh) }

            val openResult = open.await()
            val closedResult = closed.await()
            val openList = openResult.getOrElse { emptyList() }
            val closedList = closedResult.getOrElse { emptyList() }

            _prState.value = _prState.value.copy(
                isLoading = false,
                openPrs = openList,
                closedPrs = closedList.filter { it.mergedAt == null },
                mergedPrs = closedList.filter { it.mergedAt != null },
                error = listOf(openResult, closedResult)
                    .firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?.toUserMessage("PR을 불러오지 못했습니다.")
            )

            // 리뷰 + CI 상태 비동기 로드
            loadPrDetails(owner, repoName, openList, forceRefresh)
        }
    }

    private fun loadPrDetails(
        owner: String,
        repoName: String,
        prs: List<GitHubPullRequest>,
        forceRefresh: Boolean
    ) {
        // 이전 상세 로드를 취소해 오래된 응답이 최신 목록을 덮어쓰지 않도록 한다.
        prDetailsJob?.cancel()
        prDetailsJob = viewModelScope.launch {
            val repo = getRepo() ?: return@launch
            val reviewMap = mutableMapOf<Int, List<GitHubPrReview>>()
            val checkRunMap = mutableMapOf<Int, CheckRunsResponse>()

            prs.take(20).forEach { pr ->
                repo.getPrReviews(owner, repoName, pr.number, forceRefresh).getOrNull()?.let { reviews ->
                    reviewMap[pr.number] = reviews
                }
                repo.getCheckRuns(owner, repoName, pr.head.sha, forceRefresh).getOrNull()?.let { runs ->
                    checkRunMap[pr.number] = runs
                }
                _prState.update { it.copy(prReviews = reviewMap.toMap(), prCheckRuns = checkRunMap.toMap()) }
            }
        }
    }

    fun loadIssues(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _issueState.value = IssueUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _issueState.value = IssueUiState(error = NO_TOKEN_MSG)
                return@launch
            }
            val open = async { repo.getIssues(owner, repoName, "open", forceRefresh) }
            val closed = async { repo.getIssues(owner, repoName, "closed", forceRefresh) }
            val openResult = open.await()
            val closedResult = closed.await()
            _issueState.value = IssueUiState(
                isLoading = false,
                openIssues = openResult.getOrElse { emptyList() },
                closedIssues = closedResult.getOrElse { emptyList() },
                error = listOf(openResult, closedResult)
                    .firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?.toUserMessage("이슈를 불러오지 못했습니다.")
            )
        }
    }

    fun loadReleases(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _releaseState.value = ReleaseUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _releaseState.value = ReleaseUiState(error = NO_TOKEN_MSG)
                return@launch
            }
            repo.getReleases(owner, repoName, forceRefresh).fold(
                onSuccess = { _releaseState.value = ReleaseUiState(releases = it) },
                onFailure = { _releaseState.value = ReleaseUiState(error = it.toUserMessage("릴리스를 불러오지 못했습니다.")) }
            )
        }
    }

    fun loadBranches(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _branchState.value = BranchUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _branchState.value = BranchUiState(error = NO_TOKEN_MSG)
                return@launch
            }

            val branchesResult = repo.getBranches(owner, repoName, forceRefresh)
            val branches = branchesResult.getOrElse { emptyList() }
            val commitMap = mutableMapOf<String, GitHubCommit>()

            branches.forEach { branch ->
                repo.getBranchLatestCommit(owner, repoName, branch.name).getOrNull()?.let {
                    commitMap[branch.name] = it
                }
            }

            _branchState.value = BranchUiState(
                isLoading = false,
                branches = branches,
                branchCommits = commitMap,
                error = branchesResult.exceptionOrNull()?.toUserMessage("브랜치 목록을 불러오지 못했습니다.")
            )
        }
    }

    fun loadCommitDetail(owner: String, repoName: String, sha: String) {
        viewModelScope.launch {
            _commitDetailState.value = CommitDetailUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _commitDetailState.value = CommitDetailUiState(error = NO_TOKEN_MSG)
                return@launch
            }
            repo.getCommitDetail(owner, repoName, sha).fold(
                onSuccess = { _commitDetailState.value = CommitDetailUiState(detail = it) },
                onFailure = { _commitDetailState.value = CommitDetailUiState(error = it.toUserMessage("커밋 정보를 불러오지 못했습니다.")) }
            )
        }
    }

    fun loadStats(owner: String, repoName: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _statsState.value = StatsUiState(isLoading = true)
            val repo = getRepo() ?: run {
                _statsState.value = StatsUiState(error = NO_TOKEN_MSG)
                return@launch
            }

            val contributors = async { repo.getContributors(owner, repoName, forceRefresh) }
            val commits = async { repo.getCommits(owner, repoName, 1, forceRefresh) }
            val activity = async { repo.getCommitActivity(owner, repoName, forceRefresh) }
            val weeklyStats = async { repo.getContributorStats(owner, repoName, forceRefresh) }
            val trafficViews = async { repo.getTrafficViews(owner, repoName, forceRefresh) }
            val trafficClones = async { repo.getTrafficClones(owner, repoName, forceRefresh) }

            val contributorsResult = contributors.await()
            val commitsResult = commits.await()
            val activityResult = activity.await()
            val weeklyStatsResult = weeklyStats.await()
            val trafficViewsResult = trafficViews.await()
            val trafficClonesResult = trafficClones.await()

            // 트래픽은 저장소 관리자만 접근 가능(403). 이는 정상적인 권한 제한이므로
            // 일반 에러로 취급하지 않고 별도 플래그로만 표시한다.
            val trafficAccessDenied = trafficViewsResult.exceptionOrNull()?.httpCode() == 403 ||
                trafficClonesResult.exceptionOrNull()?.httpCode() == 403

            // 트래픽(403 권한 제한)을 제외한 핵심 통계 호출들에서 에러를 집계한다.
            val firstError = listOf(contributorsResult, commitsResult, activityResult, weeklyStatsResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }

            _statsState.value = StatsUiState(
                isLoading = false,
                contributors = contributorsResult.getOrElse { emptyList() },
                contributorWeeklyStats = weeklyStatsResult.getOrElse { emptyList() },
                commits = commitsResult.getOrElse { emptyList() },
                commitActivity = activityResult.getOrElse { emptyList() },
                trafficViews = trafficViewsResult.getOrNull(),
                trafficClones = trafficClonesResult.getOrNull(),
                trafficAccessDenied = trafficAccessDenied,
                error = firstError?.toUserMessage("통계를 불러오지 못했습니다.")
            )
        }
    }

    fun searchCode(owner: String, repoName: String, query: String) {
        if (query.isBlank()) return
        // 이전 검색을 취소해 느린 응답이 최신 결과를 덮어쓰지 않게 한다.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = SearchUiState(isLoading = true, query = query)
            val repo = getRepo() ?: return@launch
            repo.searchCode(owner, repoName, query).fold(
                onSuccess = { _searchState.value = SearchUiState(results = it, query = query) },
                onFailure = {
                    val msg = when (it.httpCode()) {
                        403 -> "검색 권한이 없습니다."
                        422 -> "검색어를 다시 확인해주세요."
                        else -> it.toUserMessage("검색에 실패했습니다.")
                    }
                    _searchState.value = SearchUiState(error = msg, query = query)
                }
            )
        }
    }

    fun clearSearch() {
        _searchState.value = SearchUiState()
    }

    fun loadPrFiles(owner: String, repoName: String, prNumber: Int) {
        viewModelScope.launch {
            val repo = getRepo() ?: return@launch
            repo.getPrFiles(owner, repoName, prNumber).getOrNull()?.let { files ->
                _prState.update { it.copy(prFiles = it.prFiles + (prNumber to files)) }
            }
        }
    }

    companion object {
        private const val NO_TOKEN_MSG = "로그인이 필요합니다. 다시 로그인해 주세요."
    }
}

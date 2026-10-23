package com.ydh.salvio.ui.screen.pullrequest

import com.ydh.salvio.util.openInBrowser
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.CheckRunsResponse
import com.ydh.salvio.data.model.GitHubPrReview
import com.ydh.salvio.data.model.GitHubPullRequest
import com.ydh.salvio.data.model.PullRequestFile
import com.ydh.salvio.ui.component.EmptyState
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.component.StatusBadge
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.viewmodel.DashboardViewModel
import com.ydh.salvio.util.formatAbsolute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestScreen(
    owner: String,
    repoName: String,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.prState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Open", "Merged", "Closed")
    var expandedPrNumber by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadPullRequests(owner, repoName)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("Pull Requests", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { dashboardViewModel.loadPullRequests(owner, repoName, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp) }
            ) {
                tabs.forEachIndexed { index, title ->
                    val count = when {
                        state.isLoading -> null
                        else -> when (index) {
                            0 -> state.openPrs.size
                            1 -> state.mergedPrs.size
                            2 -> state.closedPrs.size
                            else -> 0
                        }
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                if (count != null) "$title ($count)" else title,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            when {
                state.isLoading && state.openPrs.isEmpty() -> LoadingState()
                state.error != null && state.openPrs.isEmpty() && state.mergedPrs.isEmpty() && state.closedPrs.isEmpty() ->
                    ErrorState(state.error ?: "PR 목록을 불러오지 못했습니다.", onRetry = { dashboardViewModel.loadPullRequests(owner, repoName, forceRefresh = true) })
                else -> {
                    val prs = when (selectedTab) {
                        0 -> state.openPrs
                        1 -> state.mergedPrs
                        2 -> state.closedPrs
                        else -> emptyList()
                    }
                    val prState = when (selectedTab) {
                        0 -> "open"
                        1 -> "merged"
                        else -> "closed"
                    }

                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = { dashboardViewModel.loadPullRequests(owner, repoName, forceRefresh = true) }
                    ) {
                        if (prs.isEmpty()) {
                            EmptyState("${tabs[selectedTab]} PR이 없습니다.", Icons.Default.MergeType, hint = "아래로 당기면 목록을 새로고침합니다.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(Spacing.screen),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                state.detailError?.let { message ->
                                    item(key = "detail-error") {
                                        Text(message, fontSize = 12.sp, color = SalvioTheme.colors.danger)
                                    }
                                }
                                items(prs, key = { it.number }) { pr ->
                                    PullRequestCard(
                                        pr = pr,
                                        state = prState,
                                        reviews = state.prReviews[pr.number] ?: emptyList(),
                                        checkRuns = state.prCheckRuns[pr.number],
                                        files = state.prFiles[pr.number],
                                        filesFailed = state.fileErrors.contains(pr.number),
                                        isExpanded = expandedPrNumber == pr.number,
                                        onExpandToggle = {
                                            if (expandedPrNumber == pr.number) {
                                                expandedPrNumber = null
                                            } else {
                                                expandedPrNumber = pr.number
                                                if (state.prFiles[pr.number] == null) {
                                                    dashboardViewModel.loadPrFiles(owner, repoName, pr.number)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestCard(
    pr: GitHubPullRequest,
    state: String,
    reviews: List<GitHubPrReview>,
    checkRuns: CheckRunsResponse? = null,
    files: List<PullRequestFile>? = null,
    filesFailed: Boolean = false,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {}
) {
    val stateColor = when (state) {
        "open" -> SalvioTheme.colors.success
        "merged" -> SalvioTheme.colors.done
        else -> SalvioTheme.colors.danger
    }
    val stateIcon = when (state) {
        "open" -> Icons.Default.MergeType
        "merged" -> Icons.Default.Merge
        else -> Icons.Default.Close
    }

    val reviewSummary = summarizeReviews(reviews)

    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(stateIcon, contentDescription = null, tint = stateColor, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pr.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "#${pr.number}", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                        AsyncImage(
                            placeholder = ColorPainter(SalvioTheme.colors.border),
                            model = pr.user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Text(text = pr.user.login, fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                        Text(text = formatPrDate(pr, state), fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                    }
                }
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        context.openInBrowser(pr.htmlUrl)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "GitHub에서 열기",
                        tint = SalvioTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (reviewSummary.isNotEmpty() || checkRuns != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    checkRuns?.let { CiBadge(it) }
                    reviewSummary.forEach { (label, tone) ->
                        val color = when (tone) {
                            ReviewTone.APPROVED -> SalvioTheme.colors.success
                            ReviewTone.CHANGES -> SalvioTheme.colors.attention
                        }
                        StatusBadge(label = label, color = color)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Default.Comment, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                    Text(text = "${pr.comments + pr.reviewComments}", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                }
                BranchChip(label = pr.base.ref)
                Spacer(modifier = Modifier.weight(1f))
                pr.changedFiles?.let {
                    TextButton(
                        onClick = onExpandToggle,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "$it 파일 ${if (isExpanded) "▲" else "▼"}",
                            fontSize = 12.sp,
                            color = SalvioTheme.colors.accent
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(Spacing.sm))
                if (files == null && filesFailed) {
                    Text("파일 목록을 불러오지 못했습니다. 카드를 접었다 다시 펼치면 재시도합니다.", fontSize = 12.sp, color = SalvioTheme.colors.danger)
                } else if (files == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    files.take(20).forEach { file ->
                        PrFileRow(file)
                    }
                    if (files.size > 20) {
                        Text(
                            text = "… 외 ${files.size - 20}개 파일",
                            fontSize = 11.sp,
                            color = SalvioTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrFileRow(file: PullRequestFile) {
    val statusColor = when (file.status) {
        "added" -> SalvioTheme.colors.success
        "removed" -> SalvioTheme.colors.danger
        "renamed" -> SalvioTheme.colors.attention
        else -> SalvioTheme.colors.accent
    }
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Surface(shape = Radius.chip, color = statusColor.copy(alpha = 0.15f)) {
            Text(
                text = when (file.status) {
                    "added" -> "A"
                    "removed" -> "D"
                    "renamed" -> "R"
                    "modified" -> "M"
                    else -> "?"
                },
                fontSize = 10.sp,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Text(
            text = file.filename.substringAfterLast('/'),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "+${file.additions} -${file.deletions}",
            fontSize = 11.sp,
            color = SalvioTheme.colors.textSecondary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun CiBadge(checkRuns: CheckRunsResponse) {
    if (checkRuns.totalCount == 0) return
    val runs = checkRuns.checkRuns
    val allCompleted = runs.all { it.status == "completed" }
    val (label, color) = when {
        !allCompleted -> "CI 실행 중" to SalvioTheme.colors.attention
        runs.all { it.conclusion == "success" || it.conclusion == "skipped" || it.conclusion == "neutral" } ->
            "CI 통과" to SalvioTheme.colors.success
        runs.any { it.conclusion == "failure" || it.conclusion == "timed_out" } ->
            "CI 실패" to SalvioTheme.colors.danger
        else -> "CI ${runs.first().conclusion ?: "완료"}" to SalvioTheme.colors.textSecondary
    }
    StatusBadge(label = label, color = color)
}

@Composable
private fun BranchChip(label: String) {
    Surface(
        shape = Radius.chip,
        color = MaterialTheme.colorScheme.outlineVariant
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = SalvioTheme.colors.accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

private enum class ReviewTone { APPROVED, CHANGES }

/** 색은 테마에 의존하므로 여기서는 톤 종류만 반환하고 색은 Composable에서 매핑한다. */
private fun summarizeReviews(reviews: List<GitHubPrReview>): List<Pair<String, ReviewTone>> {
    if (reviews.isEmpty()) return emptyList()

    val latestByReviewer = reviews
        .filter { it.state != "COMMENTED" && it.state != "DISMISSED" }
        .groupBy { it.user.login }
        .mapValues { (_, v) -> v.last().state }

    val approvedCount = latestByReviewer.values.count { it == "APPROVED" }
    val changesCount = latestByReviewer.values.count { it == "CHANGES_REQUESTED" }

    val result = mutableListOf<Pair<String, ReviewTone>>()
    if (approvedCount > 0) result.add("✓ Approved $approvedCount" to ReviewTone.APPROVED)
    if (changesCount > 0) result.add("✗ Changes $changesCount" to ReviewTone.CHANGES)
    return result
}

private fun formatPrDate(pr: GitHubPullRequest, state: String): String {
    val dateStr = when (state) {
        "merged" -> pr.mergedAt
        "closed" -> pr.closedAt
        else -> pr.createdAt
    } ?: pr.createdAt

    val prefix = when (state) {
        "merged" -> "merged "
        "closed" -> "closed "
        else -> "opened "
    }
    return prefix + formatAbsolute(dateStr, "MM월 dd일")
}

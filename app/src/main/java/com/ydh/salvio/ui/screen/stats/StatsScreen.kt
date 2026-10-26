package com.ydh.salvio.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.CommitWeekActivity
import com.ydh.salvio.data.model.ContributorWeeklyStats
import com.ydh.salvio.data.model.GitHubContributor
import com.ydh.salvio.data.model.TrafficClones
import com.ydh.salvio.data.model.TrafficViews
import com.ydh.salvio.ui.component.CommitActivityChart
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.component.SectionHeader
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(owner: String, repoName: String, dashboardViewModel: DashboardViewModel, onBack: () -> Unit) {
    val state by dashboardViewModel.statsState.collectAsState()

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadStats(owner, repoName)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("작업 통계", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { dashboardViewModel.loadStats(owner, repoName, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading && state.contributors.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.error != null && state.contributors.isEmpty() ->
                ErrorState(state.error ?: "통계를 불러오지 못했습니다.", {
                    dashboardViewModel.loadStats(owner, repoName, forceRefresh = true)
                }, Modifier.padding(paddingValues))
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { dashboardViewModel.loadStats(owner, repoName, forceRefresh = true) },
                    modifier = Modifier.padding(paddingValues)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        item { CommitActivityCard(activity = state.commitActivity) }
                        if (state.trafficViews != null || state.trafficClones != null) {
                            item {
                                TrafficCard(
                                    views = state.trafficViews,
                                    clones = state.trafficClones
                                )
                            }
                        } else if (state.trafficAccessDenied) {
                            item { TrafficDeniedCard() }
                        }
                        item {
                            ContributorRankingCard(
                                contributors = state.contributors,
                                weeklyStats = state.contributorWeeklyStats
                            )
                        }
                        item {
                            RecentCommitAuthorsCard(
                                commits = state.commits.groupBy { it.commit.author.name }
                                    .map { (name, commits) -> name to commits.size }
                                    .sortedByDescending { it.second }
                                    .take(10)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitActivityCard(activity: List<CommitWeekActivity>) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(title = "커밋 활동 (최근 26주)", icon = Icons.Default.ShowChart)
            Spacer(modifier = Modifier.height(Spacing.md))
            if (activity.isEmpty()) {
                // GitHub 통계 엔드포인트는 집계 중이면 빈 결과(202)를 반환한다.
                Text(
                    text = "커밋 활동 데이터를 집계 중이거나 표시할 내역이 없습니다.",
                    fontSize = 12.sp,
                    color = SalvioTheme.colors.textSecondary
                )
            } else {
                val totalRecent = activity.takeLast(4).sumOf { it.total }
                CommitActivityChart(activity = activity)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "최근 4주 총 $totalRecent commits",
                    fontSize = 12.sp,
                    color = SalvioTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TrafficCard(views: TrafficViews?, clones: TrafficClones?) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(title = "저장소 트래픽 (최근 14일)", icon = Icons.Default.TrendingUp)
            Spacer(modifier = Modifier.height(Spacing.lg))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                views?.let {
                    TrafficStatItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Visibility,
                        label = "조회수",
                        total = it.count,
                        uniques = it.uniques
                    )
                }
                clones?.let {
                    TrafficStatItem(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Download,
                        label = "클론 수",
                        total = it.count,
                        uniques = it.uniques
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficDeniedCard() {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = SalvioTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    "트래픽 통계 없음",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("저장소 push 권한이 있어야 조회할 수 있습니다.", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TrafficStatItem(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    total: Int,
    uniques: Int
) {
    Surface(
        modifier = modifier,
        shape = Radius.field,
        color = MaterialTheme.colorScheme.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = SalvioTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = label, fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "$total",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = "순방문자 $uniques", fontSize = 11.sp, color = SalvioTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun ContributorRankingCard(
    contributors: List<GitHubContributor>,
    weeklyStats: List<ContributorWeeklyStats> = emptyList()
) {
    val statsMap = weeklyStats.associate { it.author.login to it }
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(title = "기여자 순위", icon = Icons.Default.EmojiEvents)
            Spacer(modifier = Modifier.height(Spacing.md))

            if (contributors.isEmpty()) {
                Text("데이터가 없습니다.", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp)
            } else {
                val maxContributions = contributors.maxOfOrNull { it.contributions } ?: 1
                val top = contributors.take(10)
                top.forEachIndexed { index, contributor ->
                    ContributorRow(
                        rank = index + 1,
                        contributor = contributor,
                        maxContributions = maxContributions,
                        weeklyStats = statsMap[contributor.login]
                    )
                    if (index < top.size - 1) {
                        HorizontalDivider(
                            color = SalvioTheme.colors.borderMuted,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = Spacing.xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(
    rank: Int,
    contributor: GitHubContributor,
    maxContributions: Int,
    weeklyStats: ContributorWeeklyStats? = null
) {
    val rankColor = when (rank) {
        1 -> SalvioTheme.colors.attention
        2 -> SalvioTheme.colors.textSecondary
        3 -> SalvioTheme.colors.danger.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "#$rank",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = rankColor,
                modifier = Modifier.width(28.dp)
            )
            AsyncImage(
                placeholder = ColorPainter(SalvioTheme.colors.border),
                model = contributor.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(SalvioTheme.colors.border)
            )
            Text(
                text = contributor.login,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${contributor.contributions} commits",
                    fontSize = 12.sp,
                    color = SalvioTheme.colors.textSecondary
                )
                weeklyStats?.let { ws ->
                    val totalAdditions = ws.weeks.sumOf { it.a }
                    val totalDeletions = ws.weeks.sumOf { it.d }
                    if (totalAdditions > 0 || totalDeletions > 0) {
                        Text(
                            text = "+$totalAdditions / -$totalDeletions",
                            fontSize = 10.sp,
                            color = SalvioTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(modifier = Modifier.padding(start = 40.dp)) {
            val ratio = contributor.contributions.toFloat() / maxContributions.toFloat()
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShapeSmall()),
                color = if (rank <= 3) rankColor else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun RecentCommitAuthorsCard(commits: List<Pair<String, Int>>) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(title = "최근 커밋 작성자", icon = Icons.Default.Commit)
            Spacer(modifier = Modifier.height(Spacing.md))

            if (commits.isEmpty()) {
                Text("데이터가 없습니다.", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp)
            } else {
                val maxCount = commits.maxOfOrNull { it.second } ?: 1
                commits.forEach { (name, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.padding(vertical = Spacing.xs)
                    ) {
                        Text(
                            text = name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(100.dp),
                            maxLines = 1
                        )
                        val ratio = count.toFloat() / maxCount.toFloat()
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShapeSmall()),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "$count",
                            fontSize = 12.sp,
                            color = SalvioTheme.colors.textSecondary,
                            modifier = Modifier.width(30.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun RoundedCornerShapeSmall() = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)

package com.ydh.salvio.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.GitHubCommit
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.component.SectionHeader
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.viewmodel.DashboardUiState
import com.ydh.salvio.viewmodel.DashboardViewModel
import com.ydh.salvio.util.relativeDays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    owner: String,
    repoName: String,
    dashboardViewModel: DashboardViewModel,
    onNavigateToPRs: () -> Unit,
    onNavigateToBranches: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToCommit: (sha: String) -> Unit,
    onNavigateToIssues: () -> Unit,
    onNavigateToReleases: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.dashboardState.collectAsState()

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadDashboard(owner, repoName, forceRefresh = false)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = {
                    Column {
                        Text(repoName, style = MaterialTheme.typography.titleMedium)
                        Text(owner, fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "코드 검색")
                    }
                    IconButton(onClick = { dashboardViewModel.loadDashboard(owner, repoName, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (state.isLoading && state.stats == null) {
            LoadingState(Modifier.padding(paddingValues))
            return@Scaffold
        }

        if (state.error != null && state.stats == null) {
            ErrorState(state.error ?: "데이터를 불러오지 못했습니다.", { dashboardViewModel.loadDashboard(owner, repoName, forceRefresh = true) }, Modifier.padding(paddingValues))
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { dashboardViewModel.loadDashboard(owner, repoName, forceRefresh = true) },
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                val bannerError = state.error
                if (state.isLoading && state.stats != null) {
                    item { RefreshingBanner() }
                }
                if (bannerError != null && state.stats != null) {
                    item { RefreshErrorBanner(bannerError) }
                }
                item { StatsRow(state, onNavigateToPRs, onNavigateToBranches, onNavigateToStats) }
                item { QuickActionsRow(onNavigateToPRs, onNavigateToBranches, onNavigateToStats, onNavigateToIssues, onNavigateToReleases) }
                item { RecentCommitsSection(state.recentCommits, onNavigateToCommit) }
                item { OpenPRsSection(state, onNavigateToPRs) }
                item { ContributorsSection(state) }
            }
        }
    }
}

@Composable
private fun RefreshingBanner() {
    Surface(
        shape = Radius.button,
        color = SalvioTheme.colors.accent.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SalvioTheme.colors.accent)
            Text("업데이트 중...", fontSize = 13.sp, color = SalvioTheme.colors.accent)
        }
    }
}

@Composable
private fun RefreshErrorBanner(message: String) {
    Surface(
        shape = Radius.button,
        color = SalvioTheme.colors.danger.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SalvioTheme.colors.danger, modifier = Modifier.size(16.dp))
            Text(message, fontSize = 13.sp, color = SalvioTheme.colors.danger)
        }
    }
}

@Composable
private fun StatsRow(
    state: DashboardUiState,
    onNavigateToPRs: () -> Unit,
    onNavigateToBranches: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StatCard(Modifier.weight(1f), "Open PR", state.stats?.openPrCount?.toString() ?: "-", onNavigateToPRs)
        StatCard(Modifier.weight(1f), "Merged", state.stats?.mergedPrCount?.toString() ?: "-", onNavigateToPRs)
        StatCard(Modifier.weight(1f), "브랜치", state.stats?.branchCount?.toString() ?: "-", onNavigateToBranches)
        StatCard(Modifier.weight(1f), "기여자", state.stats?.contributorCount?.toString() ?: "-", onNavigateToStats)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    SalvioCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 11.sp, color = SalvioTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun QuickActionsRow(
    onNavigateToPRs: () -> Unit,
    onNavigateToBranches: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToIssues: () -> Unit,
    onNavigateToReleases: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ActionButton(Modifier.weight(1f), Icons.Default.MergeType, "Pull Requests", onNavigateToPRs)
            ActionButton(Modifier.weight(1f), Icons.Default.AccountTree, "브랜치", onNavigateToBranches)
            ActionButton(Modifier.weight(1f), Icons.Default.BarChart, "통계", onNavigateToStats)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ActionButton(Modifier.weight(1f), Icons.Default.BugReport, "Issues", onNavigateToIssues)
            ActionButton(Modifier.weight(1f), Icons.Default.LocalOffer, "Releases", onNavigateToReleases)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    SalvioCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.sm).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Text(text = label, fontSize = 11.sp, color = SalvioTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun RecentCommitsSection(commits: List<GitHubCommit>, onCommitClick: (sha: String) -> Unit) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(title = "최근 커밋", icon = Icons.Default.Commit)
            Spacer(modifier = Modifier.height(Spacing.md))
            if (commits.isEmpty()) {
                Text("커밋이 없습니다.", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp)
            } else {
                commits.forEach { commit ->
                    CommitItem(commit, onClick = { onCommitClick(commit.sha) })
                    if (commit != commits.last()) HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun CommitItem(commit: GitHubCommit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = commit.author?.avatarUrl,
            contentDescription = "커밋 작성자 아바타",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(SalvioTheme.colors.border)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = commit.commit.message.lines().first(),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${commit.commit.author.name} • ${formatDate(commit.commit.author.date)}",
                fontSize = 11.sp,
                color = SalvioTheme.colors.textSecondary
            )
        }
        Text(
            text = commit.sha.take(7),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun OpenPRsSection(state: DashboardUiState, onNavigateToPRs: () -> Unit) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            SectionHeader(
                title = "열린 Pull Requests",
                icon = Icons.Default.MergeType,
                action = if (state.openPrs.isNotEmpty()) "모두 보기" to onNavigateToPRs else null
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            if (state.openPrs.isEmpty()) {
                Text("열린 PR이 없습니다.", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp)
            } else {
                state.openPrs.forEach { pr ->
                    Row(
                        modifier = Modifier
                            .clickable { onNavigateToPRs() }
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null, tint = SalvioTheme.colors.success, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = pr.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "#${pr.number} • ${pr.user.login}", fontSize = 11.sp, color = SalvioTheme.colors.textSecondary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    if (pr != state.openPrs.last()) HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun ContributorsSection(state: DashboardUiState) {
    if (state.contributors.isNotEmpty()) {
        SalvioCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.card)) {
                SectionHeader(title = "기여자", icon = Icons.Default.People)
                Spacer(modifier = Modifier.height(Spacing.md))
                val top = state.contributors.take(5)
                top.forEach { contributor ->
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = contributor.avatarUrl,
                            contentDescription = "기여자 ${contributor.login} 아바타",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(SalvioTheme.colors.border)
                        )
                        Text(text = contributor.login, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(text = "${contributor.contributions} commits", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                    }
                    if (contributor != top.last()) HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                }
            }
        }
    }
}

private fun formatDate(dateStr: String): String = relativeDays(dateStr)

package com.ydh.salvio.ui.screen.branch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.data.model.GitHubBranch
import com.ydh.salvio.data.model.GitHubCommit
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.component.StatusBadge
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.util.isOlderThanDays
import com.ydh.salvio.util.relativeDays
import com.ydh.salvio.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchScreen(
    owner: String,
    repoName: String,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.branchState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadBranches(owner, repoName)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("브랜치", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { dashboardViewModel.loadBranches(owner, repoName, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading && state.branches.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.error != null && state.branches.isEmpty() ->
                ErrorState(state.error ?: "브랜치 목록을 불러오지 못했습니다.", { dashboardViewModel.loadBranches(owner, repoName, forceRefresh = true) }, Modifier.padding(paddingValues))
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { dashboardViewModel.loadBranches(owner, repoName, forceRefresh = true) },
                    modifier = Modifier.padding(paddingValues)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        item {
                            Text(
                                text = "${state.branches.size}개 브랜치",
                                style = MaterialTheme.typography.labelMedium,
                                color = SalvioTheme.colors.textSecondary,
                                modifier = Modifier.padding(bottom = Spacing.xs)
                            )
                        }
                        items(state.branches, key = { it.name }) { branch ->
                            val latestCommit = state.branchCommits[branch.name]
                            LaunchedEffect(branch.name, latestCommit == null) {
                                if (latestCommit == null) {
                                    dashboardViewModel.loadBranchCommit(owner, repoName, branch.name)
                                }
                            }
                            BranchCard(
                                branch = branch,
                                latestCommit = latestCommit,
                                onClick = {
                                    val url = "https://github.com/$owner/$repoName/tree/${branch.name}"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchCard(branch: GitHubBranch, latestCommit: GitHubCommit?, onClick: () -> Unit) {
    val commitDate = latestCommit?.commit?.author?.date
    val isStale = commitDate?.let { isOlderThanDays(it, 30) } ?: false

    SalvioCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(Spacing.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                tint = if (branch.protected) SalvioTheme.colors.attention else SalvioTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = branch.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (branch.protected) StatusBadge(label = "protected", color = SalvioTheme.colors.attention)
                    if (isStale) StatusBadge(label = "오래됨", color = SalvioTheme.colors.danger)
                }
                latestCommit?.let { commit ->
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = commit.commit.message.lines().first(),
                        fontSize = 12.sp,
                        color = SalvioTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${commit.commit.author.name} • ${formatDate(commit.commit.author.date)}",
                        fontSize = 11.sp,
                        color = SalvioTheme.colors.textTertiary
                    )
                }
            }
            Text(
                text = branch.commit.sha.take(7),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = SalvioTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatDate(dateStr: String): String = relativeDays(dateStr)

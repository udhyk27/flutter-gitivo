package com.ydh.salvio.ui.screen.issue

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.GitHubIssue
import com.ydh.salvio.data.model.GitHubLabel
import com.ydh.salvio.ui.component.EmptyState
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.util.formatAbsolute
import com.ydh.salvio.util.openInBrowser
import com.ydh.salvio.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueScreen(owner: String, repoName: String, dashboardViewModel: DashboardViewModel, onBack: () -> Unit) {
    val state by dashboardViewModel.issueState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadIssues(owner, repoName)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("Issues", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { dashboardViewModel.loadIssues(owner, repoName, forceRefresh = true) }) {
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
                listOf("Open" to state.openIssues.size, "Closed" to state.closedIssues.size)
                    .forEachIndexed { index, (label, count) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    if (state.isLoading) label else "$label ($count)",
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
            }

            when {
                state.isLoading && state.openIssues.isEmpty() && state.closedIssues.isEmpty() -> LoadingState()
                state.error != null && state.openIssues.isEmpty() && state.closedIssues.isEmpty() ->
                    ErrorState(state.error ?: "이슈 목록을 불러오지 못했습니다.", onRetry = {
                        dashboardViewModel.loadIssues(owner, repoName, forceRefresh = true)
                    })
                else -> {
                    val issues = if (selectedTab == 0) state.openIssues else state.closedIssues

                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = { dashboardViewModel.loadIssues(owner, repoName, forceRefresh = true) }
                    ) {
                        if (issues.isEmpty()) {
                            EmptyState(
                                if (selectedTab == 0) "열린 이슈가 없습니다." else "닫힌 이슈가 없습니다.",
                                Icons.Default.CheckCircle,
                                hint = "아래로 당기면 목록을 새로고침합니다."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(Spacing.screen),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                items(issues, key = { it.number }) { issue ->
                                    IssueCard(
                                        issue = issue,
                                        onClick = {
                                            context.openInBrowser(issue.htmlUrl)
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
private fun IssueCard(issue: GitHubIssue, onClick: () -> Unit) {
    SalvioCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    imageVector = if (issue.state == "open") Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (issue.state == "open") SalvioTheme.colors.success else SalvioTheme.colors.done,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = issue.title,
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
                        Text(text = "#${issue.number}", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                        AsyncImage(
                            placeholder = ColorPainter(SalvioTheme.colors.border),
                            model = issue.user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Text(text = issue.user.login, fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                        Text(text = formatIssueDate(issue), fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                    }
                }
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = SalvioTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (issue.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    issue.labels.take(4).forEach { label ->
                        LabelChip(label)
                    }
                }
            }

            if (issue.milestone != null || issue.comments > 0) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    issue.milestone?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                tint = SalvioTheme.colors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = it.title,
                                fontSize = 11.sp,
                                color = SalvioTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (issue.comments > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                Icons.Default.Comment,
                                contentDescription = null,
                                tint = SalvioTheme.colors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(text = "${issue.comments}", fontSize = 11.sp, color = SalvioTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelChip(label: GitHubLabel) {
    val labelColor = try {
        Color(android.graphics.Color.parseColor("#${label.color}"))
    } catch (e: Exception) {
        SalvioTheme.colors.border
    }
    Surface(
        shape = Radius.chip,
        color = labelColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, labelColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = label.name,
            fontSize = 10.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun formatIssueDate(issue: GitHubIssue): String {
    val dateStr = issue.closedAt ?: issue.createdAt
    val prefix = if (issue.state == "closed") "closed " else "opened "
    return prefix + formatAbsolute(dateStr, "MM월 dd일")
}

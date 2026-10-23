package com.ydh.salvio.ui.screen.release

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.GitHubRelease
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
fun ReleaseScreen(
    owner: String,
    repoName: String,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.releaseState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(owner, repoName) {
        dashboardViewModel.loadReleases(owner, repoName)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("Releases", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { dashboardViewModel.loadReleases(owner, repoName, forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading && state.releases.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.error != null && state.releases.isEmpty() ->
                ErrorState(state.error ?: "릴리즈 목록을 불러오지 못했습니다.", { dashboardViewModel.loadReleases(owner, repoName, forceRefresh = true) }, Modifier.padding(paddingValues))
            else -> PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { dashboardViewModel.loadReleases(owner, repoName, forceRefresh = true) },
                modifier = Modifier.padding(paddingValues)
            ) {
                if (state.releases.isEmpty()) {
                    EmptyState("릴리즈가 없습니다.", Icons.Default.LocalOffer, hint = "GitHub에서 릴리즈를 만들면 여기에 표시됩니다.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(state.releases, key = { it.id }) { release ->
                            ReleaseCard(
                                release = release,
                                onClick = {
                                    context.openInBrowser(release.htmlUrl)
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
private fun ReleaseCard(release: GitHubRelease, onClick: () -> Unit) {
    SalvioCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = SalvioTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = release.tagName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = SalvioTheme.colors.accent,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                if (release.prerelease) StatusBadge(label = "Pre-release", color = SalvioTheme.colors.attention)
                if (release.draft) StatusBadge(label = "Draft", color = SalvioTheme.colors.textSecondary)
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
            }

            release.name?.takeIf { it.isNotBlank() && it != release.tagName }?.let { name ->
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    placeholder = ColorPainter(SalvioTheme.colors.border),
                    model = release.author.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clip(CircleShape)
                )
                Text(text = release.author.login, fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                Text(
                    text = release.publishedAt?.let { formatReleaseDate(it) } ?: "",
                    fontSize = 12.sp,
                    color = SalvioTheme.colors.textSecondary
                )
            }

            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                val cleanBody = body.replace(Regex("#{1,6}\\s"), "").replace("**", "").replace("*", "").trim()
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = cleanBody.lines().take(4).joinToString("\n"),
                    fontSize = 12.sp,
                    color = SalvioTheme.colors.textSecondary,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (release.assets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${release.assets.size}개 에셋",
                        fontSize = 12.sp,
                        color = SalvioTheme.colors.textSecondary
                    )
                    val totalDownloads = release.assets.sumOf { it.downloadCount }
                    if (totalDownloads > 0) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${formatCount(totalDownloads)} 다운로드",
                            fontSize = 12.sp,
                            color = SalvioTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun formatReleaseDate(dateStr: String): String =
    formatAbsolute(dateStr, "yyyy. MM. dd")

private fun formatCount(count: Int): String =
    if (count >= 1000) "${count / 1000}k" else count.toString()

package com.ydh.salvio.ui.screen.commit

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.compose.AsyncImage
import com.ydh.salvio.data.model.CommitFile
import com.ydh.salvio.data.model.GitHubCommitDetail
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.util.formatAbsolute
import com.ydh.salvio.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(
    owner: String,
    repoName: String,
    sha: String,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.commitDetailState.collectAsState()

    LaunchedEffect(sha) {
        dashboardViewModel.loadCommitDetail(owner, repoName, sha)
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = {
                    Text(
                        text = sha.take(7),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    val context = LocalContext.current
                    state.detail?.htmlUrl?.let { url ->
                        IconButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "GitHub에서 열기")
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(paddingValues))
            state.error != null ->
                ErrorState(state.error!!, { dashboardViewModel.loadCommitDetail(owner, repoName, sha) }, Modifier.padding(paddingValues))
            state.detail != null -> CommitDetailContent(
                detail = state.detail!!,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun CommitDetailContent(detail: GitHubCommitDetail, modifier: Modifier = Modifier) {
    var expandedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SalvioCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = detail.commit.message,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            placeholder = ColorPainter(SalvioTheme.colors.border),
                            model = detail.author?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SalvioTheme.colors.border)
                        )
                        Column {
                            Text(
                                text = detail.commit.author.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatDate(detail.commit.author.date),
                                fontSize = 12.sp,
                                color = SalvioTheme.colors.textSecondary
                            )
                        }
                    }
                    detail.stats?.let { stats ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatChip(label = "+${stats.additions}", color = SalvioTheme.colors.success)
                            StatChip(label = "-${stats.deletions}", color = SalvioTheme.colors.danger)
                            StatChip(label = "${detail.files.size} 파일", color = SalvioTheme.colors.textSecondary)
                        }
                    }
                    Text(
                        text = detail.sha,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Text(
                text = "변경 파일 ${detail.files.size}개",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(detail.files, key = { it.filename }) { file ->
            val isExpanded = expandedFiles.contains(file.filename)
            FileCard(
                file = file,
                isExpanded = isExpanded,
                onToggle = {
                    expandedFiles = if (isExpanded) expandedFiles - file.filename
                    else expandedFiles + file.filename
                }
            )
        }
    }
}

@Composable
private fun FileCard(file: CommitFile, isExpanded: Boolean, onToggle: () -> Unit) {
    val statusColor = when (file.status) {
        "added" -> SalvioTheme.colors.success
        "removed" -> SalvioTheme.colors.danger
        "modified" -> SalvioTheme.colors.accent
        "renamed" -> SalvioTheme.colors.attention
        else -> SalvioTheme.colors.textSecondary
    }
    val statusLabel = when (file.status) {
        "added" -> "A"
        "removed" -> "D"
        "modified" -> "M"
        "renamed" -> "R"
        else -> "?"
    }

    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = Radius.chip,
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = file.filename,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
                Text(text = "+${file.additions}", fontSize = 12.sp, color = SalvioTheme.colors.success)
                Text(text = "-${file.deletions}", fontSize = 12.sp, color = SalvioTheme.colors.danger)
                if (file.patch != null) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = SalvioTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isExpanded && file.patch != null) {
                HorizontalDivider(color = SalvioTheme.colors.borderMuted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SalvioTheme.colors.canvasInset)
                        .horizontalScroll(rememberScrollState())
                        .padding(Spacing.md)
                ) {
                    Column {
                        file.patch.lines().forEach { line ->
                            val lineColor = when {
                                line.startsWith("+") -> SalvioTheme.colors.success.copy(alpha = 0.15f)
                                line.startsWith("-") -> SalvioTheme.colors.danger.copy(alpha = 0.15f)
                                line.startsWith("@@") -> SalvioTheme.colors.accent.copy(alpha = 0.1f)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                line.startsWith("+") -> SalvioTheme.colors.success
                                line.startsWith("-") -> SalvioTheme.colors.danger
                                line.startsWith("@@") -> SalvioTheme.colors.accent
                                else -> SalvioTheme.colors.text
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(lineColor)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = line,
                                    fontSize = 11.sp,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, color: Color) {
    Surface(shape = Radius.chip, color = color.copy(alpha = 0.12f)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatDate(dateStr: String): String =
    formatAbsolute(dateStr, "yyyy년 MM월 dd일 HH:mm")

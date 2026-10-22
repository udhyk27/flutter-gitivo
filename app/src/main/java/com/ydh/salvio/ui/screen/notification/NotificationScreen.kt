package com.ydh.salvio.ui.screen.notification

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.data.model.GitHubNotification
import com.ydh.salvio.ui.component.EmptyState
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.util.relativeTime
import com.ydh.salvio.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    notificationViewModel: NotificationViewModel,
    onBack: () -> Unit
) {
    val state by notificationViewModel.state.collectAsState()
    val unreadCount = state.notifications.count { it.unread }
    var showMarkAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { notificationViewModel.load() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbarHostState.showSnackbar(it)
            notificationViewModel.consumeActionError()
        }
    }

    if (showMarkAllDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllDialog = false },
            title = { Text("모두 읽음 처리") },
            text = { Text("${unreadCount}개의 알림을 모두 읽음으로 처리하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showMarkAllDialog = false
                    notificationViewModel.markAllRead()
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllDialog = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("알림", style = MaterialTheme.typography.titleLarge)
                        if (unreadCount > 0) {
                            Badge(containerColor = SalvioTheme.colors.accent) { Text("$unreadCount") }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    if (unreadCount > 0) {
                        IconButton(onClick = { showMarkAllDialog = true }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "모두 읽음", tint = SalvioTheme.colors.accent)
                        }
                    }
                    IconButton(onClick = { notificationViewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading && state.notifications.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.error != null && state.notifications.isEmpty() ->
                ErrorState(state.error!!, { notificationViewModel.load() }, Modifier.padding(paddingValues))
            state.notifications.isEmpty() -> EmptyState("읽지 않은 알림이 없습니다.", Icons.Default.NotificationsNone, Modifier.padding(paddingValues))
            else -> PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { notificationViewModel.load() },
                modifier = Modifier.padding(paddingValues)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            onMarkRead = { notificationViewModel.markRead(notification.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: GitHubNotification, onMarkRead: () -> Unit) {
    val (typeIcon, typeColor) = when (notification.subject.type) {
        "PullRequest" -> Icons.Default.MergeType to SalvioTheme.colors.accent
        "Issue" -> Icons.Default.BugReport to SalvioTheme.colors.success
        "Release" -> Icons.Default.LocalOffer to SalvioTheme.colors.done
        "Commit" -> Icons.Default.Commit to SalvioTheme.colors.textSecondary
        else -> Icons.Default.Notifications to SalvioTheme.colors.textSecondary
    }

    val reasonLabel = when (notification.reason) {
        "review_requested" -> "리뷰 요청"
        "assign" -> "담당자 지정"
        "mention" -> "멘션"
        "author" -> "작성자"
        "comment" -> "댓글"
        "ci_activity" -> "CI"
        "subscribed" -> "구독"
        else -> notification.reason
    }

    SalvioCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (notification.unread) SalvioTheme.colors.accent.copy(alpha = 0.4f) else SalvioTheme.colors.borderMuted
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top
        ) {
            if (notification.unread) {
                Box(
                    modifier = Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(SalvioTheme.colors.accent)
                )
            } else {
                Spacer(modifier = Modifier.size(8.dp))
            }

            Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp).padding(top = 2.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.repository.fullName,
                    fontSize = 11.sp,
                    color = SalvioTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.subject.title,
                    fontSize = 13.sp,
                    fontWeight = if (notification.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ReasonBadge(label = reasonLabel)
                    Text(
                        text = relativeTime(notification.updatedAt),
                        fontSize = 11.sp,
                        color = SalvioTheme.colors.textSecondary
                    )
                }
            }

            if (notification.unread) {
                IconButton(onClick = onMarkRead, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "읽음 처리", tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReasonBadge(label: String) {
    Surface(
        shape = Radius.chip,
        color = MaterialTheme.colorScheme.outlineVariant
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = SalvioTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}


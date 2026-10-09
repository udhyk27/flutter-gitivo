package com.ydh.salvio.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.ui.theme.*

/**
 * 미니멀 공용 컴포넌트 모음.
 * 모든 화면이 여기서 정의한 카드/앱바/상태 뷰를 재사용해 일관성을 유지한다.
 */

/** 배경색과 동일한 플랫 앱바 + 하단 헤어라인. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalvioTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = SalvioTheme.colors.textSecondary
            )
        )
        HorizontalDivider(color = SalvioTheme.colors.borderMuted, thickness = 0.5.dp)
    }
}

/** 플랫한 서피스 카드: 그림자 없음, 은은한 테두리, 12dp 라운드. */
@Composable
fun SalvioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color = SalvioTheme.colors.borderMuted,
    content: @Composable () -> Unit
) {
    val base = if (onClick != null) modifier.clickable { onClick() } else modifier
    OutlinedCard(
        modifier = base,
        shape = Radius.card,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, border)
    ) { content() }
}

/** 카드 내부 섹션 헤더: 작은 아이콘 + 제목 + 선택적 우측 액션. */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
    action: Pair<String, () -> Unit>? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 상태 배지(pill). 상태색을 옅은 배경 + 텍스트로 표현. */
@Composable
fun StatusBadge(
    label: String,
    color: Color,
    icon: ImageVector? = null
) {
    Surface(
        shape = Radius.chip,
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            }
            Text(text = label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

/** 중앙 로딩 스피너. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.5.dp)
    }
}

/** 에러 상태: 아이콘 + 메시지 + 재시도. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = SalvioTheme.colors.danger,
                modifier = Modifier.size(44.dp)
            )
            Text(message, color = SalvioTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, shape = Radius.button) { Text("다시 시도") }
        }
    }
}

/** 빈 상태: 아이콘 + 안내 문구. */
@Composable
fun EmptyState(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(icon, contentDescription = null, tint = SalvioTheme.colors.textSecondary, modifier = Modifier.size(44.dp))
            Text(message, color = SalvioTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

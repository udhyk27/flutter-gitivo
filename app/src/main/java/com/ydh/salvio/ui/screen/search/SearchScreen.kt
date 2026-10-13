package com.ydh.salvio.ui.screen.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.data.model.CodeSearchItem
import com.ydh.salvio.ui.component.EmptyState
import com.ydh.salvio.ui.component.ErrorState
import com.ydh.salvio.ui.component.LoadingState
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.component.SalvioTopBar
import com.ydh.salvio.ui.component.StatusBadge
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    owner: String,
    repoName: String,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashboardViewModel.searchState.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose { dashboardViewModel.clearSearch() }
    }

    Scaffold(
        topBar = {
            SalvioTopBar(
                title = { Text("코드 검색", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("함수명, 변수명, 패턴 검색", color = SalvioTheme.colors.textSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SalvioTheme.colors.textSecondary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기", tint = SalvioTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        dashboardViewModel.searchCode(owner, repoName, query)
                    }),
                    shape = Radius.field,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        dashboardViewModel.searchCode(owner, repoName, query)
                    },
                    enabled = query.isNotBlank() && !state.isLoading,
                    shape = Radius.button,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp)
                ) {
                    Text("검색")
                }
            }

            if (state.results == null && !state.isLoading && state.error == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text("검색 팁", style = MaterialTheme.typography.labelMedium, color = SalvioTheme.colors.textSecondary)
                    listOf(
                        "함수명 또는 클래스명으로 검색",
                        "파일 확장자 지정: extension:kt",
                        "특정 경로: path:src/main",
                        "언어 지정: language:kotlin"
                    ).forEach { tip ->
                        Text("• $tip", fontSize = 12.sp, color = SalvioTheme.colors.textSecondary)
                    }
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = SalvioTheme.colors.danger, modifier = Modifier.size(40.dp))
                        Text(state.error!!, color = SalvioTheme.colors.textSecondary)
                    }
                }

                state.results != null -> {
                    val results = state.results!!
                    if (results.items.isEmpty()) {
                        EmptyState("\"${state.query}\"에 대한 결과가 없습니다.", Icons.Default.SearchOff, hint = "다른 키워드로 다시 검색해 보세요.")
                    } else {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Text(
                                    text = if (results.totalCount > results.items.size)
                                        "${results.totalCount}개 중 ${results.items.size}개 표시"
                                    else
                                        "${results.totalCount}개 결과",
                                    fontSize = 12.sp,
                                    color = SalvioTheme.colors.textSecondary
                                )
                                if (results.incompleteResults || results.totalCount > results.items.size) {
                                    StatusBadge(label = "일부 결과만 표시", color = SalvioTheme.colors.attention)
                                }
                            }
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                items(results.items, key = { it.htmlUrl }) { item ->
                                    SearchResultCard(item = item)
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
private fun SearchResultCard(item: CodeSearchItem) {
    val context = LocalContext.current
    val ext = item.name.substringAfterLast('.', "")
    val extColor = when (ext.lowercase()) {
        "kt", "kts" -> SalvioTheme.colors.accent
        "java" -> SalvioTheme.colors.attention
        "py" -> SalvioTheme.colors.success
        "js", "ts", "tsx" -> SalvioTheme.colors.attention
        "xml" -> SalvioTheme.colors.danger
        "json" -> SalvioTheme.colors.done
        else -> SalvioTheme.colors.textSecondary
    }

    SalvioCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.htmlUrl)))
            } catch (_: Exception) {}
        }
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = Radius.chip,
                    color = extColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, extColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (ext.isNotEmpty()) ext.uppercase() else "FILE",
                        fontSize = 9.sp,
                        color = extColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SalvioTheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = SalvioTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = item.path,
                fontSize = 12.sp,
                color = SalvioTheme.colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

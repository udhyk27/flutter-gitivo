package com.ydh.salvio.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.ydh.salvio.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ydh.salvio.ui.component.SalvioCard
import com.ydh.salvio.ui.theme.*
import com.ydh.salvio.viewmodel.AuthState
import com.ydh.salvio.viewmodel.AuthViewModel
import com.ydh.salvio.viewmodel.DeviceFlowState

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val deviceFlow by authViewModel.deviceFlow.collectAsState()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "GitHub 모니터링",
                style = MaterialTheme.typography.bodyMedium,
                color = SalvioTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.sm))

            SalvioCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Personal Access Token",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx", color = SalvioTheme.colors.textSecondary) },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = SalvioTheme.colors.textSecondary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SalvioTheme.colors.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true,
                        shape = Radius.field
                    )

                    if (authState is AuthState.Error) {
                        Text(
                            text = (authState as AuthState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = { authViewModel.loginWithToken(token.trim()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = token.isNotBlank() && authState !is AuthState.Loading,
                        shape = Radius.button
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("로그인", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "  또는  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = SalvioTheme.colors.textSecondary
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            when (val df = deviceFlow) {
                is DeviceFlowState.AwaitingAuthorization -> DeviceAuthCard(
                    userCode = df.userCode,
                    verificationUri = df.verificationUri,
                    onOpen = { uriHandler.openUri(df.verificationUri) },
                    onCopy = { clipboard.setText(AnnotatedString(df.userCode)) },
                    onCancel = { authViewModel.cancelDeviceLogin() }
                )
                else -> {
                    OutlinedButton(
                        onClick = { authViewModel.startDeviceLogin() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = df !is DeviceFlowState.Starting,
                        shape = Radius.button
                    ) {
                        if (df is DeviceFlowState.Starting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("GitHub 계정으로 로그인", fontWeight = FontWeight.Medium)
                        }
                    }
                    if (df is DeviceFlowState.Error) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = df.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Settings → Developer settings → Personal access tokens에서 발급\n필요 권한: repo, read:user",
                style = MaterialTheme.typography.labelMedium,
                color = SalvioTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun DeviceAuthCard(
    userCode: String,
    verificationUri: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit
) {
    SalvioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "아래 코드를 GitHub 인증 페이지에 입력하세요",
                style = MaterialTheme.typography.bodyMedium,
                color = SalvioTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = userCode,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "코드 복사", tint = SalvioTheme.colors.textSecondary)
                }
            }
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = Radius.button
            ) {
                Text("인증 페이지 열기", fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = verificationUri,
                style = MaterialTheme.typography.labelMedium,
                color = SalvioTheme.colors.textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("인증 대기 중…", style = MaterialTheme.typography.labelMedium, color = SalvioTheme.colors.textSecondary)
            }
            TextButton(onClick = onCancel) { Text("취소") }
        }
    }
}

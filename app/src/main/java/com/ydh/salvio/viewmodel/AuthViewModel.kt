package com.ydh.salvio.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ydh.salvio.BuildConfig
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.data.api.RetrofitClient
import com.ydh.salvio.data.model.GitHubUser
import com.ydh.salvio.data.worker.PrCheckWorker
import com.ydh.salvio.util.httpCode
import com.ydh.salvio.util.toUserMessage
import com.ydh.salvio.util.isAuthExpired
import com.ydh.salvio.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: GitHubUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

/** GitHub OAuth Device Flow 진행 상태 */
sealed class DeviceFlowState {
    object Idle : DeviceFlowState()
    object Starting : DeviceFlowState()
    data class AwaitingAuthorization(
        val userCode: String,
        val verificationUri: String
    ) : DeviceFlowState()
    data class Error(val message: String) : DeviceFlowState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalvioApplication
    private val dataStore = app.tokenDataStore

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<GitHubUser?>(null)
    val currentUser: StateFlow<GitHubUser?> = _currentUser.asStateFlow()

    private val _deviceFlow = MutableStateFlow<DeviceFlowState>(DeviceFlowState.Idle)
    val deviceFlow: StateFlow<DeviceFlowState> = _deviceFlow.asStateFlow()

    private var deviceFlowJob: Job? = null

    init {
        checkExistingToken()
    }

    private fun checkExistingToken() {
        viewModelScope.launch {
            val token = dataStore.token.first()
            if (!token.isNullOrBlank()) {
                loginWithToken(token)
            }
        }
    }

    fun loginWithToken(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val repo = app.githubRepository(token)
            repo.getUser().fold(
                onSuccess = { user ->
                    dataStore.saveToken(token)
                    _currentUser.value = user
                    _authState.value = AuthState.Success(user)
                },
                onFailure = { e ->
                    // 401 응답이면 자동 로그아웃
                    if (e.isAuthExpired()) {
                        Logger.authFailed("Token expired (401)")
                        logout()
                        _authState.value = AuthState.Error("인증이 만료되었습니다. 다시 로그인하세요.")
                    } else {
                        val msg = e.toUserMessage("인증에 실패했습니다.")
                        Logger.authFailed(msg)
                        _currentUser.value = null
                        _authState.value = AuthState.Error(msg)
                    }
                }
            )
        }
    }

    /** GitHub 계정 로그인(OAuth Device Flow) 시작 */
    fun startDeviceLogin() {
        val clientId = BuildConfig.GITHUB_CLIENT_ID
        if (clientId.isBlank()) {
            _deviceFlow.value = DeviceFlowState.Error(
                "OAuth Client ID가 설정되지 않았습니다.\ngradle.properties에 GITHUB_CLIENT_ID를 추가하세요."
            )
            return
        }

        deviceFlowJob?.cancel()
        deviceFlowJob = viewModelScope.launch {
            _deviceFlow.value = DeviceFlowState.Starting
            val api = RetrofitClient.createOAuth()
            val code = try {
                api.requestDeviceCode(clientId, scope = "repo read:user notifications")
            } catch (e: Exception) {
                _deviceFlow.value = DeviceFlowState.Error("로그인 시작에 실패했습니다. (${e.message})")
                return@launch
            }

            _deviceFlow.value = DeviceFlowState.AwaitingAuthorization(code.userCode, code.verificationUri)

            // 만료 시각까지 interval 초마다 토큰 발급 여부 폴링
            var intervalSec = code.interval.coerceAtLeast(5)
            val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(intervalSec * 1000L)
                val resp = try {
                    api.requestAccessToken(clientId, code.deviceCode)
                } catch (e: Exception) {
                    continue // 일시적 네트워크 오류는 다음 폴링에서 재시도
                }

                val token = resp.accessToken
                when {
                    token != null -> {
                        _deviceFlow.value = DeviceFlowState.Idle
                        loginWithToken(token) // 사용자 검증 + 토큰 저장 + AuthState.Success
                        return@launch
                    }
                    resp.error == "authorization_pending" -> Unit // 계속 대기
                    resp.error == "slow_down" -> intervalSec += 5
                    resp.error == "expired_token" -> {
                        _deviceFlow.value = DeviceFlowState.Error("인증 코드가 만료되었습니다. 다시 시도하세요.")
                        return@launch
                    }
                    resp.error == "access_denied" -> {
                        _deviceFlow.value = DeviceFlowState.Error("인증이 거부되었습니다.")
                        return@launch
                    }
                    else -> {
                        _deviceFlow.value = DeviceFlowState.Error(
                            resp.errorDescription ?: "인증에 실패했습니다."
                        )
                        return@launch
                    }
                }
            }
            _deviceFlow.value = DeviceFlowState.Error("인증 시간이 만료되었습니다. 다시 시도하세요.")
        }
    }

    /** Device Flow 취소/초기화 */
    fun cancelDeviceLogin() {
        deviceFlowJob?.cancel()
        deviceFlowJob = null
        _deviceFlow.value = DeviceFlowState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            deviceFlowJob?.cancel()
            _deviceFlow.value = DeviceFlowState.Idle
            dataStore.clearToken()
            // 백그라운드 PR 감시 중단 + 이전 사용자 데이터(메모리/Room 캐시) 제거
            PrCheckWorker.cancel(app)
            app.clearSession()
            withContext(Dispatchers.IO) { app.database.clearAllTables() }
            _currentUser.value = null
            _authState.value = AuthState.Idle
        }
    }
}
